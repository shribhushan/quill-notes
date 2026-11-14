package com.quillnotes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A file (currently images) attached to a note. The bytes live encrypted on disk under the
 * attachment's [uuid], not in the database. Several notes may reference the same [uuid] (a
 * sync "conflicted copy" shares its original's images), so the file is only removed once the
 * last reference goes.
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["noteUuid", "uuid"], unique = true), Index(value = ["uuid"])]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val noteUuid: String,
    val mimeType: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** False for an attachment announced by sync whose bytes have not been downloaded yet. */
    val hasLocalFile: Boolean = true,
    /** True once the encrypted bytes are in the cloud (or came from it). */
    val isUploaded: Boolean = false
)
