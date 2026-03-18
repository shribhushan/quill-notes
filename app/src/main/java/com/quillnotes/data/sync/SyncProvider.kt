package com.quillnotes.data.sync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.ui.graphics.vector.ImageVector

enum class SyncProvider(
    val key: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector
) {
    NONE(
        key = "none",
        displayName = "None",
        description = "No sync configured",
        icon = Icons.Outlined.Cloud
    ),
    GOOGLE_DRIVE(
        key = "google_drive",
        displayName = "Google Drive",
        description = "Sync encrypted notes to your Google Drive",
        icon = Icons.Outlined.Cloud
    ),
    ONEDRIVE(
        key = "onedrive",
        displayName = "Microsoft OneDrive",
        description = "Sync encrypted notes to your OneDrive",
        icon = Icons.Outlined.CloudQueue
    );

    companion object {
        fun fromKey(key: String): SyncProvider =
            entries.find { it.key == key } ?: NONE
    }
}
