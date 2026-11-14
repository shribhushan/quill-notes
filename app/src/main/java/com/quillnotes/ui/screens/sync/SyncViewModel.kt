package com.quillnotes.ui.screens.sync

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.data.sync.BackupKeyState
import com.quillnotes.data.sync.CloudSyncManager
import com.quillnotes.data.sync.SyncProvider
import com.quillnotes.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.quillnotes.R
import com.quillnotes.data.sync.MIN_PASSPHRASE_LENGTH
import com.quillnotes.data.sync.SyncError
import com.quillnotes.ui.util.UiText
import com.quillnotes.ui.util.uiText

data class SyncUiState(
    val provider: SyncProvider = SyncProvider.NONE,
    val isSyncing: Boolean = false,
    val isConnected: Boolean = false,
    val lastSyncTime: Long? = null,
    val message: UiText? = null,
    val error: UiText? = null,
    val unsyncedCount: Int = 0,
    /** Set when the backup needs a recovery passphrase from the user before it can sync. */
    val passphrasePrompt: PassphrasePrompt = PassphrasePrompt.NONE,
    val passphraseError: UiText? = null
)

/** The translated sentence for a failed backup step or sync. [count] fills the plural-ish messages. */
fun SyncError.toUiText(count: Int = 0): UiText = when (this) {
    SyncError.NOT_CONNECTED -> uiText(R.string.sync_err_not_connected)
    SyncError.OFFLINE -> uiText(R.string.sync_err_offline)
    SyncError.PASSPHRASE_TOO_SHORT -> uiText(R.string.sync_err_too_short, MIN_PASSPHRASE_LENGTH)
    SyncError.BACKUP_EXISTS -> uiText(R.string.sync_err_backup_exists)
    SyncError.KEY_UPLOAD_FAILED -> uiText(R.string.sync_err_key_upload)
    SyncError.NO_BACKUP_FOUND -> uiText(R.string.sync_err_no_backup)
    SyncError.KEY_DOWNLOAD_FAILED -> uiText(R.string.sync_err_key_download)
    SyncError.KEY_UNREADABLE -> uiText(R.string.sync_err_key_unreadable)
    SyncError.WRONG_PASSPHRASE -> uiText(R.string.sync_err_wrong_passphrase)
    SyncError.PARTIAL -> uiText(R.string.sync_err_partial, count)
    SyncError.UNDECRYPTABLE -> uiText(R.string.sync_err_undecryptable, count)
}

enum class PassphrasePrompt {
    NONE,
    /** First backup: choose a passphrase. */
    SETUP,
    /** A backup from another phone exists: enter its passphrase. */
    UNLOCK
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val prefsRepository: PreferencesRepository,
    private val noteRepository: NoteRepository,
    private val syncManager: CloudSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepository.syncProvider.collect { providerKey ->
                val provider = SyncProvider.fromKey(providerKey)
                val connected = if (provider != SyncProvider.NONE) syncManager.isConnected(provider) else false
                val unsynced = noteRepository.countUnsynced()
                _uiState.update {
                    it.copy(
                        provider = provider,
                        isConnected = connected,
                        unsyncedCount = unsynced
                    )
                }
                if (connected) checkBackupKey()
            }
        }
    }

    /** Intent to launch (via rememberLauncherForActivityResult) to show Google's account picker. */
    fun getGoogleSignInIntent(): Intent = syncManager.getGoogleSignInIntent()

    /** Call with the Intent data returned from the Google sign-in activity result. */
    fun onGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = uiText(R.string.sync_connecting, SyncProvider.GOOGLE_DRIVE.displayName)) }
            val success = syncManager.completeGoogleSignIn(data)
            prefsRepository.setSyncProvider(if (success) SyncProvider.GOOGLE_DRIVE.key else SyncProvider.NONE.key)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    isConnected = success,
                    provider = if (success) SyncProvider.GOOGLE_DRIVE else SyncProvider.NONE,
                    message = if (success) uiText(R.string.sync_connected_to, SyncProvider.GOOGLE_DRIVE.displayName) else null,
                    error = if (!success) uiText(R.string.sync_google_failed) else null
                )
            }
            if (success) checkBackupKey()
        }
    }

    fun connectProvider(provider: SyncProvider) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = uiText(R.string.sync_connecting, provider.displayName)) }
            val success = syncManager.connect(provider)
            prefsRepository.setSyncProvider(if (success) provider.key else SyncProvider.NONE.key)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    isConnected = success,
                    provider = if (success) provider else SyncProvider.NONE,
                    message = if (success) uiText(R.string.sync_connected_to, provider.displayName) else null,
                    error = if (!success) uiText(R.string.sync_connect_failed) else null
                )
            }
            if (success) checkBackupKey()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            syncManager.disconnect(_uiState.value.provider)
            prefsRepository.setSyncProvider(SyncProvider.NONE.key)
            _uiState.update {
                it.copy(
                    provider = SyncProvider.NONE,
                    isConnected = false,
                    message = uiText(R.string.sync_disconnected),
                    error = null
                )
            }
        }
    }

    /** Asks for a passphrase when this phone has no backup key yet. */
    private suspend fun checkBackupKey() {
        val prompt = when (syncManager.backupKeyState()) {
            BackupKeyState.NEEDS_SETUP -> PassphrasePrompt.SETUP
            BackupKeyState.NEEDS_PASSPHRASE -> PassphrasePrompt.UNLOCK
            BackupKeyState.READY, BackupKeyState.UNKNOWN -> PassphrasePrompt.NONE
        }
        _uiState.update { it.copy(passphrasePrompt = prompt, passphraseError = null) }
    }

    fun submitPassphrase(passphrase: String) {
        val prompt = _uiState.value.passphrasePrompt
        if (prompt == PassphrasePrompt.NONE) return
        val chars = passphrase.toCharArray()
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, passphraseError = null) }
            val error: SyncError? = when (prompt) {
                PassphrasePrompt.SETUP -> syncManager.setUpBackup(chars)
                else -> syncManager.unlockBackup(chars)
            }
            chars.fill('\u0000')
            if (error != null) {
                _uiState.update { it.copy(isSyncing = false, passphraseError = error.toUiText()) }
            } else {
                _uiState.update { it.copy(isSyncing = false, passphrasePrompt = PassphrasePrompt.NONE) }
                syncNow() // restores everything on a new phone / uploads the first backup
            }
        }
    }

    fun dismissPassphrasePrompt() {
        _uiState.update { it.copy(passphrasePrompt = PassphrasePrompt.NONE, passphraseError = null) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = uiText(R.string.sync_syncing), error = null) }
            when (val result = syncManager.sync(noteRepository)) {
                is SyncResult.Success -> _uiState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncTime = System.currentTimeMillis(),
                        unsyncedCount = noteRepository.countUnsynced(),
                        message = successMessage(result)
                    )
                }
                is SyncResult.Failure -> _uiState.update {
                    it.copy(
                        isSyncing = false,
                        unsyncedCount = noteRepository.countUnsynced(),
                        error = result.error.toUiText(result.count),
                        message = null
                    )
                }
                SyncResult.NeedsBackupSetup -> _uiState.update {
                    it.copy(isSyncing = false, message = null, passphrasePrompt = PassphrasePrompt.SETUP)
                }
                SyncResult.NeedsPassphrase -> _uiState.update {
                    it.copy(isSyncing = false, message = null, passphrasePrompt = PassphrasePrompt.UNLOCK)
                }
            }
        }
    }

    private fun successMessage(r: SyncResult.Success): UiText {
        val base = if (r.count == 0) uiText(R.string.sync_up_to_date) else uiText(R.string.sync_transferred, r.uploaded, r.downloaded)
        return if (r.conflicts > 0) uiText(R.string.sync_with_conflicts, base, r.conflicts) else base
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
