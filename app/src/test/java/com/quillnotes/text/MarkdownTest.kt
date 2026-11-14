package com.quillnotes.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test fun `classifies every line kind`() {
        val text = "# Title\n- [ ] todo\n- [x] done\n- [X] shouted\n- bullet\n* star\n3. third\n> quote\nplain\n\n  - [ ] nested"
        val kinds = Markdown.lines(text).map { it.kind }
        assertEquals(
            listOf(
                LineKind.HEADING, LineKind.CHECK, LineKind.CHECK, LineKind.CHECK, LineKind.BULLET, LineKind.BULLET,
                LineKind.NUMBERED, LineKind.QUOTE, LineKind.PLAIN, LineKind.PLAIN, LineKind.CHECK
            ),
            kinds
        )
    }

    @Test fun `line offsets and marker lengths`() {
        val text = "## Heading\n  - [x] nested done"
        val (h, c) = Markdown.lines(text)
        assertEquals(2, h.level)
        assertEquals(3, h.markerLength)
        assertEquals(0, h.start); assertEquals(10, h.end)
        assertEquals(2, c.indent)
        assertEquals(6, c.markerLength)
        assertTrue(c.checked)
        assertEquals(11 + 2 + 6, c.contentStart)
        assertEquals("nested done", text.substring(c.contentStart, c.end))
    }

    @Test fun `an empty text is one plain line and a trailing newline adds one`() {
        assertEquals(1, Markdown.lines("").size)
        assertEquals(2, Markdown.lines("a\n").size)
        assertEquals(LineKind.PLAIN, Markdown.lines("a\n").last().kind)
    }

    @Test fun `bracket fragments are not checklist items`() {
        assertEquals(LineKind.BULLET, Markdown.lines("- [ ]no space").single().kind)
        assertEquals(LineKind.PLAIN, Markdown.lines("[ ] bare").single().kind)
        assertEquals(LineKind.PLAIN, Markdown.lines("#nospace").single().kind)
        assertEquals(LineKind.PLAIN, Markdown.lines("#######seven").single().kind)
    }

    @Test fun `lineAt picks the line at its end and at the next start`() {
        val lines = Markdown.lines("ab\ncd")
        assertEquals(0, Markdown.lineAt(lines, 2).index)
        assertEquals(1, Markdown.lineAt(lines, 3).index)
        assertEquals(1, Markdown.lineAt(lines, 99).index)
    }

    // ── inline ──

    private fun types(s: String) = Markdown.inlineSpans(s).map { it.type }

    @Test fun `finds bold italic strike code and links`() {
        assertEquals(listOf(SpanType.BOLD), types("a **bold** b"))
        assertEquals(listOf(SpanType.ITALIC), types("a _it_ b"))
        assertEquals(listOf(SpanType.ITALIC), types("a *it* b"))
        assertEquals(listOf(SpanType.STRIKE), types("a ~~gone~~ b"))
        assertEquals(listOf(SpanType.CODE), types("run `ls -l` now"))
        assertEquals(listOf(SpanType.LINK), types("see [docs](https://x.org/a)"))
        assertEquals(listOf(SpanType.WIKILINK), types("see [[Other note]]"))
        assertEquals(listOf(SpanType.URL), types("go to https://example.com/page, ok"))
    }

    @Test fun `span ranges cover markers and content separately`() {
        val s = Markdown.inlineSpans("x **bold** y").single()
        assertEquals(2, s.start); assertEquals(10, s.end)
        assertEquals(4, s.contentStart); assertEquals(8, s.contentEnd)
        val link = Markdown.inlineSpans("[go](http://a.b)").single()
        assertEquals("http://a.b", link.target)
        assertEquals(1, link.contentStart); assertEquals(3, link.contentEnd)
    }

    @Test fun `offsets are shifted by the line start`() {
        val s = Markdown.inlineSpans("**b**", offset = 10).single()
        assertEquals(10, s.start); assertEquals(15, s.end); assertEquals(12, s.contentStart)
    }

    @Test fun `arithmetic and snake_case are not formatting`() {
        assertTrue(Markdown.inlineSpans("2 * 3 * 4").isEmpty())
        assertTrue(Markdown.inlineSpans("some_snake_case_name").isEmpty())
        assertTrue(Markdown.inlineSpans("**unterminated").isEmpty())
    }

    @Test fun `code protects its contents and URLs inside links are not double counted`() {
        assertEquals(listOf(SpanType.CODE), types("`**not bold**`"))
        assertEquals(listOf(SpanType.LINK), types("[https://a.b](https://a.b)"))
    }

    @Test fun `bold and italic can nest`() {
        assertEquals(setOf(SpanType.BOLD, SpanType.ITALIC), types("**a _b_ c**").toSet())
    }

    @Test fun `spans over a document skip the block markers`() {
        val text = "- [ ] buy **milk**\n## a _b_"
        val spans = Markdown.spans(text)
        assertEquals(2, spans.size)
        assertEquals("milk", text.substring(spans[0].contentStart, spans[0].contentEnd))
        assertEquals("b", text.substring(spans[1].contentStart, spans[1].contentEnd))
    }

    // ── flatten ──

    @Test fun `plain preview removes markers and shows ticks and bullets`() {
        val md = "# Plan\n- [ ] **call** mum\n- [x] done\n- point\n2. second\nsee [docs](https://x.org)"
        assertEquals("Plan\n☐ call mum\n☑ done\n• point\n2. second\nsee docs", Markdown.toPlain(md))
    }

    @Test fun `sharing keeps link addresses`() {
        assertEquals("see docs (https://x.org)", Markdown.toPlain("see [docs](https://x.org)", Markdown.PlainStyle.SHARE))
    }

    @Test fun `nested emphasis flattens and plain text is untouched`() {
        assertEquals("a b c", Markdown.stripInline("**a _b_ c**"))
        assertEquals("just words, 2 * 3", Markdown.stripInline("just words, 2 * 3"))
        assertEquals("keep", Markdown.stripInline("`keep`"))
    }

    @Test fun `isRich only flags real markdown`() {
        assertFalse(Markdown.isRich("Just a sentence. And another.\n\nWith a paragraph."))
        assertFalse(Markdown.isRich("see https://example.com"))
        assertTrue(Markdown.isRich("- [ ] a"))
        assertTrue(Markdown.isRich("some **bold** text"))
        assertTrue(Markdown.isRich("# Heading"))
        assertFalse(Markdown.isRich(""))
    }

    @Test fun `wiki links are collected once each`() {
        assertEquals(listOf("Alpha", "Beta"), Markdown.wikiLinks("[[Alpha]] and [[ Beta ]] and [[ALPHA]] [[]]"))
    }

    @Test fun `devanagari text passes through untouched`() {
        val s = "- [ ] दूध खरीदें **ज़रूरी**"
        assertEquals("☐ दूध खरीदें ज़रूरी", Markdown.toPlain(s))
    }
}
