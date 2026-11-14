package com.quillnotes.data.sync

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.local.entity.TombstoneEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/** Two phones sharing one cloud folder and one backup key, as in the Phase 1 "done when" scenario. */
class SyncEngineTest {

    private var clock = 1_000L
    private val key = BackupKeyCrypto.newDataKey()
    private lateinit var cloud: FakeCloud
    private lateinit var a: Phone
    private lateinit var b: Phone

    @Before fun setUp() {
        cloud = FakeCloud()
        a = Phone()
        b = Phone()
    }

    // ── Fakes ──────────────────────────────────────────────

    private inner class Phone : SyncStore, ReminderSink {
        val notes = linkedMapOf<Long, NoteEntity>()
        val tombstones = linkedMapOf<String, TombstoneEntity>()
        val alarms = mutableSetOf<Long>()
        private var nextId = 1L

        // user actions (mirror NoteRepository: every edit stamps updatedAt and clears isSynced)
        fun create(title: String, type: NoteType = NoteType.NOTE, due: Long? = null): NoteEntity {
            val n = NoteEntity(id = nextId++, title = title, content = "", type = type, dueDate = due,
                createdAt = tick(), updatedAt = clock)
            notes[n.id] = n
            return n
        }
        fun edit(uuid: String, change: (NoteEntity) -> NoteEntity) {
            val n = byUuid(uuid)!!
            notes[n.id] = change(n).copy(isSynced = false, updatedAt = tick())
        }
        fun trash(uuid: String) = edit(uuid) { it.copy(isDeleted = true) }
        fun deleteForever(uuid: String) {
            val n = byUuid(uuid)!!
            tombstones[uuid] = TombstoneEntity(uuid, n.cloudFileId, deletedAt = tick())
            notes.remove(n.id)
        }
        fun byUuid(uuid: String) = notes.values.firstOrNull { it.uuid == uuid }
        fun titles() = notes.values.filter { !it.isDeleted }.map { it.title }.sorted()
        fun sync(): SyncResult = runBlocking { SyncEngine(this@Phone, this@Phone) { clock }.run(cloud, key, cloud.listFiles()!!) }

        // SyncStore
        override suspend fun getAllForSync() = notes.values.toList()
        override suspend fun getNoteById(id: Long) = notes[id]
        override suspend fun getTombstones() = tombstones.values.toList()
        override suspend fun markTombstoneSynced(uuid: String, cloudFileId: String) {
            tombstones[uuid] = tombstones.getValue(uuid).copy(isSynced = true, cloudFileId = cloudFileId)
        }
        override suspend fun dropTombstone(uuid: String) { tombstones.remove(uuid) }
        override suspend fun markSynced(id: Long, cloudFileId: String, updatedAt: Long, syncedUpdatedAt: Long) {
            val n = notes[id] ?: return
            if (n.updatedAt == updatedAt) notes[id] = n.copy(isSynced = true, cloudFileId = cloudFileId, syncedUpdatedAt = syncedUpdatedAt)
        }
        override suspend fun insertFromSync(payload: NotePayload, cloudFileId: String): Long {
            val n = NoteEntity(id = nextId++, uuid = payload.uuid, title = payload.title, content = payload.content,
                type = payload.noteType(), createdAt = payload.createdAt, updatedAt = payload.updatedAt,
                isPinned = payload.isPinned, isCompleted = payload.isCompleted, dueDate = payload.dueDate,
                mood = payload.mood, tags = payload.tags, color = payload.color, isSynced = true,
                syncedUpdatedAt = payload.updatedAt, cloudFileId = cloudFileId, isDeleted = payload.isTrashed)
            notes[n.id] = n
            return n.id
        }
        override suspend fun applyFromSync(existing: NoteEntity, payload: NotePayload, cloudFileId: String) {
            notes[existing.id] = existing.copy(title = payload.title, content = payload.content,
                type = payload.noteType(), updatedAt = payload.updatedAt, isPinned = payload.isPinned,
                isCompleted = payload.isCompleted, dueDate = payload.dueDate, mood = payload.mood, tags = payload.tags,
                color = payload.color, isSynced = true, syncedUpdatedAt = payload.updatedAt,
                cloudFileId = cloudFileId, isDeleted = payload.isTrashed)
        }
        override suspend fun deleteFromSync(uuid: String) { notes.values.removeAll { it.uuid == uuid } }
        override suspend fun addConflictCopy(loser: NotePayload) {
            val n = NoteEntity(id = nextId++, uuid = UUID.randomUUID().toString(),
                title = SyncMerge.conflictTitle(loser.title), content = loser.content, type = loser.noteType(),
                createdAt = loser.createdAt, updatedAt = tick(), isSynced = false)
            notes[n.id] = n
        }

        // ReminderSink
        override fun schedule(taskId: Long, title: String, dueDateMillis: Long) { alarms += taskId }
        override fun cancel(taskId: Long) { alarms -= taskId }
    }

    private class FakeCloud : CloudSyncService {
        val files = linkedMapOf<String, Pair<String, ByteArray>>() // id -> name, data
        var failUploads = false
        private var n = 0
        override suspend fun isAuthenticated() = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun uploadFile(fileId: String?, name: String, data: ByteArray): String? {
            if (failUploads) return null
            val id = fileId ?: "f${++n}"
            files[id] = name to data
            return id
        }
        override suspend fun downloadFile(fileId: String) = files[fileId]?.second
        override suspend fun deleteFile(fileId: String) { files.remove(fileId) }
        override suspend fun listFiles(): Map<String, String> = files.mapValues { it.value.first }
    }

    private fun tick() = ++clock

    // ── Scenarios ──────────────────────────────────────────

    @Test fun `a second phone restores every note, task and trashed item`() {
        val note = a.create("Groceries")
        a.edit(note.uuid) { it.copy(content = "milk", isPinned = true, color = "green", tags = "home") }
        a.create("Pay rent", NoteType.TASK, due = clock + 100_000)
        val old = a.create("Old")
        a.trash(old.uuid)
        assertTrue(a.sync() is SyncResult.Success)

        val result = b.sync()
        assertEquals(3, (result as SyncResult.Success).downloaded)

        val restored = b.byUuid(note.uuid)!!
        assertEquals("milk", restored.content)
        assertTrue(restored.isPinned)
        assertEquals("green", restored.color)
        assertEquals("home", restored.tags)
        assertTrue(b.byUuid(old.uuid)!!.isDeleted)
        assertEquals(1, b.alarms.size) // the task's reminder was scheduled on the new phone
        assertTrue(b.notes.values.all { it.isSynced })
    }

    @Test fun `second sync with no changes transfers nothing`() {
        a.create("x"); a.sync()
        val again = a.sync() as SyncResult.Success
        assertEquals(0, again.uploaded + again.downloaded)
    }

    @Test fun `edits, pin and trash flow both ways`() {
        val n = a.create("one"); a.sync(); b.sync()
        b.edit(n.uuid) { it.copy(title = "one (b)", isPinned = true) }; b.sync()
        a.sync()
        assertEquals("one (b)", a.byUuid(n.uuid)!!.title)
        assertTrue(a.byUuid(n.uuid)!!.isPinned)
        a.trash(n.uuid); a.sync(); b.sync()
        assertTrue(b.byUuid(n.uuid)!!.isDeleted)
    }

    @Test fun `editing on both phones keeps both versions and the phones converge`() {
        val n = a.create("Plan"); a.sync(); b.sync()
        a.edit(n.uuid) { it.copy(content = "from A") }
        b.edit(n.uuid) { it.copy(content = "from B") } // later edit wins the original note
        val resultA = a.sync() as SyncResult.Success
        val resultB = b.sync() as SyncResult.Success
        a.sync(); b.sync()

        assertEquals(0, resultA.conflicts)
        assertEquals(1, resultB.conflicts)
        for (phone in listOf(a, b)) {
            assertEquals(2, phone.notes.size)
            assertEquals("from B", phone.byUuid(n.uuid)!!.content)
            val copy = phone.notes.values.single { it.uuid != n.uuid }
            assertEquals("Plan (conflicted copy)", copy.title)
            assertEquals("from A", copy.content)
        }
        assertEquals(a.notes.values.map { it.uuid }.toSet(), b.notes.values.map { it.uuid }.toSet())
    }

    @Test fun `identical edits on both phones do not create a copy`() {
        val n = a.create("Same"); a.sync(); b.sync()
        a.edit(n.uuid) { it.copy(content = "x") }
        b.edit(n.uuid) { it.copy(content = "x") }
        a.sync(); b.sync(); a.sync()
        assertEquals(1, a.notes.size)
        assertEquals(1, b.notes.size)
        assertTrue(a.notes.values.all { it.isSynced } && b.notes.values.all { it.isSynced })
    }

    @Test fun `permanent delete propagates and the note stays gone`() {
        val n = a.create("Secret"); a.sync(); b.sync()
        a.deleteForever(n.uuid)
        a.sync(); b.sync()
        assertNull(b.byUuid(n.uuid))
        a.sync(); b.sync()
        assertTrue(a.notes.isEmpty() && b.notes.isEmpty())
        assertTrue(a.tombstones.getValue(n.uuid).isSynced)
    }

    @Test fun `a note edited after the other phone deleted it survives`() {
        val n = a.create("Keep me"); a.sync(); b.sync()
        a.deleteForever(n.uuid); a.sync()
        b.edit(n.uuid) { it.copy(content = "still needed") }
        b.sync(); a.sync()
        assertEquals("still needed", b.byUuid(n.uuid)!!.content)
        assertEquals("still needed", a.byUuid(n.uuid)!!.content) // resurrected on A
        assertNull(a.tombstones[n.uuid])
    }

    @Test fun `failed uploads stay pending and retry`() {
        a.create("flaky")
        cloud.failUploads = true
        assertTrue(a.sync() is SyncResult.Failure)
        assertFalse(a.notes.values.single().isSynced)
        cloud.failUploads = false
        assertTrue(a.sync() is SyncResult.Success)
        assertTrue(a.notes.values.single().isSynced)
        assertEquals(1, cloud.files.size)
    }

    @Test fun `files encrypted with another key are reported and never overwritten`() {
        a.create("mine"); a.sync()
        val foreign = BackupKeyCrypto.newDataKey()
        val result = runBlocking { SyncEngine(b, b) { clock }.run(cloud, foreign, cloud.listFiles()) }
        assertTrue(result is SyncResult.Failure)
        assertTrue(b.notes.isEmpty())
        assertEquals(1, cloud.files.size)
    }

    @Test fun `legacy and unrelated cloud files are ignored`() {
        runBlocking {
            cloud.uploadFile(null, "note_7.qnote", byteArrayOf(1, 2, 3))
            cloud.uploadFile(null, BACKUP_KEY_FILE_NAME, byteArrayOf(9))
        }
        assertTrue(b.sync() is SyncResult.Success)
        assertTrue(b.notes.isEmpty())
    }

    @Test fun `file names round trip`() {
        val id = UUID.randomUUID().toString()
        assertEquals(id, uuidFromFileName(noteFileName(id)))
        assertNull(uuidFromFileName("note_42.qnote"))
        assertNull(uuidFromFileName(BACKUP_KEY_FILE_NAME))
        assertNotNull(uuidFromFileName("note_${id.uppercase()}.qnote"))
    }
}
