package com.quillnotes.domain

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinksTest {
    private fun note(id: Long, title: String, content: String = "", updated: Long = id, deleted: Boolean = false) =
        NoteEntity(id = id, title = title, content = content, type = NoteType.NOTE, updatedAt = updated, isDeleted = deleted)

    private val notes = listOf(
        note(1, "Project Atlas", "see [[Budget]] and [[Missing page]]"),
        note(2, "Budget", "numbers. back to [[project atlas]]"),
        note(3, "Ideas", "[[Budget]] could fund [[Project Atlas]]"),
        note(4, "Old", "[[Budget]]", deleted = true)
    )
    private val index = NoteLinks.index(notes)

    @Test fun `outgoing links resolve by title ignoring case, unknown titles stay unresolved`() {
        val info = NoteLinks.info(index, 1, "Project Atlas", notes[0].content)
        assertEquals(listOf("Budget", "Missing page"), info.outgoing.map { it.title })
        assertEquals(2L, info.outgoing[0].target?.id)
        assertNull(info.outgoing[1].target)
    }

    @Test fun `backlinks are the other notes that link here, trashed ones excluded`() {
        val info = NoteLinks.info(index, 2, "Budget", notes[1].content)
        assertEquals(listOf("Ideas", "Project Atlas"), info.backlinks.map { it.title })
    }

    @Test fun `a note does not link to itself`() {
        val info = NoteLinks.info(index, 2, "Budget", "[[Budget]]")
        assertNull(info.outgoing.single().target)
        assertTrue(info.backlinks.none { it.id == 2L })
    }

    @Test fun `an unsaved or untitled note has no backlinks`() {
        assertTrue(NoteLinks.info(index, null, "", "text").backlinks.isEmpty())
        assertTrue(NoteLinks.info(index, null, "Brand new", "").backlinks.isEmpty())
    }

    @Test fun `the most recently edited note wins a shared title`() {
        val dupes = NoteLinks.index(listOf(note(1, "Same", updated = 5), note(2, "Same", updated = 9)))
        assertEquals(2L, NoteLinks.resolve(dupes, " same ")?.id)
    }

    @Test fun `templates are never link targets`() {
        val idx = NoteLinks.index(listOf(note(1, "Tpl").copy(isTemplate = true)))
        assertNull(NoteLinks.resolve(idx, "Tpl"))
    }
}
