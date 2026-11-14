package com.quillnotes.data.sync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.quillnotes.R

enum class SyncProvider(
    val key: String,
    val displayName: String,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    /** False while the sign-in for this provider is not finished; the Sync screen only lists available ones. */
    val isAvailable: Boolean = true
) {
    NONE(
        key = "none",
        displayName = "None",
        descriptionRes = R.string.sync_provider_none_desc,
        icon = Icons.Outlined.Cloud,
        isAvailable = false
    ),
    GOOGLE_DRIVE(
        key = "google_drive",
        displayName = "Google Drive",
        descriptionRes = R.string.sync_provider_drive_desc,
        icon = Icons.Outlined.Cloud
    ),
    ONEDRIVE(
        key = "onedrive",
        displayName = "Microsoft OneDrive",
        descriptionRes = R.string.sync_provider_onedrive_desc,
        icon = Icons.Outlined.CloudQueue,
        // MSAL sign-in is not wired up (OneDriveSyncService.signIn always returns false), so do not offer it.
        isAvailable = false
    );

    companion object {
        fun fromKey(key: String): SyncProvider =
            entries.find { it.key == key } ?: NONE
    }
}
