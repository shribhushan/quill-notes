package com.quillnotes.data.sync

import android.content.Intent
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.notifications.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-way sync across providers.
 *
 * Each note is one encrypted file, `note_<uuid>.qnote`, holding a [NotePayload]. A sync
 * downloads every cloud file and merges it with the local copy (see [SyncMerge]),
 * then uploads whatever is still newer locally, including delete tombstones. Content is
 * encrypted with the backup data key ([BackupKeyCrypto]) before it leaves the phone.
 */
@Singleton
class CloudSyncManager @Inject constructor(
    private val googleDriveService: GoogleDriveSyncService,
    private val oneDriveService: OneDriveSyncService,
    private val keyStore: BackupKeyStore,
    private val alarmScheduler: AlarmScheduler
) {
    /** Serialises manual and background syncs so two never interleave. */
    private val syncMutex = Mutex()

    private fun serviceFor(provider: SyncProvider): CloudSyncService? = when (provider) {
        SyncProvider.GOOGLE_DRIVE -> googleDriveService
        SyncProvider.ONEDRIVE    -> oneDriveService
        SyncProvider.NONE        -> null
    }

    private suspend fun activeService(): CloudSyncService? =
        listOf(googleDriveService, oneDriveService).firstOrNull { it.isAuthenticated() }

    suspend fun isConnected(provider: SyncProvider): Boolean =
        serviceFor(provider)?.isAuthenticated() ?: false

    suspend fun connect(provider: SyncProvider): Boolean =
        serviceFor(provider)?.signIn() ?: false

    suspend fun disconnect(provider: SyncProvider) {
        serviceFor(provider)?.signOut()
    }

    /** Intent to launch for the interactive Google account picker / consent screen. */
    fun getGoogleSignInIntent(): Intent = googleDriveService.getSignInIntent()

    /** Completes Google sign-in using the Intent data from [getGoogleSignInIntent]'s activity result. */
    suspend fun completeGoogleSignIn(data: Intent?): Boolean = googleDriveService.completeSignIn(data)

    // ── Backup key ─────────────────────────────────────────────

    suspend fun backupKeyState(): BackupKeyState {
        if (keyStore.has()) return BackupKeyState.READY
        val files = activeService()?.listFiles() ?: return BackupKeyState.UNKNOWN
        return if (files.containsValue(BACKUP_KEY_FILE_NAME)) BackupKeyState.NEEDS_PASSPHRASE
        else BackupKeyState.NEEDS_SETUP
    }

    /** First-time setup: creates the data key, stores it wrapped by [passphrase] in the cloud. Returns an error message or null. */
    suspend fun setUpBackup(passphrase: CharArray): String? = syncMutex.withLock {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) return "Use at least $MIN_PASSPHRASE_LENGTH characters."
        val service = activeService() ?: return "Connect a cloud account first."
        val files = service.listFiles() ?: return "Couldn't reach the cloud. Check your connection."
        if (files.containsValue(BACKUP_KEY_FILE_NAME)) {
            return "A backup already exists in this account. Enter its passphrase instead."
        }
        val dataKey = BackupKeyCrypto.newDataKey()
        val info = withContext(Dispatchers.Default) { BackupKeyCrypto.wrap(dataKey, passphrase) }
        service.uploadFile(null, BACKUP_KEY_FILE_NAME, BackupKeyCrypto.encodeKeyInfo(info))
            ?: return "Couldn't save the backup key to the cloud. Try again."
        keyStore.put(dataKey)
        null
    }

    /** Restores access on this phone using the passphrase chosen at setup. Returns an error message or null. */
    suspend fun unlockBackup(passphrase: CharArray): String? = syncMutex.withLock {
        val service = activeService() ?: return "Connect a cloud account first."
        val files = service.listFiles() ?: return "Couldn't reach the cloud. Check your connection."
        val keyFileId = files.entries.firstOrNull { it.value == BACKUP_KEY_FILE_NAME }?.key
            ?: return "No backup was found in this account."
        val bytes = service.downloadFile(keyFileId) ?: return "Couldn't download the backup key. Try again."
        val info = BackupKeyCrypto.decodeKeyInfo(bytes)
            ?: return "The backup key file is unreadable. It may have been created by a newer version of Quill."
        val dataKey = withContext(Dispatchers.Default) { BackupKeyCrypto.unwrap(info, passphrase) }
            ?: return "That passphrase doesn't match this backup."
        keyStore.put(dataKey)
        null
    }

    // ── Sync ───────────────────────────────────────────────────

    suspend fun sync(repo: NoteRepository): SyncResult = syncMutex.withLock {
        val service = activeService() ?: return SyncResult.Failure("No cloud provider connected")
        val remoteFiles = service.listFiles()
            ?: return SyncResult.Failure("Couldn't reach the cloud. Check your connection and try again.")

        val dataKey = keyStore.get()
        if (dataKey == null) {
            return if (remoteFiles.containsValue(BACKUP_KEY_FILE_NAME)) SyncResult.NeedsPassphrase
            else SyncResult.NeedsBackupSetup
        }
        SyncEngine(repo, alarmScheduler).run(service, dataKey, remoteFiles)
    }

    private companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
