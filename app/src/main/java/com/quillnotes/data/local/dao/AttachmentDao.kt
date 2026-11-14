package com.quillnotes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quillnotes.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE noteUuid = :noteUuid ORDER BY position, id")
    fun observeForNote(noteUuid: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteUuid = :noteUuid ORDER BY position, id")
    suspend fun getForNote(noteUuid: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments ORDER BY noteUuid, position, id")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE noteUuid = :noteUuid AND uuid = :uuid")
    suspend fun delete(noteUuid: String, uuid: String)

    @Query("DELETE FROM attachments WHERE noteUuid = :noteUuid")
    suspend fun deleteForNote(noteUuid: String)

    @Query("SELECT COUNT(*) FROM attachments WHERE uuid = :uuid")
    suspend fun countByUuid(uuid: String): Int

    @Query("UPDATE attachments SET isUploaded = 1 WHERE uuid = :uuid")
    suspend fun markUploaded(uuid: String)

    /** The bytes arrived (from the cloud): usable locally and already uploaded. */
    @Query("UPDATE attachments SET hasLocalFile = 1, isUploaded = 1 WHERE uuid = :uuid")
    suspend fun markDownloaded(uuid: String)

    @Query("SELECT * FROM attachments WHERE hasLocalFile = 1 AND isUploaded = 0 GROUP BY uuid")
    suspend fun pendingUploads(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE hasLocalFile = 0 GROUP BY uuid")
    suspend fun missingFiles(): List<AttachmentEntity>

    @Query("UPDATE attachments SET hasLocalFile = 1 WHERE uuid = :uuid")
    suspend fun markFilePresent(uuid: String)
}
