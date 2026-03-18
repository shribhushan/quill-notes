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

    fun searchNotes(query: String): Flow<List<NoteEntity>> =
        noteDao.searchNotes(encryption.encrypt(query)).map { notes ->
            notes.map { decryptNote(it) }
        }

    fun getCountByType(type: NoteType): Flow<Int> = noteDao.getCountByType(type)

    // ── Write Operations ───────────────────────────────────────

    suspend fun createNote(note: NoteEntity): Long {
        val encrypted = encryptNote(note)
        return noteDao.insertNote(encrypted)
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
            title = encryption.decrypt(note.title, context),
            content = encryption.decrypt(note.content, context),
            tags = encryption.decrypt(note.tags, context),
            mood = note.mood?.let { encryption.decrypt(it, context) }
        )
    }
}
