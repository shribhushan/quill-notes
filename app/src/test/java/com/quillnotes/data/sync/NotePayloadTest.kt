package com.quillnotes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePayloadTest {

    private val full = NotePayload(
        uuid = "abc", title = "Quote \" and \\ and\nnewline", content = "emoji 🪶 ünï", type = "TASK",
        createdAt = 1, updatedAt = 2, isPinned = true, isCompleted = true, dueDate = 99, mood = "😊",
        tags = "a,b", color = "blue", isTrashed = true,
        priority = 2, repeatRule = "1W", notebook = "Work", isArchived = true, isTemplate = true, isLocked = true,
        attachments = listOf(AttachmentRef("att-1", "image/png", 640, 480, 1234, 5))
    )

    @Test fun `round trips every field including pin, colour and trash`() {
        assertEquals(full, NotePayload.decode(NotePayload.encode(full)))
    }

    @Test fun `ignores fields added by newer minor versions`() {
        val text = NotePayload.encode(full).dropLast(1) + ""","futureField":123}"""
        assertEquals(full, NotePayload.decode(text))
    }

    @Test fun `rejects payloads from a newer format or legacy v1 files`() {
        assertNull(NotePayload.decode(NotePayload.encode(full).replace("\"v\":${NotePayload.FORMAT_VERSION}", "\"v\":99")))
        assertNull(NotePayload.decode("""{"id":7,"title":"old","content":"","type":"NOTE","createdAt":1,"updatedAt":2}"""))
        assertNull(NotePayload.decode("not json"))
    }

    @Test fun `payloads written before the new fields existed still decode with defaults`() {
        val v2 = """{"v":2,"uuid":"abc","title":"t","content":"c","type":"NOTE","createdAt":1,"updatedAt":2,"isPinned":true}"""
        val p = NotePayload.decode(v2)!!
        assertEquals(0, p.priority)
        assertNull(p.notebook)
        assertTrue(p.attachments.isEmpty())
        assertTrue(p.isPinned)
    }

    @Test fun `tombstone is purged and blank`() {
        val t = NotePayload.tombstone("abc", "NOTE", 5)
        assertTrue(t.isPurged)
        assertEquals("", t.title + t.content)
    }

    @Test fun `same content ignores timestamps only`() {
        assertTrue(full.sameContentAs(full.copy(updatedAt = 500, createdAt = 7)))
        assertFalse(full.sameContentAs(full.copy(isPinned = false)))
        assertFalse(full.sameContentAs(full.copy(color = null)))
        assertFalse(full.sameContentAs(full.copy(notebook = "Home")))
        assertFalse(full.sameContentAs(full.copy(attachments = emptyList())))
    }
}
