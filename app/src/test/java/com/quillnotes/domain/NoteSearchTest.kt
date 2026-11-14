package com.quillnotes.domain

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {
    private var id = 0L
    private fun note(
        title: String, content: String = "", type: NoteType = NoteType.NOTE, tags: String = "",
        notebook: String? = null, updated: Long = 1000, pinned: Boolean = false,
        archived: Boolean = false, deleted: Boolean = false
    ) = NoteEntity(
        id = ++id, title = title, content = content, type = type, tags = tags, notebook = notebook,
        updatedAt = updated, isPinned = pinned, isArchived = archived, isDeleted = deleted
    )

    private fun titles(hits: List<SearchHit>) = hits.map { it.note.title }

    @Test fun `finds notes, journal entries and tasks in one pass`() {
        val all = listOf(
            note("Garden plan", "tomatoes and basil"),
            note("Tuesday", "planted the tomatoes", type = NoteType.JOURNAL),
            note("Buy tomato cages", type = NoteType.TASK)
        )
        assertEquals(3, NoteSearch.search(all, "tomato").size)
    }

    @Test fun `every word must match somewhere in the note`() {
        val all = listOf(note("Trip", "flights to Pune"), note("Pune", "restaurants"), note("Goa", "beaches"))
        assertEquals(listOf("Trip"), titles(NoteSearch.search(all, "pune flights")))
        assertTrue(NoteSearch.search(all, "pune beaches").isEmpty())
    }

    @Test fun `title matches rank above body matches, then recency`() {
        val all = listOf(
            note("Misc", "budget numbers", updated = 3000),
            note("Budget", "", updated = 1000),
            note("Other budget", "", updated = 2000)
        )
        assertEquals(listOf("Budget", "Other budget", "Misc"), titles(NoteSearch.search(all, "budget")))
    }

    @Test fun `tags and notebooks are searchable and hash is ignored`() {
        val all = listOf(note("A", tags = "travel, food"), note("B", notebook = "Recipes"), note("C"))
        assertEquals(listOf("A"), titles(NoteSearch.search(all, "#travel")))
        assertEquals(listOf("B"), titles(NoteSearch.search(all, "recipes")))
    }

    @Test fun `matching ignores case and accents`() {
        val all = listOf(note("Café crème", "Zürich"))
        assertEquals(1, NoteSearch.search(all, "cafe creme").size)
        assertEquals(1, NoteSearch.search(all, "ZURICH").size)
        assertEquals(1, NoteSearch.search(all, "zürich").size)
    }

    @Test fun `fold keeps indexes and leaves devanagari alone`() {
        val s = "Crème brûlée"
        assertEquals(s.length, NoteSearch.fold(s).length)
        val hindi = "नमस्ते दुनिया"
        assertEquals(hindi, NoteSearch.fold(hindi))
        assertEquals(1, NoteSearch.search(listOf(note("x", hindi)), "दुनिया").size)
    }

    @Test fun `title highlights point at the matched text`() {
        val hit = NoteSearch.search(listOf(note("Weekly review notes")), "review").single()
        assertEquals(listOf(7..12), hit.titleMatches)
        assertEquals("review", "Weekly review notes".substring(hit.titleMatches[0].first, hit.titleMatches[0].last + 1))
    }

    @Test fun `snippet is centred on the first body match with highlights that line up`() {
        val body = "Lorem ipsum ".repeat(30) + "the NEEDLE is here " + "dolor sit ".repeat(30)
        val hit = NoteSearch.search(listOf(note("Long", body)), "needle").single()
        assertTrue(hit.snippet.startsWith("…") && hit.snippet.endsWith("…"))
        assertTrue(hit.snippet.length < 200)
        val r = hit.snippetMatches.single()
        assertEquals("needle", hit.snippet.substring(r.first, r.last + 1).lowercase())
    }

    @Test fun `snippet shows markdown as plain text`() {
        val hit = NoteSearch.search(listOf(note("List", "- [ ] buy **milk** today")), "milk").single()
        assertEquals("☐ buy milk today", hit.snippet)
        val r = hit.snippetMatches.single()
        assertEquals("milk", hit.snippet.substring(r.first, r.last + 1))
    }

    @Test fun `trashed notes and templates never show up`() {
        val all = listOf(note("Gone budget", deleted = true), note("Template budget").copy(isTemplate = true), note("Real budget"))
        assertEquals(listOf("Real budget"), titles(NoteSearch.search(all, "budget")))
    }

    @Test fun `filters narrow by kind, tag, notebook, archive and pin`() {
        val all = listOf(
            note("n1 alpha", tags = "x", notebook = "Work", pinned = true),
            note("j1 alpha", type = NoteType.JOURNAL),
            note("t1 alpha", type = NoteType.TASK, tags = "x"),
            note("n2 alpha", archived = true)
        )
        assertEquals(listOf("j1 alpha"), titles(NoteSearch.search(all, "alpha", SearchFilters(types = setOf(NoteType.JOURNAL)))))
        assertEquals(setOf("n1 alpha", "t1 alpha"), titles(NoteSearch.search(all, "alpha", SearchFilters(tag = "X"))).toSet())
        assertEquals(listOf("n1 alpha"), titles(NoteSearch.search(all, "alpha", SearchFilters(notebook = "work"))))
        assertFalse("n2 alpha" in titles(NoteSearch.search(all, "alpha", SearchFilters(includeArchived = false))))
        assertEquals(listOf("n1 alpha"), titles(NoteSearch.search(all, "alpha", SearchFilters(onlyPinned = true))))
    }

    @Test fun `an empty query with filters lists the matching notes newest first`() {
        val all = listOf(note("old", updated = 1), note("new", updated = 2), note("task", type = NoteType.TASK, updated = 3))
        assertEquals(listOf("new", "old"), titles(NoteSearch.search(all, "  ", SearchFilters(types = setOf(NoteType.NOTE)))))
    }

    @Test fun `locked notes expose their title only`() {
        val locked = note("Diary", "secret plans").copy(isLocked = true)
        assertTrue(NoteSearch.search(listOf(locked), "secret").isEmpty())
        val hit = NoteSearch.search(listOf(locked), "diary").single()
        assertEquals("", hit.snippet)
        assertEquals("", NoteSearch.search(listOf(locked), "").single().snippet)
    }

    @Test fun `no match returns nothing`() {
        assertTrue(NoteSearch.search(listOf(note("a", "b")), "zzz").isEmpty())
    }
}
