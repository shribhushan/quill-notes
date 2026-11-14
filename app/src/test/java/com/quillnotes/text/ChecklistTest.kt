package com.quillnotes.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTest {

    @Test fun `reads items and progress`() {
        val c = "Shopping\n- [ ] milk\n- [x] eggs\n  - [x] free range\n- [ ] bread"
        assertEquals(listOf("milk", "eggs", "free range", "bread"), Checklist.items(c).map { it.text })
        assertEquals(listOf(1, 2, 3, 4), Checklist.items(c).map { it.lineIndex })
        assertEquals(2 to 4, Checklist.progress(c))
        assertNull(Checklist.progress("no list here"))
    }

    @Test fun `ticking sinks the item to the bottom of its list`() {
        val c = "- [ ] a\n- [ ] b\n- [ ] c"
        assertEquals("- [ ] b\n- [ ] c\n- [x] a", Checklist.toggle(c, 0))
        assertEquals("- [ ] a\n- [ ] c\n- [x] b", Checklist.toggle(c, 1))
        assertEquals("- [ ] a\n- [ ] b\n- [x] c", Checklist.toggle(c, 2))
    }

    @Test fun `a newly ticked item goes below items that were already ticked`() {
        assertEquals("- [x] a\n- [x] c\n- [x] b", Checklist.toggle("- [x] a\n- [ ] b\n- [x] c", 1))
    }

    @Test fun `unticking rises above the first ticked item`() {
        assertEquals("- [ ] a\n- [ ] c\n- [x] b", Checklist.toggle("- [ ] a\n- [x] b\n- [x] c", 2))
        assertEquals("- [ ] b\n- [x] a\n- [x] c", Checklist.toggle("- [x] a\n- [x] b\n- [x] c", 1))
    }

    @Test fun `children move with their parent`() {
        val c = "- [ ] a\n  - [ ] a1\n- [ ] b"
        assertEquals("- [ ] b\n- [x] a\n  - [ ] a1", Checklist.toggle(c, 0))
    }

    @Test fun `sub items sort among their own siblings`() {
        val c = "- [ ] a\n  - [ ] a1\n  - [ ] a2\n- [ ] b"
        assertEquals("- [ ] a\n  - [ ] a2\n  - [x] a1\n- [ ] b", Checklist.toggle(c, 1))
    }

    @Test fun `separate lists do not mix`() {
        val c = "- [ ] a\n- [ ] b\n\nnotes\n\n- [ ] c\n- [ ] d"
        assertEquals("- [ ] b\n- [x] a\n\nnotes\n\n- [ ] c\n- [ ] d", Checklist.toggle(c, 0))
        assertEquals("- [ ] a\n- [ ] b\n\nnotes\n\n- [ ] d\n- [x] c", Checklist.toggle(c, 5))
    }

    @Test fun `a single item just flips and non items are ignored`() {
        assertEquals("- [x] only", Checklist.toggle("- [ ] only", 0))
        assertEquals("- [ ] only", Checklist.toggle("- [x] only", 0))
        assertEquals("plain\n- [ ] a", Checklist.toggle("plain\n- [ ] a", 0))
        assertEquals("- [ ] a", Checklist.toggle("- [ ] a", 7))
    }

    @Test fun `text around the list is preserved exactly`() {
        val c = "Title\n\n- [ ] a\n- [ ] b\n\nTail\n"
        assertEquals("Title\n\n- [ ] b\n- [x] a\n\nTail\n", Checklist.toggle(c, 2))
    }

    @Test fun `ticking twice returns an equivalent list`() {
        val c = "- [ ] a\n- [ ] b\n- [ ] c"
        val ticked = Checklist.toggle(c, 0)               // b, c, a✓
        val back = Checklist.toggle(ticked, 2)            // a is last (index 2) -> unticks, no other ticked: stays
        assertEquals(setOf("a", "b", "c"), Checklist.items(back).map { it.text }.toSet())
        assertEquals(0, Checklist.items(back).count { it.checked })
    }

    @Test fun `append goes after the last top level item and its children`() {
        assertEquals("- [ ] new", Checklist.append("", "  new "))
        assertEquals("note\n- [ ] new", Checklist.append("note", "new"))
        assertEquals("- [ ] a\n  - [ ] a1\n- [ ] new\n\ntail", Checklist.append("- [ ] a\n  - [ ] a1\n\ntail", "new"))
    }
}
