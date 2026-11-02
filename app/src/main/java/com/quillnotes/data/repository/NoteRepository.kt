package com.quillnotes.data.repository

import com.quillnotes.data.encryption.NoteEncryptionManager
import com.quillnotes.data.local.dao.NoteDao
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
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
) {
    // ── Read Operations ────────────────────────────────────────

    fun getAllNotes(): Flow<List<NoteEntity>> =
        noteDao.getAllNotes().map { notes -> notes.map { decryptNote(it) } }

    fun getQuickNotes(): Flow<List<NoteEntity>> =
        noteDao.getQuickNotes().map { notes -> notes.map { decryptNote(it) } }

    fun getJournalEntries(): Flow<List<NoteEntity>> =
        noteDao.getJournalEntries().map { notes -> notes.map { decryptNote(it) } }

    fun getTasks(): Flow<List<NoteEntity>> =
        noteDao.getTasks().map { notes -> notes.map { decryptNote(it) } }

    suspend fun getNoteById(id: Long): NoteEntity? =
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
        val encrypted = encryptNote(note.copy(updatedAt = System.currentTimeMillis()))
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
        noteDao.permanentlyDeleteNote(id)
    }

    suspend fun purgeOldDeletedNotes() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        noteDao.purgeOldDeletedNotes(cutoff)
    }

    suspend fun purgeAllDeletedNotes() {
        noteDao.purgeAllDeletedNotes()
    }

    // ── Task Reminder Operations ───────────────────────────────

    suspend fun getTasksWithDueDateAfter(millis: Long): List<NoteEntity> =
        noteDao.getTasksWithDueDateAfter(millis).map { decryptNote(it) }

    // ── Sync Operations ────────────────────────────────────────

    suspend fun getUnsyncedNotes(): List<NoteEntity> =
        noteDao.getUnsyncedNotes().map { decryptNote(it) }

    suspend fun markAsSynced(id: Long, cloudFileId: String) {
        noteDao.markAsSynced(id, cloudFileId)
    }

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
