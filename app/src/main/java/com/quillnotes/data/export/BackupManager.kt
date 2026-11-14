package com.quillnotes.data.export

import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.sync.toPayload
import com.quillnotes.notifications.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(val added: Int, val updated: Int, val skipped: Int, val images: Int) {
    val total: Int get() = added + updated + skipped
}

/**
 * Export and import without any cloud account: Markdown (a zip of readable files) and the
 * passphrase-encrypted `.qbackup`. The format logic lives in [NoteExporter] and [BackupFile].
 */
@Singleton
class BackupManager @Inject constructor(
    private val repository: NoteRepository,
    private val alarmScheduler: AlarmScheduler
) {
    /** Writes every live note as Markdown into [out] (a zip). */
    suspend fun exportMarkdown(out: OutputStream): NoteExporter.ZipSummary = withContext(Dispatchers.IO) {
        val notes = repository.getAllForSync().map { it.toPayload() }
        NoteExporter.writeZip(out, notes) { repository.readAttachmentBlocking(it) }
    }

    /** Writes an encrypted backup of every note, trashed ones included, to [out] (which is closed). */
    suspend fun exportBackup(out: OutputStream, passphrase: CharArray): BackupFile.Manifest = withContext(Dispatchers.IO) {
        val notes = repository.getAllForSync().map { it.toPayload() }
        BackupFile.write(out, passphrase, notes, { repository.readAttachmentBlocking(it) })
    }

    /**
     * Restores a backup. Notes that are not on the phone are added, older local copies are
     * replaced by newer backed-up ones, and anything edited more recently on the phone is kept.
     * @throws BadPassphraseException
     * @throws InvalidBackupException
     */
    suspend fun importBackup(input: InputStream, passphrase: CharArray): ImportSummary = withContext(Dispatchers.IO) {
        var added = 0
        var updated = 0
        var skipped = 0
        var images = 0
        // The reader calls back on this (IO) thread, in file order: notes first, then image files.
        BackupFile.read(
            input, passphrase,
            onNote = { payload ->
                runBlocking {
                    val local = repository.getNoteByUuid(payload.uuid)
                    when (ImportPolicy.decide(local?.updatedAt, payload)) {
                        ImportAction.ADD -> { repository.importNew(payload); added++ }
                        ImportAction.REPLACE -> { repository.importOver(local!!, payload); updated++ }
                        ImportAction.SKIP -> skipped++
                    }
                }
            },
            onAttachment = { uuid, bytes ->
                runBlocking { repository.importAttachmentFile(uuid, bytes) }
                images++
            }
        )
        // Restored tasks need their reminders armed again.
        repository.getTasksWithDueDateAfter(System.currentTimeMillis()).forEach { task ->
            alarmScheduler.scheduleReminder(task.id, task.title, task.dueDate!!)
        }
        ImportSummary(added, updated, skipped, images)
    }
}
