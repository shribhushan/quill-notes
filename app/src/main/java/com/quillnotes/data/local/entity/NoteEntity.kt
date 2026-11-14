package com.quillnotes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Core note entity. All text content (title, content) is stored encrypted.
 * Encryption/decryption happens in the repository layer.
 */
@Entity(tableName = "notes", indices = [Index(value = ["uuid"], unique = true)])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Stable identity used by cloud sync; the local [id] differs between phones. */
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,           // Encrypted
    val content: String,         // Encrypted
    val type: NoteType,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isCompleted: Boolean = false,   // For tasks
    val dueDate: Long? = null,          // For tasks
    val mood: String? = null,           // For journal entries
    val tags: String = "",              // Comma-separated, encrypted
    val color: String? = null,          // Note tone key, see ui.theme.NoteColorKeys
    /** False while the note has local changes the cloud copy does not have yet. */
    val isSynced: Boolean = false,
    /** `updatedAt` of the cloud copy as of the last successful sync (0 = never synced). */
    val syncedUpdatedAt: Long = 0,
    val cloudFileId: String? = null,    // Remote file ID for sync
    val isDeleted: Boolean = false      // Soft delete
)

enum class NoteType {
    NOTE,
    JOURNAL,
    TASK
}
