package com.quillnotes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergeTest {

    private fun payload(updatedAt: Long, title: String = "t", purged: Boolean = false) = NotePayload(
        uuid = "u1", title = title, type = "NOTE", createdAt = 1, updatedAt = updatedAt, isPurged = purged
    )

    private fun local(updatedAt: Long, dirty: Boolean, synced: Long, title: String = "t") =
        LocalNote(payload(updatedAt, title), dirty, synced)

    private fun decide(l: LocalNote?, r: NotePayload, t: LocalTombstone? = null) = SyncMerge.decide(l, r, t)

    @Test fun `new phone creates notes it has never seen`() =
        assertEquals(MergeAction.CreateLocal, decide(null, payload(10)))

    @Test fun `unchanged on both sides is skipped`() =
        assertEquals(MergeAction.Skip, decide(local(10, dirty = false, synced = 10), payload(10)))

    @Test fun `local-only edit is pushed`() =
        assertEquals(MergeAction.PushLocal, decide(local(20, dirty = true, synced = 10), payload(10)))

    @Test fun `remote-only edit is applied`() =
        assertEquals(MergeAction.ApplyRemote, decide(local(10, dirty = false, synced = 10), payload(30, "new")))

    @Test fun `identical edits on both sides just mark synced`() =
        assertEquals(MergeAction.MarkSynced, decide(local(20, dirty = true, synced = 10, "same"), payload(30, "same")))

    @Test fun `both edited keeps both and newer side wins`() {
        assertEquals(
            MergeAction.KeepBoth(localWins = true),
            decide(local(40, dirty = true, synced = 10, "mine"), payload(30, "theirs"))
        )
        assertEquals(
            MergeAction.KeepBoth(localWins = false),
            decide(local(20, dirty = true, synced = 10, "mine"), payload(30, "theirs"))
        )
    }

    @Test fun `never-synced local note colliding with cloud copy is a conflict, not an overwrite`() =
        assertEquals(
            MergeAction.KeepBoth(localWins = false),
            decide(local(5, dirty = true, synced = 0, "mine"), payload(30, "theirs"))
        )

    @Test fun `remote tombstone deletes a clean local note`() =
        assertEquals(MergeAction.DeleteLocal, decide(local(10, dirty = false, synced = 10), payload(50, purged = true)))

    @Test fun `remote tombstone loses to a later local edit`() =
        assertEquals(MergeAction.PushLocal, decide(local(60, dirty = true, synced = 10), payload(50, purged = true)))

    @Test fun `remote tombstone with no local note is ignored`() =
        assertEquals(MergeAction.Skip, decide(null, payload(50, purged = true)))

    @Test fun `local permanent delete newer than cloud copy pushes tombstone`() =
        assertEquals(MergeAction.PushTombstone, decide(null, payload(10), LocalTombstone(deletedAt = 20)))

    @Test fun `cloud edit made after local delete resurrects the note`() =
        assertEquals(MergeAction.CreateLocal, decide(null, payload(30), LocalTombstone(deletedAt = 20)))

    @Test fun `conflict title falls back for blank titles`() {
        assertEquals("Groceries (conflicted copy)", SyncMerge.conflictTitle("Groceries"))
        assertEquals("Untitled (conflicted copy)", SyncMerge.conflictTitle(""))
    }
}
