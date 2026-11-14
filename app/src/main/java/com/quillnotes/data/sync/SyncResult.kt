package com.quillnotes.data.sync

/** Passphrases shorter than this are refused when a backup is first set up. */
const val MIN_PASSPHRASE_LENGTH = 8

/** Why a backup step or a sync failed. The UI turns each into translated text. */
enum class SyncError {
    NOT_CONNECTED, OFFLINE, PASSPHRASE_TOO_SHORT, BACKUP_EXISTS, KEY_UPLOAD_FAILED,
    NO_BACKUP_FOUND, KEY_DOWNLOAD_FAILED, KEY_UNREADABLE, WRONG_PASSPHRASE,

    /** Some files could not be transferred; they are retried on the next sync. */
    PARTIAL,

    /** Some cloud files could not be decrypted with this phone's backup key. */
    UNDECRYPTABLE
}

sealed class SyncResult {
    data class Success(
        val uploaded: Int,
        val downloaded: Int,
        /** Notes edited on two phones; the older edit was kept as a "conflicted copy". */
        val conflicts: Int = 0
    ) : SyncResult() {
        val count: Int get() = uploaded + downloaded
    }

    data class Failure(val error: SyncError, val count: Int = 0) : SyncResult()

    /** No backup key exists anywhere yet: the user must choose a recovery passphrase. */
    data object NeedsBackupSetup : SyncResult()

    /** A backup exists in the cloud but this phone cannot read it yet: ask for the passphrase. */
    data object NeedsPassphrase : SyncResult()
}

enum class BackupKeyState { READY, NEEDS_SETUP, NEEDS_PASSPHRASE, UNKNOWN }
