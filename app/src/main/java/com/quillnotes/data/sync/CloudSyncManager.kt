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

    /** First-time setup: creates the data key, stores it wrapped by [passphrase] in the cloud. Returns the reason it failed, or null. */
    suspend fun setUpBackup(passphrase: CharArray): SyncError? = syncMutex.withLock {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) return SyncError.PASSPHRASE_TOO_SHORT
        val service = activeService() ?: return SyncError.NOT_CONNECTED
        val files = service.listFiles() ?: return SyncError.OFFLINE
        if (files.containsValue(BACKUP_KEY_FILE_NAME)) return SyncError.BACKUP_EXISTS
        val dataKey = BackupKeyCrypto.newDataKey()
        val info = withContext(Dispatchers.Default) { BackupKeyCrypto.wrap(dataKey, passphrase) }
        service.uploadFile(null, BACKUP_KEY_FILE_NAME, BackupKeyCrypto.encodeKeyInfo(info))
            ?: return SyncError.KEY_UPLOAD_FAILED
        keyStore.put(dataKey)
        null
    }

    /** Restores access on this phone using the passphrase chosen at setup. Returns the reason it failed, or null. */
    suspend fun unlockBackup(passphrase: CharArray): SyncError? = syncMutex.withLock {
        val service = activeService() ?: return SyncError.NOT_CONNECTED
        val files = service.listFiles() ?: return SyncError.OFFLINE
        val keyFileId = files.entries.firstOrNull { it.value == BACKUP_KEY_FILE_NAME }?.key
            ?: return SyncError.NO_BACKUP_FOUND
        val bytes = service.downloadFile(keyFileId) ?: return SyncError.KEY_DOWNLOAD_FAILED
        val info = BackupKeyCrypto.decodeKeyInfo(bytes) ?: return SyncError.KEY_UNREADABLE
        val dataKey = withContext(Dispatchers.Default) { BackupKeyCrypto.unwrap(info, passphrase) }
            ?: return SyncError.WRONG_PASSPHRASE
        keyStore.put(dataKey)
        null
    }

    // ── Sync ───────────────────────────────────────────────────

    suspend fun sync(repo: NoteRepository): SyncResult = syncMutex.withLock {
        val service = activeService() ?: return SyncResult.Failure(SyncError.NOT_CONNECTED)
        val remoteFiles = service.listFiles() ?: return SyncResult.Failure(SyncError.OFFLINE)

        val dataKey = keyStore.get()
        if (dataKey == null) {
            return if (remoteFiles.containsValue(BACKUP_KEY_FILE_NAME)) SyncResult.NeedsPassphrase
            else SyncResult.NeedsBackupSetup
        }
        SyncEngine(repo, alarmScheduler).run(service, dataKey, remoteFiles)
    }
}
