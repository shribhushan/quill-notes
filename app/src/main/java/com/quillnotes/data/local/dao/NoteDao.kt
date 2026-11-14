package com.quillnotes.data.local.dao

import androidx.room.*
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.local.entity.TombstoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ── All Notes ──────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE isDeleted = 0 AND isTemplate = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getAllNotes(): Flow<List<NoteEntity>>

    // ── By Type ────────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = :type AND isDeleted = 0 AND isTemplate = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getNotesByType(type: NoteType): Flow<List<NoteEntity>>

    // ── Quick Notes ────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = 'NOTE' AND isDeleted = 0 AND isTemplate = 0
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun getQuickNotes(): Flow<List<NoteEntity>>

    // ── Journal Entries ────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = 'JOURNAL' AND isDeleted = 0 AND isTemplate = 0
        ORDER BY createdAt DESC
    """)
    fun getJournalEntries(): Flow<List<NoteEntity>>

    // ── Tasks ──────────────────────────────────────────────────
    @Query("""
        SELECT * FROM notes
        WHERE type = 'TASK' AND isDeleted = 0 AND isTemplate = 0
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

    @Query("UPDATE notes SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentlyDeleteNote(id: Long)

    @Query("UPDATE notes SET isCompleted = :completed, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun toggleTaskComplete(id: Long, completed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isPinned = :pinned, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun togglePin(id: Long, pinned: Boolean, timestamp: Long = System.currentTimeMillis())

    /** A recurring task was completed: start its next round. */
    @Query("UPDATE notes SET dueDate = :next, isCompleted = 0, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun advanceRecurring(id: Long, next: Long, timestamp: Long = System.currentTimeMillis())

    // ── Task Reminders ─────────────────────────────────────────
    @Query("SELECT * FROM notes WHERE type = 'TASK' AND isDeleted = 0 AND isTemplate = 0 AND isCompleted = 0 AND dueDate > :after")
    suspend fun getTasksWithDueDateAfter(after: Long): List<NoteEntity>

    // ── Archive / lock / templates ─────────────────────────────
    @Query("UPDATE notes SET isArchived = :archived, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isLocked = :locked, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isTemplate = :template, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun setTemplate(id: Long, template: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes WHERE isTemplate = 1 AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getTemplates(): Flow<List<NoteEntity>>

    /** Marks a note changed (e.g. after its attachments changed) so the next sync uploads it. */
    @Query("UPDATE notes SET isSynced = 0, updatedAt = :timestamp WHERE uuid = :uuid")
    suspend fun touchByUuid(uuid: String, timestamp: Long = System.currentTimeMillis())

    /** Rows whose encrypted fields must be re-written (notebook rename / removal). */
    @Query("SELECT * FROM notes WHERE notebook IS NOT NULL AND isTemplate = 0")
    suspend fun getNotesWithNotebook(): List<NoteEntity>

    // ── Sync ───────────────────────────────────────────────────
    /** Every row including trashed ones: trash state is part of what syncs. */
    @Query("SELECT * FROM notes")
    suspend fun getAllForSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE uuid = :uuid")
    suspend fun getNoteByUuid(uuid: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    /**
     * Marks a note as synced only if it has not been edited since [updatedAt] was
     * read, so an edit made while the upload was in flight stays flagged as pending.
     */
    @Query("""
        UPDATE notes SET isSynced = 1, cloudFileId = :cloudFileId, syncedUpdatedAt = :syncedUpdatedAt
        WHERE id = :id AND updatedAt = :updatedAt
    """)
    suspend fun markAsSynced(id: Long, cloudFileId: String, updatedAt: Long, syncedUpdatedAt: Long)

    @Query("DELETE FROM notes WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND updatedAt < :cutoff")
    suspend fun getDeletedBefore(cutoff: Long): List<NoteEntity>

    // ── Tombstones ─────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: TombstoneEntity)

    @Query("SELECT * FROM tombstones")
    suspend fun getAllTombstones(): List<TombstoneEntity>

    @Query("UPDATE tombstones SET isSynced = 1, cloudFileId = :cloudFileId WHERE uuid = :uuid")
    suspend fun markTombstoneSynced(uuid: String, cloudFileId: String)

    @Query("DELETE FROM tombstones WHERE uuid = :uuid")
    suspend fun deleteTombstone(uuid: String)

    // ── Trash ──────────────────────────────────────────────────
    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET isDeleted = 0, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun restoreNote(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND updatedAt < :cutoff")
    suspend fun purgeOldDeletedNotes(cutoff: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun purgeAllDeletedNotes()

    // ── Stats ──────────────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM notes WHERE type = :type AND isDeleted = 0 AND isTemplate = 0")
    fun getCountByType(type: NoteType): Flow<Int>
}
