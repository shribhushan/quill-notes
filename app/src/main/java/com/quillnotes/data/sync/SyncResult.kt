package com.quillnotes.data.sync

sealed class SyncResult {
    data class Success(
        val uploaded: Int,
        val downloaded: Int,
        /** Notes edited on two phones; the older edit was kept as a "conflicted copy". */
        val conflicts: Int = 0
    ) : SyncResult() {
        val count: Int get() = uploaded + downloaded
    }

    data class Failure(val reason: String) : SyncResult()

    /** No backup key exists anywhere yet: the user must choose a recovery passphrase. */
    data object NeedsBackupSetup : SyncResult()

    /** A backup exists in the cloud but this phone cannot read it yet: ask for the passphrase. */
    data object NeedsPassphrase : SyncResult()
}

enum class BackupKeyState { READY, NEEDS_SETUP, NEEDS_PASSPHRASE, UNKNOWN }
