package com.quillnotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Core note entity. All text content (title, content) is stored encrypted.
 * Encryption/decryption happens in the repository layer.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val color: String? = null,          // Accent label key, see ui.theme.noteColorPalette
    val isSynced: Boolean = false,
    val cloudFileId: String? = null,    // Remote file ID for sync
    val isDeleted: Boolean = false      // Soft delete
)

enum class NoteType {
    NOTE,
    JOURNAL,
    TASK
}
