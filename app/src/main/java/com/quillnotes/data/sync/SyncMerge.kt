package com.quillnotes.data.sync

/** A local note as the merge sees it. */
data class LocalNote(
    val payload: NotePayload,
    /** Has local changes the cloud has not seen. */
    val dirty: Boolean,
    /** Cloud `updatedAt` as of the last successful sync. */
    val syncedUpdatedAt: Long
)

/** A locally recorded permanent delete. */
data class LocalTombstone(val deletedAt: Long)

sealed interface MergeAction {
    /** Nothing to do. */
    data object Skip : MergeAction
    /** No local copy: create it from the cloud copy. */
    data object CreateLocal : MergeAction
    /** Overwrite the local copy with the cloud copy. */
    data object ApplyRemote : MergeAction
    /** Local copy is newer or the cloud has none: upload it. */
    data object PushLocal : MergeAction
    /** Local deletion is newer: upload the tombstone over the cloud copy. */
    data object PushTombstone : MergeAction
    /** Cloud says permanently deleted: remove the local copy. */
    data object DeleteLocal : MergeAction
    /** Both copies are already identical: just record the sync. */
    data object MarkSynced : MergeAction
    /**
     * Both sides changed. The most recently edited version keeps the note's identity;
     * the other is preserved as a separate "conflicted copy" (unless it was trashed).
     */
    data class KeepBoth(val localWins: Boolean) : MergeAction
}

/** Decides what to do when a cloud copy of a note is seen. Pure so it can be unit-tested. */
object SyncMerge {

    fun decide(local: LocalNote?, remote: NotePayload, tombstone: LocalTombstone?): MergeAction {
        if (remote.isPurged) return decidePurged(local, remote)

        if (local == null) {
            // Deleted here after the cloud copy was last written: the deletion wins.
            return if (tombstone != null && tombstone.deletedAt >= remote.updatedAt) MergeAction.PushTombstone
            else MergeAction.CreateLocal
        }

        val remoteChanged = remote.updatedAt != local.syncedUpdatedAt
        return when {
            !remoteChanged -> if (local.dirty) MergeAction.PushLocal else MergeAction.Skip
            !local.dirty -> MergeAction.ApplyRemote
            local.payload.sameContentAs(remote) -> MergeAction.MarkSynced
            else -> MergeAction.KeepBoth(localWins = local.payload.updatedAt >= remote.updatedAt)
        }
    }

    private fun decidePurged(local: LocalNote?, remote: NotePayload): MergeAction = when {
        local == null -> MergeAction.Skip
        // An edit made after the other phone deleted the note keeps the note alive.
        local.dirty && local.payload.updatedAt > remote.updatedAt -> MergeAction.PushLocal
        else -> MergeAction.DeleteLocal
    }

    /** Title for the preserved loser of a [MergeAction.KeepBoth]. */
    fun conflictTitle(title: String): String {
        val base = title.ifBlank { "Untitled" }
        return "$base (conflicted copy)"
    }
}
