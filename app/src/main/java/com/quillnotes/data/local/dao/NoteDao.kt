package com.quillnotes.data.local.dao

import androidx.room.*
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ── All Notes ──────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE isDeleted = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getAllNotes(): Flow<List<NoteEntity>>

    // ── By Type ────────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = :type AND isDeleted = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesByType(type: NoteType): Flow<List<NoteEntity>>

    // ── Quick Notes ────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = 'NOTE' AND isDeleted = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getQuickNotes(): Flow<List<NoteEntity>>

    // ── Journal Entries ────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = 'JOURNAL' AND isDeleted = 0
        ORDER BY createdAt DESC
    """)
    fun getJournalEntries(): Flow<List<NoteEntity>>

    // ── Tasks ──────────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = 'TASK' AND isDeleted = 0
        ORDER BY isCompleted ASC, dueDate ASC, updatedAt DESC
    """)
    fun getTasks(): Flow<List<NoteEntity>>

    // ── Single Note ────────────────────────────────────────────
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?

    // ── Search ─────────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    // ── CRUD ───────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentlyDeleteNote(id: Long)

    @Query("UPDATE notes SET isCompleted = :completed, updatedAt = :timestamp WHERE id = :id")
    suspend fun toggleTaskComplete(id: Long, completed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :timestamp WHERE id = :id")
    suspend fun togglePin(id: Long, pinned: Boolean, timestamp: Long = System.currentTimeMillis())

    // ── Task Reminders ─────────────────────────────────────────
    @Query("SELECT * FROM notes WHERE type = 'TASK' AND isDeleted = 0 AND dueDate > :after")
    suspend fun getTasksWithDueDateAfter(after: Long): List<NoteEntity>

    // ── Sync ───────────────────────────────────────────────────
    @Query("SELECT * FROM notes WHERE isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedNotes(): List<NoteEntity>

    @Query("UPDATE notes SET isSynced = 1, cloudFileId = :cloudFileId WHERE id = :id")
    suspend fun markAsSynced(id: Long, cloudFileId: String)

    // ── Trash ──────────────────────────────────────────────────
    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET isDeleted = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun restoreNote(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND updatedAt < :cutoff")
    suspend fun purgeOldDeletedNotes(cutoff: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun purgeAllDeletedNotes()

    // ── Stats ──────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM notes WHERE type = :type AND isDeleted = 0")
    fun getCountByType(type: NoteType): Flow<Int>
}
