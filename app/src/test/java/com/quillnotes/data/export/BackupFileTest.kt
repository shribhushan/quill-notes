package com.quillnotes.data.export

import com.quillnotes.data.sync.AttachmentRef
import com.quillnotes.data.sync.NotePayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class BackupFileTest {
    private val fast = 1_000
    private val pass = "correct horse battery".toCharArray()

    private fun note(i: Int, attachments: List<AttachmentRef> = emptyList()) = NotePayload(
        uuid = "n$i", title = "Note $i 🪶", content = "body $i\n- [ ] item", type = "NOTE",
        createdAt = i.toLong(), updatedAt = i * 10L, tags = "t$i", attachments = attachments
    )

    private fun backup(notes: List<NotePayload>, images: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        BackupFile.write(out, pass, notes, { images[it] }, now = 99, iterations = fast)
        return out.toByteArray()
    }

    private class Read(val notes: List<NotePayload>, val images: Map<String, ByteArray>, val manifest: BackupFile.Manifest)

    private fun restore(bytes: ByteArray, passphrase: CharArray = pass): Read {
        val notes = ArrayList<NotePayload>()
        val images = LinkedHashMap<String, ByteArray>()
        val m = BackupFile.read(ByteArrayInputStream(bytes), passphrase, { notes += it }, { u, b -> images[u] = b })
        return Read(notes, images, m)
    }

    @Test fun `round trips notes and images`() {
        val img = ByteArray(300_000).also { Random(7).nextBytes(it) } // spans several 64 KB segments
        val notes = listOf(note(1, listOf(AttachmentRef("img1", size = img.size.toLong()))), note(2))
        val r = restore(backup(notes, mapOf("img1" to img)))
        assertEquals(notes, r.notes)
        assertArrayEquals(img, r.images.getValue("img1"))
        assertEquals(BackupFile.Manifest(createdAt = 99, noteCount = 2, attachmentCount = 1), r.manifest)
    }

    @Test fun `an image shared by two notes is stored once`() {
        val ref = listOf(AttachmentRef("shared"))
        val r = restore(backup(listOf(note(1, ref), note(2, ref)), mapOf("shared" to byteArrayOf(5))))
        assertEquals(1, r.images.size)
        assertEquals(1, r.manifest.attachmentCount)
    }

    @Test fun `images that are not on the phone are skipped`() {
        val r = restore(backup(listOf(note(1, listOf(AttachmentRef("gone")))), emptyMap()))
        assertEquals(1, r.notes.size)
        assertTrue(r.images.isEmpty())
    }

    @Test fun `an empty backup is valid`() {
        val r = restore(backup(emptyList()))
        assertTrue(r.notes.isEmpty())
        assertEquals(0, r.manifest.noteCount)
    }

    @Test fun `the wrong passphrase is reported as such`() {
        val bytes = backup(listOf(note(1)))
        try { restore(bytes, "wrong".toCharArray()); fail("expected BadPassphraseException") }
        catch (e: BadPassphraseException) { /* expected */ }
    }

    @Test fun `the file does not contain readable text`() {
        val text = String(backup(listOf(note(1))), Charsets.ISO_8859_1)
        assertTrue("Note 1" !in text)
        assertTrue("body 1" !in text)
        assertTrue(text.startsWith("QUILLBACKUP1"))
    }

    @Test fun `files that are not backups are rejected cleanly`() {
        for (junk in listOf(ByteArray(0), "hello".toByteArray(), ByteArray(200) { it.toByte() }, "QUILLBACKUP1\n".toByteArray())) {
            try { restore(junk); fail("expected InvalidBackupException") }
            catch (e: InvalidBackupException) { /* expected */ }
        }
    }

    @Test fun `tampering is detected, never silently accepted`() {
        val bytes = backup(listOf(note(1), note(2)))
        for (offset in listOf(bytes.size - 5, bytes.size / 2, 60)) {
            val bad = bytes.copyOf().also { it[offset] = (it[offset] + 1).toByte() }
            try { restore(bad); fail("tampering at $offset went unnoticed") }
            catch (e: BadPassphraseException) { }
            catch (e: InvalidBackupException) { }
        }
    }

    @Test fun `a truncated backup is reported as damaged`() {
        val bytes = backup(listOf(note(1), note(2), note(3)))
        try { restore(bytes.copyOf(bytes.size - 40)); fail("expected an error") }
        catch (e: InvalidBackupException) { }
        catch (e: BadPassphraseException) { }
    }

    @Test fun `two backups of the same data differ`() {
        assertTrue(!backup(listOf(note(1))).contentEquals(backup(listOf(note(1)))))
    }

    @Test fun `import policy never overwrites a newer local edit`() {
        val b = note(1) // updatedAt = 10
        assertEquals(ImportAction.ADD, ImportPolicy.decide(null, b))
        assertEquals(ImportAction.REPLACE, ImportPolicy.decide(5, b))
        assertEquals(ImportAction.SKIP, ImportPolicy.decide(10, b))
        assertEquals(ImportAction.SKIP, ImportPolicy.decide(50, b))
        assertEquals(ImportAction.SKIP, ImportPolicy.decide(null, b.copy(isPurged = true)))
    }
}
