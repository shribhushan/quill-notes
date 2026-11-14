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
        tags = "a,b", color = "blue", isTrashed = true
    )

    @Test fun `round trips every field including pin, colour and trash`() {
        assertEquals(full, NotePayload.decode(NotePayload.encode(full)))
    }

    @Test fun `ignores fields added by newer minor versions`() {
        val text = NotePayload.encode(full).dropLast(1) + ""","futureField":123}"""
        assertEquals(full, NotePayload.decode(text))
    }

    @Test fun `rejects payloads from a newer format or legacy v1 files`() {
        assertNull(NotePayload.decode(NotePayload.encode(full).replace("\"v\":2", "\"v\":99")))
        assertNull(NotePayload.decode("""{"id":7,"title":"old","content":"","type":"NOTE","createdAt":1,"updatedAt":2}"""))
        assertNull(NotePayload.decode("not json"))
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
    }
}
