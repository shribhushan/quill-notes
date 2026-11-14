package com.quillnotes.data.sync

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.local.entity.TombstoneEntity

/** What the sync engine needs from local storage ([com.quillnotes.data.repository.NoteRepository]). */
interface SyncStore {
    /** Every note including trashed ones, decrypted. */
    suspend fun getAllForSync(): List<NoteEntity>
    suspend fun getNoteById(id: Long): NoteEntity?
    suspend fun getTombstones(): List<TombstoneEntity>
    suspend fun markTombstoneSynced(uuid: String, cloudFileId: String)
    suspend fun dropTombstone(uuid: String)

    /** Records an upload; must be ignored if the note was edited after [updatedAt]. */
    suspend fun markSynced(id: Long, cloudFileId: String, updatedAt: Long, syncedUpdatedAt: Long)

    /** Creates a local note from a cloud copy, already marked synced. Returns the row id. */
    suspend fun insertFromSync(payload: NotePayload, cloudFileId: String): Long

    /** Overwrites a local note with a cloud copy, keeping the cloud timestamp, marked synced. */
    suspend fun applyFromSync(existing: NoteEntity, payload: NotePayload, cloudFileId: String)
    suspend fun deleteFromSync(uuid: String)

    /** Keeps the losing side of an edit conflict as its own un-synced note. */
    suspend fun addConflictCopy(loser: NotePayload)
}

/** Alarm side effects of sync changing a task. */
interface ReminderSink {
    fun schedule(taskId: Long, title: String, dueDateMillis: Long)
    fun cancel(taskId: Long)
}

/**
 * One sync pass: download and merge every cloud note, then upload what is still newer
 * locally. Free of Android types so the whole algorithm is unit-tested on the JVM.
 */
class SyncEngine(
    private val store: SyncStore,
    private val reminders: ReminderSink,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** [remoteFiles] is the cloud listing (file id → name) the caller already fetched. */
    suspend fun run(service: CloudSyncService, dataKey: ByteArray, remoteFiles: Map<String, String>): SyncResult {
        val fileIds = HashMap<String, String>()   // uuid -> remote file id
        val fileNames = HashMap<String, String>() // uuid -> remote file name
        for ((id, name) in remoteFiles) {
            val uuid = uuidFromFileName(name)?.lowercase() ?: continue
            fileIds[uuid] = id
            fileNames[uuid] = name
        }

        var uploaded = 0
        var downloaded = 0
        var conflicts = 0
        var failures = 0
        var undecryptable = 0
        val locals = store.getAllForSync().associateBy { it.uuid }
        val tombstones = store.getTombstones().associateBy { it.uuid }
        val handledTombstones = HashSet<String>()

        suspend fun upload(payload: NotePayload): String? {
            val name = fileNames[payload.uuid] ?: noteFileName(payload.uuid)
            val blob = BackupKeyCrypto.encryptNote(dataKey, name, NotePayload.encode(payload))
            val id = service.uploadFile(fileIds[payload.uuid], name, blob) ?: return null
            fileIds[payload.uuid] = id
            fileNames[payload.uuid] = name
            return id
        }

        // ── 1. Download and merge ──
        for ((uuid, fileId) in fileIds.toList()) {
            val name = fileNames.getValue(uuid)
            val bytes = service.downloadFile(fileId)
            if (bytes == null) { failures++; continue }
            val json = BackupKeyCrypto.decryptNote(dataKey, name, bytes)
            if (json == null) { undecryptable++; continue }
            val remote = NotePayload.decode(json)?.takeIf { it.uuid.lowercase() == uuid }
            if (remote == null) continue // newer format or damaged: leave it untouched

            val local = locals[uuid]
            val tombstone = tombstones[uuid]
            val localNote = local?.let { LocalNote(it.toPayload(), dirty = !it.isSynced, syncedUpdatedAt = it.syncedUpdatedAt) }

            when (val action = SyncMerge.decide(localNote, remote, tombstone?.let { LocalTombstone(it.deletedAt) })) {
                MergeAction.Skip, MergeAction.PushLocal -> Unit // pushes happen in step 3
                MergeAction.CreateLocal -> {
                    val id = store.insertFromSync(remote, fileId)
                    tombstone?.let { store.dropTombstone(uuid) }
                    store.getNoteById(id)?.let { syncReminder(it) }
                    downloaded++
                }
                MergeAction.ApplyRemote -> {
                    store.applyFromSync(local!!, remote, fileId)
                    syncReminder(local.withRemote(remote))
                    downloaded++
                }
                MergeAction.DeleteLocal -> {
                    store.deleteFromSync(uuid)
                    reminders.cancel(local!!.id)
                    downloaded++
                }
                MergeAction.MarkSynced ->
                    store.markSynced(local!!.id, fileId, local.updatedAt, remote.updatedAt)
                MergeAction.PushTombstone -> {
                    if (upload(NotePayload.tombstone(uuid, remote.type, tombstone!!.deletedAt)) != null) {
                        store.markTombstoneSynced(uuid, fileId)
                        handledTombstones += uuid
                    } else failures++
                }
                is MergeAction.KeepBoth -> {
                    conflicts++
                    val loser = if (action.localWins) remote else local!!.toPayload()
                    // The copy is made before the winner is applied: a crash in between
                    // can duplicate a note, but can never lose an edit.
                    if (!loser.isTrashed) store.addConflictCopy(loser)
                    if (!action.localWins) {
                        store.applyFromSync(local!!, remote, fileId)
                        syncReminder(local.withRemote(remote))
                        downloaded++
                    }
                    // localWins: the local note stays dirty and is uploaded in step 3.
                }
            }
        }

        // ── 2. Upload delete tombstones ──
        for (tombstone in store.getTombstones()) {
            if (tombstone.isSynced || tombstone.uuid in handledTombstones) continue
            val id = upload(NotePayload.tombstone(tombstone.uuid, NoteType.NOTE.name, tombstone.deletedAt))
            if (id != null) { store.markTombstoneSynced(tombstone.uuid, id); uploaded++ } else failures++
        }

        // ── 3. Upload local changes (including edits just kept in conflict resolution) ──
        for (note in store.getAllForSync()) {
            if (note.isSynced) continue
            val id = upload(note.toPayload())
            if (id != null) {
                store.markSynced(note.id, id, note.updatedAt, note.updatedAt)
                uploaded++
            } else failures++
        }

        return when {
            failures > 0 -> SyncResult.Failure(
                "Synced with problems: $failures item(s) could not be transferred. They will be retried."
            )
            undecryptable > 0 -> SyncResult.Failure(
                "$undecryptable cloud file(s) could not be decrypted with this phone's backup key."
            )
            else -> SyncResult.Success(uploaded, downloaded, conflicts)
        }
    }

    private fun NoteEntity.withRemote(remote: NotePayload) = copy(
        title = remote.title, type = remote.noteType(), dueDate = remote.dueDate,
        isCompleted = remote.isCompleted, isDeleted = remote.isTrashed
    )

    /** Keeps the alarm for a task in line with a note that sync just created or changed. */
    private fun syncReminder(note: NoteEntity) {
        val due = note.dueDate
        if (note.type == NoteType.TASK && !note.isDeleted && !note.isCompleted && due != null && due > now()) {
            reminders.schedule(note.id, note.title, due)
        } else {
            reminders.cancel(note.id)
        }
    }
}
