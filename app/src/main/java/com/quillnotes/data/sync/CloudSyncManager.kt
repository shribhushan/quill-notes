package com.quillnotes.data.sync

import com.quillnotes.data.encryption.NoteEncryptionManager
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.repository.NoteRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates sync across providers.
 * Delegates to the correct CloudSyncService and handles
 * encrypt-before-upload / decrypt-after-download lifecycle.
 */
@Singleton
class CloudSyncManager @Inject constructor(
    private val googleDriveService: GoogleDriveSyncService,
    private val oneDriveService: OneDriveSyncService,
    private val encryption: NoteEncryptionManager
) {
    private fun serviceFor(provider: SyncProvider): CloudSyncService? = when (provider) {
        SyncProvider.GOOGLE_DRIVE -> googleDriveService
        SyncProvider.ONEDRIVE    -> oneDriveService
        SyncProvider.NONE        -> null
    }

    suspend fun isConnected(provider: SyncProvider): Boolean =
        serviceFor(provider)?.isAuthenticated() ?: false

    suspend fun connect(provider: SyncProvider): Boolean =
        serviceFor(provider)?.signIn() ?: false

    suspend fun disconnect(provider: SyncProvider) {
        serviceFor(provider)?.signOut()
    }

    /**
     * Uploads every un-synced note, encrypting content before it leaves the device.
     * Existing cloud files are updated (using the stored cloudFileId); new notes
     * get a new remote file created.
     */
    suspend fun syncAll(noteRepository: NoteRepository): SyncResult {
        // Determine active provider from the first connected service
        val (provider, service) = listOf(
            SyncProvider.GOOGLE_DRIVE to googleDriveService,
            SyncProvider.ONEDRIVE    to oneDriveService
        ).firstOrNull { (_, svc) -> svc.isAuthenticated() }
            ?: return SyncResult.Failure("No cloud provider connected")

        var synced = 0
        val unsynced: List<NoteEntity> = noteRepository.getUnsyncedNotes()

        for (note in unsynced) {
            val payload = buildPayload(note)
            val encryptedBytes = encryption.encryptForExport(payload)
            val fileName = "note_${note.id}.qnote"

            val remoteId = service.uploadFile(
                fileId = note.cloudFileId,
                name   = fileName,
                data   = encryptedBytes
            )
            if (remoteId != null) {
                noteRepository.markAsSynced(note.id, remoteId)
                synced++
            }
        }

        return if (synced == unsynced.size)
            SyncResult.Success(synced)
        else
            SyncResult.Failure("Partial sync: $synced of ${unsynced.size} note(s) uploaded")
    }

    // ── Helpers ────────────────────────────────────────────────

    /**
     * Serialises a note to a simple JSON-like string for cloud storage.
     * In production you could use a Protobuf or proper JSON serialiser.
     */
    private fun buildPayload(note: NoteEntity): String = buildString {
        append("{")
        append("\"id\":${note.id},")
        append("\"title\":\"${note.title.escapeJson()}\",")
        append("\"content\":\"${note.content.escapeJson()}\",")
        append("\"type\":\"${note.type.name}\",")
        append("\"createdAt\":${note.createdAt},")
        append("\"updatedAt\":${note.updatedAt},")
        append("\"mood\":\"${note.mood.orEmpty().escapeJson()}\",")
        append("\"tags\":\"${note.tags.escapeJson()}\",")
        append("\"dueDate\":${note.dueDate ?: "null"},")
        append("\"isCompleted\":${note.isCompleted}")
        append("}")
    }

    private fun String.escapeJson() =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
