package com.quillnotes.data.repository

import com.quillnotes.data.encryption.NoteEncryptionManager
import com.quillnotes.data.local.dao.NoteDao
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.local.entity.TombstoneEntity
import com.quillnotes.data.sync.NotePayload
import com.quillnotes.data.sync.SyncMerge
import com.quillnotes.data.sync.SyncStore
import com.quillnotes.data.sync.noteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing encrypted note operations.
 * All data passing through this layer is encrypted/decrypted transparently.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val encryption: NoteEncryptionManager
) : SyncStore {
    // ── Read Operations ────────────────────────────────────────

    fun getAllNotes(): Flow<List<NoteEntity>> =
        noteDao.getAllNotes().map { notes -> notes.map { decryptNote(it) } }

    fun getQuickNotes(): Flow<List<NoteEntity>> =
        noteDao.getQuickNotes().map { notes -> notes.map { decryptNote(it) } }

    fun getJournalEntries(): Flow<List<NoteEntity>> =
        noteDao.getJournalEntries().map { notes -> notes.map { decryptNote(it) } }

    fun getTasks(): Flow<List<NoteEntity>> =
        noteDao.getTasks().map { notes -> notes.map { decryptNote(it) } }

    override suspend fun getNoteById(id: Long): NoteEntity? =
        noteDao.getNoteById(id)?.let { decryptNote(it) }

    /**
     * Note text is encrypted with a randomised AEAD, so it can't be matched in SQL —
     * search the decrypted notes in memory instead.
     */
    fun searchNotes(query: String): Flow<List<NoteEntity>> =
        getAllNotes().map { notes ->
            val q = query.trim()
            if (q.isEmpty()) notes
            else notes.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.content.contains(q, ignoreCase = true) ||
                    it.tags.contains(q, ignoreCase = true)
            }
        }

    fun getCountByType(type: NoteType): Flow<Int> = noteDao.getCountByType(type)

    // ── Write Operations ───────────────────────────────────────

    suspend fun createNote(note: NoteEntity): Long {
        // Ciphertext is bound to the note's id, which only exists once the row is inserted:
        // reserve the row first, then write the content encrypted under its real id.
        val id = noteDao.insertNote(note.copy(id = 0, title = "", content = "", tags = "", mood = null))
        noteDao.updateNote(encryptNote(note.copy(id = id)))
        return id
    }

    suspend fun updateNote(note: NoteEntity) {
        // Callers hold a cached copy of the row; sync may have moved its bookkeeping on
        // since, so keep the stored sync state rather than writing the stale one back.
        val stored = noteDao.getNoteById(note.id)
        val encrypted = encryptNote(
            note.copy(
                uuid = stored?.uuid ?: note.uuid,
                cloudFileId = stored?.cloudFileId ?: note.cloudFileId,
                syncedUpdatedAt = stored?.syncedUpdatedAt ?: note.syncedUpdatedAt,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        noteDao.updateNote(encrypted)
    }

    suspend fun deleteNote(id: Long) {
        noteDao.softDeleteNote(id)
    }

    suspend fun toggleTaskComplete(id: Long, completed: Boolean) {
        noteDao.toggleTaskComplete(id, completed)
    }

    suspend fun togglePin(id: Long, pinned: Boolean) {
        noteDao.togglePin(id, pinned)
    }

    // ── Trash Operations ───────────────────────────────────────

    fun getDeletedNotes(): Flow<List<NoteEntity>> =
        noteDao.getDeletedNotes().map { notes -> notes.map { decryptNote(it) } }

    suspend fun restoreNote(id: Long) {
        noteDao.restoreNote(id)
    }

    suspend fun permanentlyDeleteNote(id: Long) {
        noteDao.getNoteById(id)?.let { leaveTombstone(it) }
        noteDao.permanentlyDeleteNote(id)
    }

    suspend fun purgeOldDeletedNotes() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        noteDao.getDeletedBefore(cutoff).forEach { leaveTombstone(it); noteDao.permanentlyDeleteNote(it.id) }
    }

    suspend fun purgeAllDeletedNotes() {
        noteDao.getDeletedBefore(Long.MAX_VALUE).forEach { leaveTombstone(it); noteDao.permanentlyDeleteNote(it.id) }
    }

    /** Recorded before the row is removed so the deletion reaches the cloud and other phones. */
    private suspend fun leaveTombstone(note: NoteEntity) {
        noteDao.insertTombstone(
            TombstoneEntity(uuid = note.uuid, cloudFileId = note.cloudFileId, deletedAt = System.currentTimeMillis())
        )
    }

    // ── Task Reminder Operations ───────────────────────────────

    suspend fun getTasksWithDueDateAfter(millis: Long): List<NoteEntity> =
        noteDao.getTasksWithDueDateAfter(millis).map { decryptNote(it) }

    // ── Sync Operations ────────────────────────────────────────

    suspend fun countUnsynced(): Int = noteDao.countUnsynced()

    /** Every note including trashed ones, decrypted. */
    override suspend fun getAllForSync(): List<NoteEntity> =
        noteDao.getAllForSync().map { decryptNote(it) }

    override suspend fun getTombstones(): List<TombstoneEntity> = noteDao.getAllTombstones()

    override suspend fun markTombstoneSynced(uuid: String, cloudFileId: String) =
        noteDao.markTombstoneSynced(uuid, cloudFileId)

    override suspend fun dropTombstone(uuid: String) = noteDao.deleteTombstone(uuid)

    /**
     * Records a successful upload. Ignored if the note was edited after [updatedAt] was
     * read, leaving that newer edit flagged for the next sync.
     */
    override suspend fun markSynced(id: Long, cloudFileId: String, updatedAt: Long, syncedUpdatedAt: Long) {
        noteDao.markAsSynced(id, cloudFileId, updatedAt, syncedUpdatedAt)
    }

    /** Creates a local note from a cloud copy. Returns the new row id. */
    override suspend fun insertFromSync(payload: NotePayload, cloudFileId: String): Long {
        noteDao.getNoteByUuid(payload.uuid)?.let { return it.id }
        val entity = payload.toEntity(cloudFileId)
        // Content is bound to the row id, which exists only after insert (see createNote).
        val id = noteDao.insertNote(entity.copy(title = "", content = "", tags = "", mood = null))
        noteDao.updateNote(encryptNote(entity.copy(id = id)))
        return id
    }

    /** Overwrites a local note with a cloud copy, keeping the cloud timestamp. */
    override suspend fun applyFromSync(existing: NoteEntity, payload: NotePayload, cloudFileId: String) {
        noteDao.updateNote(encryptNote(payload.toEntity(cloudFileId).copy(id = existing.id)))
    }

    override suspend fun deleteFromSync(uuid: String) = noteDao.deleteByUuid(uuid)

    /** Keeps the losing side of an edit conflict as its own note, flagged to upload. */
    override suspend fun addConflictCopy(loser: NotePayload) {
        createNote(
            NoteEntity(
                title = SyncMerge.conflictTitle(loser.title),
                content = loser.content,
                type = loser.noteType(),
                createdAt = loser.createdAt,
                isPinned = false,
                isCompleted = loser.isCompleted,
                dueDate = loser.dueDate,
                mood = loser.mood,
                tags = loser.tags,
                color = loser.color
            )
        )
    }

    private fun NotePayload.toEntity(cloudFileId: String) = NoteEntity(
        uuid = uuid, title = title, content = content, type = noteType(),
        createdAt = createdAt, updatedAt = updatedAt, isPinned = isPinned, isCompleted = isCompleted,
        dueDate = dueDate, mood = mood, tags = tags, color = color,
        isSynced = true, syncedUpdatedAt = updatedAt, cloudFileId = cloudFileId, isDeleted = isTrashed
    )

    // ── Encryption Helpers ─────────────────────────────────────

    private fun encryptNote(note: NoteEntity): NoteEntity {
        val context = "note_${note.id}"
        return note.copy(
            title = encryption.encrypt(note.title, context),
            content = encryption.encrypt(note.content, context),
            tags = encryption.encrypt(note.tags, context),
            mood = note.mood?.let { encryption.encrypt(it, context) }
        )
    }

    private fun decryptNote(note: NoteEntity): NoteEntity {
        val context = "note_${note.id}"
        return note.copy(
            title = decryptField(note.title, context),
            content = decryptField(note.content, context),
            tags = decryptField(note.tags, context),
            mood = note.mood?.let { decryptField(it, context) }
        )
    }

    /**
     * Earlier builds encrypted brand-new notes before they had an id (context "note_0"),
     * so fall back to that context when the note's own one doesn't authenticate.
     * [NoteEncryptionManager.decrypt] returns its input unchanged on failure.
     */
    private fun decryptField(value: String, context: String): String {
        val plain = encryption.decrypt(value, context)
        return if (plain == value && value.isNotEmpty()) encryption.decrypt(value, LEGACY_NEW_NOTE_CONTEXT) else plain
    }

    private companion object {
        const val LEGACY_NEW_NOTE_CONTEXT = "note_0"
    }
}
