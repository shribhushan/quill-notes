package com.quillnotes.data.export

import com.quillnotes.data.sync.AttachmentRef
import com.quillnotes.data.sync.NotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class NoteExporterTest {
    private fun note(
        title: String = "Chapter 12", content: String = "She finds the reel.", type: String = "NOTE",
        tags: String = "writing, draft", notebook: String? = null, uuid: String = "u1", extra: NotePayload.() -> NotePayload = { this }
    ) = NotePayload(
        uuid = uuid, title = title, content = content, type = type, createdAt = 1_700_000_000_000, updatedAt = 1_700_000_100_000,
        tags = tags, notebook = notebook
    ).extra()

    @Test fun `markdown has a readable header and the text`() {
        val md = NoteExporter.markdown(note(extra = { copy(isPinned = true, color = "yellow") }))
        assertTrue(md.startsWith("---\n"))
        assertTrue("title: Chapter 12" in md)
        assertTrue("type: note" in md)
        assertTrue("created: 2023-11-14T22:13:20Z" in md)
        assertTrue("tags: [writing, draft]" in md)
        assertTrue("pinned: true" in md)
        assertTrue("color: yellow" in md)
        assertTrue(md.contains("---\n\n# Chapter 12\n\nShe finds the reel.\n"))
    }

    @Test fun `tasks and journal entries carry their own fields`() {
        val task = NoteExporter.markdown(note(type = "TASK", extra = { copy(dueDate = 1_700_000_000_000, repeatRule = "2W", priority = 3, isCompleted = true) }))
        assertTrue("type: task" in task)
        assertTrue("due: 2023-11-14T22:13:20Z" in task)
        assertTrue("repeat: every 2 weeks" in task)
        assertTrue("priority: high" in task)
        assertTrue("completed: true" in task)
        val journal = NoteExporter.markdown(note(type = "JOURNAL", extra = { copy(mood = "calm") }))
        assertTrue("mood: calm" in journal)
        assertFalse("completed:" in journal)
    }

    @Test fun `awkward values are quoted so the header stays valid`() {
        val md = NoteExporter.markdown(note(title = "Plan: \"Q3\" #1", tags = "a:b, yes"))
        assertTrue("title: \"Plan: \\\"Q3\\\" #1\"" in md)
        assertTrue("tags: [\"a:b\", \"yes\"]" in md)
    }

    @Test fun `a heading already in the text is not repeated and untitled notes get none`() {
        val md = NoteExporter.markdown(note(content = "# Chapter 12\nbody"))
        assertEquals(1, Regex("# Chapter 12").findAll(md).count())
        assertFalse("\n# " in NoteExporter.markdown(note(title = "", content = "just text")))
    }

    @Test fun `attachments are linked at the end`() {
        val md = NoteExporter.markdown(note(), listOf("../attachments/a.jpg"))
        assertTrue(md.trimEnd().endsWith("![](../attachments/a.jpg)"))
    }

    @Test fun `plain text flattens markdown and keeps link addresses`() {
        val t = NoteExporter.plainText(note(content = "- [ ] call **mum**\nsee [site](https://x.org)"))
        assertEquals("Chapter 12\n\n☐ call mum\nsee site (https://x.org)", t)
        assertEquals("only body", NoteExporter.plainText(note(title = "", content = "only body")))
    }

    @Test fun `file names are safe and unique`() {
        val taken = HashSet<String>()
        assertEquals("Q3 plan.md", NoteExporter.fileName(note(title = "Q3/plan?"), taken))
        assertEquals("q3 PLAN (2).md", NoteExporter.fileName(note(title = "q3 PLAN"), taken)) // clash is case-insensitive
        assertEquals("Q3 plan (3).md", NoteExporter.fileName(note(title = "Q3 plan"), taken))
        assertEquals("_CON.md", NoteExporter.fileName(note(title = "CON"), HashSet()))
        assertEquals("Untitled.md", NoteExporter.fileName(note(title = "", content = ""), HashSet()))
        assertEquals("first line of body.md", NoteExporter.fileName(note(title = "", content = "\n- [ ] first line of body"), HashSet()))
        assertTrue(NoteExporter.fileName(note(title = "x".repeat(300)), HashSet()).length <= 83)
        assertEquals("दुनिया.md", NoteExporter.fileName(note(title = "दुनिया"), HashSet()))
    }

    @Test fun `folders follow notebook and kind`() {
        assertEquals("Notes", NoteExporter.folderFor(note()))
        assertEquals("Work", NoteExporter.folderFor(note(notebook = "Work")))
        assertEquals("Journal", NoteExporter.folderFor(note(type = "JOURNAL")))
        assertEquals("Tasks", NoteExporter.folderFor(note(type = "TASK")))
        assertEquals("Templates", NoteExporter.folderFor(note(extra = { copy(isTemplate = true) })))
        assertEquals("a b", NoteExporter.folderFor(note(notebook = "a/b")))
    }

    @Test fun `zip holds one markdown file per live note plus the images`() {
        val withImage = note(title = "Trip", uuid = "n1", extra = { copy(attachments = listOf(AttachmentRef("img1", "image/png"), AttachmentRef("missing"))) })
        val notes = listOf(
            withImage, note(title = "Trip", uuid = "n2", notebook = "Travel"),
            note(title = "Trashed", uuid = "n3", extra = { copy(isTrashed = true) }), note(title = "Ghost", uuid = "n4", extra = { copy(isPurged = true) }),
            note(title = "Trip", uuid = "n5")
        )
        val out = ByteArrayOutputStream()
        val summary = NoteExporter.writeZip(out, notes) { if (it == "img1") byteArrayOf(1, 2, 3) else null }
        assertEquals(NoteExporter.ZipSummary(notes = 3, images = 1), summary)

        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) { val e = z.nextEntry ?: break; entries[e.name] = z.readBytes() }
        }
        assertEquals(setOf("attachments/img1.png", "Notes/Trip.md", "Travel/Trip.md", "Notes/Trip (2).md"), entries.keys)
        assertEquals(listOf<Byte>(1, 2, 3), entries.getValue("attachments/img1.png").toList())
        val md = String(entries.getValue("Notes/Trip.md"))
        assertTrue("![](../attachments/img1.png)" in md)
        assertFalse("missing" in md)
    }
}
