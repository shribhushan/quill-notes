package com.quillnotes.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewTest {

    @Test fun `markers are stripped and kinds kept`() {
        val p = MarkdownPreview.build("# Plan\nsome **bold** text\n- [ ] call mum\n- [x] done\n- point\n2. two", 10)
        assertEquals(
            listOf(LineKind.HEADING, LineKind.PLAIN, LineKind.CHECK, LineKind.CHECK, LineKind.BULLET, LineKind.NUMBERED),
            p.lines.map { it.kind }
        )
        assertEquals(listOf("Plan", "some bold text", "call mum", "done", "point", "two"), p.lines.map { it.text })
        assertFalse(p.truncated)
    }

    @Test fun `checklist lines remember their place in the note so a tap can tick them`() {
        val content = "Intro\n\n- [ ] a\n- [x] b"
        val checks = MarkdownPreview.build(content, 10).lines.filter { it.kind == LineKind.CHECK }
        assertEquals(listOf(2, 3), checks.map { it.lineIndex })
        assertEquals(listOf(false, true), checks.map { it.checked })
        // The index really addresses that line.
        assertEquals("- [ ] a\n- [x] b".split("\n")[0], content.split("\n")[checks[0].lineIndex])
    }

    @Test fun `blank lines are skipped and long notes are cut with a flag`() {
        val p = MarkdownPreview.build("a\n\n\nb\n\nc\nd", 3)
        assertEquals(listOf("a", "b", "c"), p.lines.map { it.text })
        assertTrue(p.truncated)
    }

    @Test fun `an empty checklist item still shows its box`() {
        val p = MarkdownPreview.build("- [ ] ", 5)
        assertEquals(1, p.lines.size)
        assertEquals(LineKind.CHECK, p.lines[0].kind)
    }

    @Test fun `nesting is kept for sub items`() {
        val p = MarkdownPreview.build("- [ ] a\n  - [ ] a1", 5)
        assertEquals(listOf(0, 2), p.lines.map { it.indent })
    }

    @Test fun `plain notes come through unchanged`() {
        val p = MarkdownPreview.build("Just a sentence.\nAnother line.", 6)
        assertEquals(listOf("Just a sentence.", "Another line."), p.lines.map { it.text })
        assertTrue(p.lines.all { it.kind == LineKind.PLAIN })
    }
}
