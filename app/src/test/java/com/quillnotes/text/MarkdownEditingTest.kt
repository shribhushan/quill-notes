package com.quillnotes.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditingTest {

    private fun st(text: String, a: Int, b: Int = a) = TextState(text, a, b)

    // ── wrap ──

    @Test fun `bold wraps the selection and keeps it selected`() {
        val r = MarkdownEditing.toggleWrap(st("make this bold", 5, 9), "**")
        assertEquals("make **this** bold", r.text)
        assertEquals(7 to 11, r.selStart to r.selEnd)
    }

    @Test fun `toggling again unwraps`() {
        val r = MarkdownEditing.toggleWrap(st("make **this** bold", 7, 11), "**")
        assertEquals("make this bold", r.text)
        assertEquals(5 to 9, r.selStart to r.selEnd)
    }

    @Test fun `selecting the markers too also unwraps`() {
        val r = MarkdownEditing.toggleWrap(st("a **b** c", 2, 7), "**")
        assertEquals("a b c", r.text)
    }

    @Test fun `with no selection the word under the caret is wrapped`() {
        val r = MarkdownEditing.toggleWrap(st("hello world", 8), "_")
        assertEquals("hello _world_", r.text)
    }

    @Test fun `on whitespace an empty pair is inserted with the caret inside`() {
        val r = MarkdownEditing.toggleWrap(st("a  b", 2), "**")
        assertEquals("a ****", r.text.substring(0, 6))
        assertEquals(4, r.selStart)
    }

    @Test fun `italic does not unwrap bold`() {
        val r = MarkdownEditing.toggleWrap(st("a **b** c", 4, 5), "_")
        assertEquals("a **_b_** c", r.text)
    }

    @Test fun `devanagari words count as words`() {
        val r = MarkdownEditing.toggleWrap(st("नमस्ते दुनिया", 3), "**")
        assertEquals("**नमस्ते** दुनिया", r.text)
    }

    @Test fun `link uses the selection as its label`() {
        val r = MarkdownEditing.insertLink(st("see docs here", 4, 8), null, "https://x.org")
        assertEquals("see [docs](https://x.org) here", r.text)
        assertEquals(r.text.indexOf(" here"), r.selStart)
    }

    @Test fun `link with no selection and no label uses the address`() {
        assertEquals("[https://x.org](https://x.org)", MarkdownEditing.insertLink(st("", 0), null, "https://x.org").text)
        assertEquals("[Docs](https://x.org)", MarkdownEditing.insertLink(st("", 0), "Docs", "https://x.org").text)
    }

    // ── blocks ──

    @Test fun `bullets toggle on and off for several lines`() {
        val on = MarkdownEditing.toggleBullet(st("a\nb\nc", 0, 3))
        assertEquals("- a\n- b\nc", on.text)
        val off = MarkdownEditing.toggleBullet(st("- a\n- b", 0, 7))
        assertEquals("a\nb", off.text)
    }

    @Test fun `numbering is sequential across the selection`() {
        assertEquals("1. a\n2. b\n3. c", MarkdownEditing.toggleNumbered(st("a\nb\nc", 0, 5)).text)
        assertEquals("a\nb", MarkdownEditing.toggleNumbered(st("1. a\n2. b", 0, 8)).text)
    }

    @Test fun `converting between list kinds replaces the marker`() {
        assertEquals("- [ ] a", MarkdownEditing.toggleChecklist(st("- a", 3)).text)
        assertEquals("1. a", MarkdownEditing.toggleNumbered(st("- [x] a", 7)).text)
        assertEquals("- a", MarkdownEditing.toggleBullet(st("# a", 3)).text)
    }

    @Test fun `checklist toggle keeps ticks on lines that were already items`() {
        val r = MarkdownEditing.toggleChecklist(st("- [x] done\nnew", 0, 14))
        assertEquals("- [x] done\n- [ ] new", r.text)
    }

    @Test fun `checklist on a blank line makes an empty item`() {
        val r = MarkdownEditing.toggleChecklist(st("a\n\nb", 2))
        assertEquals("a\n- [ ] \nb", r.text)
        assertEquals(8, r.selStart)
    }

    @Test fun `heading cycles up to three then clears`() {
        var s = st("title", 2)
        s = MarkdownEditing.cycleHeading(s); assertEquals("# title", s.text)
        s = MarkdownEditing.cycleHeading(s); assertEquals("## title", s.text)
        s = MarkdownEditing.cycleHeading(s); assertEquals("### title", s.text)
        s = MarkdownEditing.cycleHeading(s); assertEquals("title", s.text)
    }

    @Test fun `the caret keeps its place in the text when a marker is added or removed`() {
        val r = MarkdownEditing.toggleBullet(st("hello", 3))
        assertEquals("- hello", r.text)
        assertEquals(5, r.selStart)
        val back = MarkdownEditing.toggleBullet(r)
        assertEquals("hello", back.text)
        assertEquals(3, back.selStart)
    }

    @Test fun `indent and outdent move list items by two spaces`() {
        val r = MarkdownEditing.changeIndent(st("- [ ] a", 7), 1)
        assertEquals("  - [ ] a", r.text)
        assertEquals(9, r.selStart)
        assertEquals("- [ ] a", MarkdownEditing.changeIndent(r, -1).text)
        assertEquals("- [ ] a", MarkdownEditing.changeIndent(st("- [ ] a", 7), -1).text)
        assertEquals("plain", MarkdownEditing.changeIndent(st("plain", 2), 1).text)
    }

    // ── typing ──

    private fun enter(text: String, caret: Int): TextState {
        val old = st(text, caret)
        val new = st(text.substring(0, caret) + "\n" + text.substring(caret), caret + 1)
        return MarkdownEditing.afterEdit(old, new)
    }

    private fun backspace(text: String, caret: Int): TextState {
        val old = st(text, caret)
        val new = st(text.removeRange(caret - 1, caret), caret - 1)
        return MarkdownEditing.afterEdit(old, new)
    }

    @Test fun `enter on a checklist item starts an unticked item`() {
        val r = enter("- [x] milk", 10)
        assertEquals("- [x] milk\n- [ ] ", r.text)
        assertEquals(r.text.length, r.selStart)
    }

    @Test fun `enter in the middle of an item splits it`() {
        val r = enter("- [ ] buy milk", 9)
        assertEquals("- [ ] buy\n- [ ]  milk", r.text)
        assertEquals(16, r.selStart)
    }

    @Test fun `enter on a bullet continues it and keeps the nesting`() {
        assertEquals("- a\n- ", enter("- a", 3).text)
        assertEquals("  * a\n  - ", enter("  * a", 5).text)
    }

    @Test fun `enter on a numbered item increments and renumbers what follows`() {
        val r = enter("1. a\n2. b\n3. c", 4)
        assertEquals("1. a\n2. \n3. b\n4. c", r.text)
        assertEquals(8, r.selStart)
    }

    @Test fun `enter on an empty item ends the list`() {
        val r = enter("- [ ] a\n- [ ] ", 14)
        assertEquals("- [ ] a\n", r.text)
        assertEquals(8, r.selStart)
    }

    @Test fun `enter on an empty nested item outdents first`() {
        val r = enter("- a\n  - ", 8)
        assertEquals("- a\n- ", r.text)
        assertEquals(6, r.selStart)
    }

    @Test fun `enter on plain and heading lines is a normal newline`() {
        assertEquals("hello\n", enter("hello", 5).text)
        assertEquals("# Title\n", enter("# Title", 7).text)
    }

    @Test fun `enter continues quotes`() {
        assertEquals("> a\n> ", enter("> a", 3).text)
        assertEquals("> a\n", enter("> a\n> ", 6).text)
    }

    @Test fun `backspace at the start of an item removes the whole marker`() {
        val r = backspace("- [ ] hello", 6)
        assertEquals("hello", r.text)
        assertEquals(0, r.selStart)
        assertEquals("  hello", backspace("  - [x] hello", 8).text)
        assertEquals("text", backspace("1. text", 3).text)
        assertEquals("Title", backspace("## Title", 3).text)
    }

    @Test fun `backspace elsewhere is left alone`() {
        assertEquals("- [ ] hell", backspace("- [ ] hello", 11).text)
        assertEquals("plan", backspace("plann", 5).text)
    }

    @Test fun `other edits pass through untouched`() {
        val old = st("abc", 1)
        val typed = st("aXbc", 2)
        assertEquals(typed, MarkdownEditing.afterEdit(old, typed))
        val selecting = st("abc", 0, 3)
        assertEquals(st("", 0), MarkdownEditing.afterEdit(selecting, st("", 0)))
    }
}
