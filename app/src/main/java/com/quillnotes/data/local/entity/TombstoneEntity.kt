package com.quillnotes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Left behind when a note is permanently deleted, so the deletion can be
 * propagated to the cloud and to other phones instead of the note coming back.
 */
@Entity(tableName = "tombstones")
data class TombstoneEntity(
    @PrimaryKey val uuid: String,
    val cloudFileId: String? = null,
    val deletedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
