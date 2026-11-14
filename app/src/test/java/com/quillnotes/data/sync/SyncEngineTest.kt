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
        /** note uuid -> attachment list, as the attachments table would hold it */
        val attachments = linkedMapOf<String, MutableList<AttachmentRef>>()
        /** attachment uuid -> bytes on this phone */
        val files = linkedMapOf<String, ByteArray>()
        val uploadedAttachments = mutableSetOf<String>()
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
        fun attach(noteUuid: String, bytes: ByteArray): AttachmentRef {
            val ref = AttachmentRef(uuid = UUID.randomUUID().toString(), size = bytes.size.toLong())
            files[ref.uuid] = bytes
            attachments.getOrPut(noteUuid) { mutableListOf() } += ref
            edit(noteUuid) { it } // adding an image counts as editing the note
            return ref
        }
        fun detach(noteUuid: String, attachmentUuid: String) {
            attachments[noteUuid]?.removeAll { it.uuid == attachmentUuid }
            if (attachments.values.none { list -> list.any { it.uuid == attachmentUuid } }) files.remove(attachmentUuid)
            edit(noteUuid) { it }
        }
        fun deleteForever(uuid: String) {
            val n = byUuid(uuid)!!
            tombstones[uuid] = TombstoneEntity(uuid, n.cloudFileId, deletedAt = tick())
            notes.remove(n.id)
        }
        fun byUuid(uuid: String) = notes.values.firstOrNull { it.uuid == uuid }
        fun titles() = notes.values.filter { !it.isDeleted }.map { it.title }.sorted()
        fun sync(): SyncResult = runBlocking { SyncEngine(this@Phone, this@Phone) { clock }.run(cloud, key, cloud.listFiles()!!) }

        // SyncStore
        override suspend fun getAllForSync() = notes.values.map { SyncNote(it, attachments[it.uuid].orEmpty().toList()) }
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
                syncedUpdatedAt = payload.updatedAt, cloudFileId = cloudFileId, isDeleted = payload.isTrashed,
                priority = payload.priority, repeatRule = payload.repeatRule, notebook = payload.notebook,
                isArchived = payload.isArchived, isTemplate = payload.isTemplate, isLocked = payload.isLocked)
            notes[n.id] = n
            reconcile(payload)
            return n.id
        }
        private fun reconcile(payload: NotePayload) {
            val old = attachments[payload.uuid].orEmpty()
            val keep = payload.attachments.map { it.uuid }.toSet()
            for (gone in old.filter { it.uuid !in keep }) {
                if (attachments.filterKeys { it != payload.uuid }.values.none { l -> l.any { it.uuid == gone.uuid } }) files.remove(gone.uuid)
            }
            attachments[payload.uuid] = payload.attachments.toMutableList()
            payload.attachments.forEach { uploadedAttachments += it.uuid } // it came from the cloud
        }
        override suspend fun applyFromSync(existing: NoteEntity, payload: NotePayload, cloudFileId: String) {
            notes[existing.id] = existing.copy(title = payload.title, content = payload.content,
                type = payload.noteType(), updatedAt = payload.updatedAt, isPinned = payload.isPinned,
                isCompleted = payload.isCompleted, dueDate = payload.dueDate, mood = payload.mood, tags = payload.tags,
                color = payload.color, isSynced = true, syncedUpdatedAt = payload.updatedAt,
                cloudFileId = cloudFileId, isDeleted = payload.isTrashed,
                priority = payload.priority, repeatRule = payload.repeatRule, notebook = payload.notebook,
                isArchived = payload.isArchived, isTemplate = payload.isTemplate, isLocked = payload.isLocked)
            reconcile(payload)
        }
        override suspend fun deleteFromSync(uuid: String) {
            notes.values.removeAll { it.uuid == uuid }
            attachments.remove(uuid)
        }
        override suspend fun attachmentsToUpload() =
            attachments.values.flatten().filter { it.uuid in files && it.uuid !in uploadedAttachments }.distinctBy { it.uuid }
        override suspend fun readAttachmentBytes(uuid: String) = files[uuid]
        override suspend fun markAttachmentUploaded(uuid: String) { uploadedAttachments += uuid }
        override suspend fun attachmentsToDownload() =
            attachments.values.flatten().filter { it.uuid !in files }.distinctBy { it.uuid }
        override suspend fun storeDownloadedAttachment(uuid: String, bytes: ByteArray) {
            files[uuid] = bytes
            uploadedAttachments += uuid
        }
        override suspend fun addConflictCopy(loser: NotePayload) {
            val n = NoteEntity(id = nextId++, uuid = UUID.randomUUID().toString(),
                title = SyncMerge.conflictTitle(loser.title), content = loser.content, type = loser.noteType(),
                createdAt = loser.createdAt, updatedAt = tick(), isSynced = false)
            notes[n.id] = n
            attachments[n.uuid] = loser.attachments.toMutableList() // shares the original's files
        }

        // ReminderSink
        override fun schedule(taskId: Long, title: String, dueDateMillis: Long) { alarms += taskId }
        override fun cancel(taskId: Long) { alarms -= taskId }
    }

    private class FakeCloud : CloudSyncService {
        val files = linkedMapOf<String, Pair<String, ByteArray>>() // id -> name, data
        val uploadLog = mutableListOf<String>()                    // names in upload order
        var failUploads = false
        private var n = 0
        override suspend fun isAuthenticated() = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun uploadFile(fileId: String?, name: String, data: ByteArray): String? {
            if (failUploads) return null
            val id = fileId ?: "f${++n}"
            uploadLog += name
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

    @Test fun `images travel with their note and are available on the second phone`() {
        val note = a.create("Trip")
        val ref = a.attach(note.uuid, "jpeg-bytes".toByteArray())
        assertTrue(a.sync() is SyncResult.Success)

        val result = b.sync() as SyncResult.Success
        assertEquals(listOf(ref.uuid), b.attachments.getValue(note.uuid).map { it.uuid })
        assertEquals("jpeg-bytes", String(b.files.getValue(ref.uuid)))
        assertTrue(result.downloaded >= 2) // the note and its image
    }

    @Test fun `an image is uploaded before the note that points at it`() {
        val note = a.create("Order")
        val ref = a.attach(note.uuid, byteArrayOf(1, 2, 3))
        a.sync()
        val attachmentAt = cloud.uploadLog.indexOf(attachmentFileName(ref.uuid))
        val noteAt = cloud.uploadLog.indexOf(noteFileName(note.uuid))
        assertTrue("image first", attachmentAt in 0 until noteAt)
    }

    @Test fun `unchanged images are not uploaded again`() {
        val note = a.create("Once")
        a.attach(note.uuid, byteArrayOf(9))
        a.sync()
        val uploads = cloud.uploadLog.size
        a.sync()
        assertEquals(uploads, cloud.uploadLog.size)
    }

    @Test fun `removing an image on one phone removes it on the other`() {
        val note = a.create("Pic")
        val ref = a.attach(note.uuid, byteArrayOf(7))
        a.sync(); b.sync()
        assertTrue(ref.uuid in b.files)

        a.detach(note.uuid, ref.uuid)
        a.sync(); b.sync()
        assertTrue(b.attachments[note.uuid].orEmpty().isEmpty())
        assertFalse(ref.uuid in b.files)
    }

    @Test fun `a note whose image has not been uploaded yet syncs and the image follows later`() {
        val note = a.create("Late")
        val ref = a.attach(note.uuid, byteArrayOf(4, 5))
        // The other phone sees the note first: simulate by syncing it before the image blob exists.
        a.uploadedAttachments += ref.uuid                // A believes it uploaded it (it did not)
        a.sync()
        assertTrue(b.sync() is SyncResult.Success)       // note arrives, image missing is not an error
        assertFalse(ref.uuid in b.files)

        a.uploadedAttachments -= ref.uuid                // A notices and uploads
        a.sync(); b.sync()
        assertTrue(ref.uuid in b.files)
    }

    @Test fun `a conflicted copy keeps the images of the version it preserves`() {
        val n = a.create("Both"); a.sync(); b.sync()
        val ref = a.attach(n.uuid, byteArrayOf(1))
        b.edit(n.uuid) { it.copy(content = "text from B") }
        a.sync(); b.sync(); a.sync(); b.sync()
        // Whichever side lost, both phones end with the image on some note and no data loss.
        for (phone in listOf(a, b)) {
            assertTrue(phone.attachments.values.flatten().any { it.uuid == ref.uuid })
            assertTrue(ref.uuid in phone.files)
        }
    }

    @Test fun `new fields survive a round trip through the cloud`() {
        val n = a.create("Rich")
        a.edit(n.uuid) { it.copy(priority = 3, repeatRule = "1W", notebook = "Work", isArchived = true, isLocked = true) }
        a.sync(); b.sync()
        val got = b.byUuid(n.uuid)!!
        assertEquals(3, got.priority)
        assertEquals("1W", got.repeatRule)
        assertEquals("Work", got.notebook)
        assertTrue(got.isArchived && got.isLocked)
    }

    @Test fun `file names round trip`() {
        val id = UUID.randomUUID().toString()
        assertEquals(id, uuidFromFileName(noteFileName(id)))
        assertNull(uuidFromFileName("note_42.qnote"))
        assertNull(uuidFromFileName(BACKUP_KEY_FILE_NAME))
        assertNotNull(uuidFromFileName("note_${id.uppercase()}.qnote"))
        assertEquals(id, attachmentUuidFromFileName(attachmentFileName(id)))
        assertNull(attachmentUuidFromFileName(noteFileName(id)))
        assertNull(uuidFromFileName(attachmentFileName(id)))
    }
}
