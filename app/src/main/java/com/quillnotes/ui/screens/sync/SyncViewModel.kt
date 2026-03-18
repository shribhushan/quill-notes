package com.quillnotes.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.data.sync.CloudSyncManager
import com.quillnotes.data.sync.SyncProvider
import com.quillnotes.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val provider: SyncProvider = SyncProvider.NONE,
    val isSyncing: Boolean = false,
    val isConnected: Boolean = false,
    val lastSyncTime: Long? = null,
    val message: String? = null,
    val error: String? = null,
    val unsyncedCount: Int = 0
)

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
                val unsynced = noteRepository.getUnsyncedNotes().size
                _uiState.update {
                    it.copy(
                        provider = provider,
                        isConnected = connected,
                        unsyncedCount = unsynced
                    )
                }
            }
        }
    }

    fun connectProvider(provider: SyncProvider) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = "Connecting to ${provider.displayName}…") }
            val success = syncManager.connect(provider)
            prefsRepository.setSyncProvider(if (success) provider.key else SyncProvider.NONE.key)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    isConnected = success,
                    provider = if (success) provider else SyncProvider.NONE,
                    message = if (success) "Connected to ${provider.displayName}" else null,
                    error = if (!success) "Failed to connect. Please try again." else null
                )
            }
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
                    message = "Disconnected",
                    error = null
                )
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = "Syncing…", error = null) }
            val result = syncManager.syncAll(noteRepository)
            when (result) {
                is SyncResult.Success -> _uiState.update {
                    it.copy(
                        isSyncing = false,
                        lastSyncTime = System.currentTimeMillis(),
                        unsyncedCount = 0,
                        message = "Synced ${result.count} note(s) successfully"
                    )
                }
                is SyncResult.Failure -> _uiState.update {
                    it.copy(
                        isSyncing = false,
                        error = result.reason,
                        message = null
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}
