package com.quillnotes.text

/** Text plus selection, independent of Compose so the editing rules can be unit-tested. */
data class TextState(val text: String, val selStart: Int, val selEnd: Int = selStart) {
    val start: Int get() = minOf(selStart, selEnd)
    val end: Int get() = maxOf(selStart, selEnd)
    val hasSelection: Boolean get() = selStart != selEnd
}

/** The formatting toolbar's actions and the editor's list auto-continuation, as pure functions. */
object MarkdownEditing {

    // ── Inline: bold / italic / strike / code ──────────────────

    /** Wraps the selection (or the word at the caret) in [marker], or removes it if already wrapped. */
    fun toggleWrap(s: TextState, marker: String): TextState {
        val t = s.text
        val m = marker.length
        var a = s.start
        var b = s.end
        if (a == b) {
            val (ws, we) = wordBounds(t, a)
            if (ws == we) {
                val nt = t.substring(0, a) + marker + marker + t.substring(a)
                return TextState(nt, a + m)
            }
            a = ws
            b = we
        }
        // Markers just outside the selection.
        if (a - m >= 0 && b + m <= t.length && t.startsWith(marker, a - m) && t.startsWith(marker, b)) {
            val nt = t.substring(0, a - m) + t.substring(a, b) + t.substring(b + m)
            return TextState(nt, a - m, b - m)
        }
        // Markers inside the selection.
        if (b - a >= 2 * m && t.startsWith(marker, a) && t.startsWith(marker, b - m)) {
            val nt = t.substring(0, a) + t.substring(a + m, b - m) + t.substring(b)
            return TextState(nt, a, b - 2 * m)
        }
        val nt = t.substring(0, a) + marker + t.substring(a, b) + marker + t.substring(b)
        return TextState(nt, a + m, b + m)
    }

    /** Replaces [start, end) with [replacement] and puts the caret after it. */
    fun replaceRange(s: TextState, start: Int, end: Int, replacement: String): TextState {
        val a = start.coerceIn(0, s.text.length)
        val b = end.coerceIn(a, s.text.length)
        return TextState(s.text.substring(0, a) + replacement + s.text.substring(b), a + replacement.length)
    }

    /** `[label](url)`; with a selection the selection is the label unless [label] is given. */
    fun insertLink(s: TextState, label: String?, url: String): TextState {
        val text = label?.takeIf { it.isNotEmpty() } ?: s.text.substring(s.start, s.end).ifEmpty { url }
        return replaceRange(s, s.start, s.end, "[$text]($url)")
    }

    // ── Block: headings, lists ─────────────────────────────────

    /** none → # → ## → ### → none, applied to every selected line. */
    fun cycleHeading(s: TextState): TextState {
        val first = Markdown.lineAt(Markdown.lines(s.text), s.start)
        val next = if (first.kind == LineKind.HEADING) (first.level + 1).let { if (it > 3) 0 else it } else 1
        return editLines(s) { line, _ ->
            if (next == 0) "" else "#".repeat(next) + " "
        }
    }

    fun toggleBullet(s: TextState): TextState {
        val selected = selectedLines(s)
        val all = selected.all { it.kind == LineKind.BULLET }
        return editLines(s) { line, _ -> if (all) indentOf(line) else indentOf(line) + "- " }
    }

    fun toggleNumbered(s: TextState): TextState {
        val selected = selectedLines(s)
        val all = selected.all { it.kind == LineKind.NUMBERED }
        return editLines(s) { line, n -> if (all) indentOf(line) else indentOf(line) + "${n + 1}. " }
    }

    fun toggleChecklist(s: TextState): TextState {
        val selected = selectedLines(s)
        val all = selected.all { it.kind == LineKind.CHECK }
        return editLines(s) { line, _ ->
            when {
                all -> indentOf(line)
                line.kind == LineKind.CHECK -> indentOf(line) + (if (line.checked) "- [x] " else "- [ ] ")
                else -> indentOf(line) + "- [ ] "
            }
        }
    }

    /** Indent (+1) or outdent (−1) the selected list items by two spaces. */
    fun changeIndent(s: TextState, direction: Int): TextState {
        val selected = selectedLines(s)
        if (selected.none { it.isList }) return s
        return editLines(s, keepNonList = true) { line, _ ->
            val current = line.indent
            val target = (current + 2 * direction).coerceAtLeast(0)
            " ".repeat(target) + markerText(s.text, line)
        }
    }

    // ── Typing behaviour ───────────────────────────────────────

    /**
     * Applied after the field reports a change: Enter on a list item continues the list (an empty
     * item ends it), and Backspace at the start of an item's text removes its marker whole rather
     * than leaving `- [ ]` fragments behind. Anything else passes through unchanged.
     */
    fun afterEdit(old: TextState, new: TextState): TextState {
        if (old.hasSelection || new.hasSelection) return new

        val insertedNewline = new.text.length == old.text.length + 1 &&
            new.selStart == old.selStart + 1 &&
            new.text[old.selStart] == '\n' &&
            new.text.substring(0, old.selStart) == old.text.substring(0, old.selStart) &&
            new.text.substring(new.selStart) == old.text.substring(old.selStart)
        if (insertedNewline) return continueList(old, new)

        val deletedOne = new.text.length == old.text.length - 1 && new.selStart == old.selStart - 1 &&
            new.text.substring(0, new.selStart) == old.text.substring(0, new.selStart) &&
            new.text.substring(new.selStart) == old.text.substring(old.selStart)
        if (deletedOne) {
            val line = Markdown.lineAt(Markdown.lines(old.text), old.selStart)
            val atContentStart = old.selStart == line.contentStart && line.markerLength > 0
            if (atContentStart && line.kind != LineKind.PLAIN) {
                // Keep the nesting, drop the marker.
                val t = old.text.substring(0, line.markerStart) + old.text.substring(line.contentStart)
                return TextState(t, line.markerStart)
            }
        }
        return new
    }

    private fun continueList(old: TextState, new: TextState): TextState {
        val oldLines = Markdown.lines(old.text)
        val line = Markdown.lineAt(oldLines, old.selStart)
        val p = old.selStart
        val content = old.text.substring(line.contentStart.coerceAtMost(line.end), line.end)

        if (line.isList) {
            val emptyItem = content.isBlank() && p >= line.contentStart
            if (emptyItem) {
                // Enter on an empty item: outdent a nested one, otherwise end the list.
                return if (line.indent >= 2) {
                    val t = old.text.substring(0, line.start) + " ".repeat(line.indent - 2) +
                        old.text.substring(line.markerStart)
                    TextState(t, p - 2)
                } else {
                    val t = old.text.substring(0, line.start) + old.text.substring(line.contentStart)
                    TextState(t, line.start)
                }
            }
            if (p >= line.contentStart) {
                val marker = nextMarker(line)
                var text = new.text.substring(0, p + 1) + marker + new.text.substring(p + 1)
                if (line.kind == LineKind.NUMBERED) text = renumberFrom(text, Markdown.lines(text).first { it.start == p + 1 }.index)
                return TextState(text, p + 1 + marker.length)
            }
        }
        if (line.kind == LineKind.QUOTE && p >= line.contentStart) {
            if (content.isBlank()) {
                val t = old.text.substring(0, line.start) + old.text.substring(line.contentStart)
                return TextState(t, line.start)
            }
            val marker = "> "
            return TextState(new.text.substring(0, p + 1) + marker + new.text.substring(p + 1), p + 1 + marker.length)
        }
        return new
    }

    private fun nextMarker(line: MdLine): String = " ".repeat(line.indent) + when (line.kind) {
        LineKind.CHECK -> "- [ ] "
        LineKind.NUMBERED -> "${line.number + 1}. "
        else -> "- "
    }

    /** Renumbers the run of numbered items (same indent) that starts at [lineIndex]. */
    private fun renumberFrom(text: String, lineIndex: Int): String {
        val lines = Markdown.lines(text)
        val raw = text.split('\n').toMutableList()
        val first = lines[lineIndex]
        var n = first.number
        var i = lineIndex
        while (i < lines.size && lines[i].kind == LineKind.NUMBERED && lines[i].indent == first.indent) {
            val l = lines[i]
            val delim = raw[i][l.markerStart - l.start + l.markerLength - 2]
            raw[i] = raw[i].substring(0, l.indent) + "$n$delim " + raw[i].substring(l.indent + l.markerLength)
            n++
            i++
        }
        return raw.joinToString("\n")
    }

    // ── Helpers ────────────────────────────────────────────────

    private fun markerText(text: String, line: MdLine): String = text.substring(line.markerStart, line.contentStart)

    private fun indentOf(line: MdLine): String = " ".repeat(line.indent)

    private fun selectedLines(s: TextState): List<MdLine> {
        val lines = Markdown.lines(s.text)
        val first = Markdown.lineAt(lines, s.start)
        var last = Markdown.lineAt(lines, s.end)
        // A selection that ends right at the start of the next line does not include that line.
        if (s.hasSelection && last.index > first.index && s.end == last.start) last = lines[last.index - 1]
        return lines.subList(first.index, last.index + 1)
    }

    /**
     * Rewrites the marker of every selected line. [newPrefix] receives the line and its position
     * among the selected lines and returns the full new text that precedes the content (indent +
     * marker). The selection is mapped through the change.
     */
    private fun editLines(
        s: TextState,
        keepNonList: Boolean = false,
        newPrefix: (line: MdLine, ordinal: Int) -> String
    ): TextState {
        val lines = Markdown.lines(s.text)
        val selected = selectedLines(s)
        val firstIdx = selected.first().index
        val lastIdx = selected.last().index

        data class Edit(val line: MdLine, val oldPrefixEnd: Int, val newStart: Int, val newPrefixLen: Int)
        val sb = StringBuilder()
        val edits = ArrayList<Edit>()
        lines.forEachIndexed { i, line ->
            if (i > 0) sb.append('\n')
            val newStart = sb.length
            val raw = s.text.substring(line.start, line.end)
            if (i in firstIdx..lastIdx && !(keepNonList && !line.isList)) {
                val prefix = newPrefix(line, i - firstIdx)
                val content = raw.substring((line.contentStart - line.start).coerceAtMost(raw.length))
                sb.append(prefix).append(content)
                edits += Edit(line, line.contentStart, newStart, prefix.length)
            } else {
                sb.append(raw)
                edits += Edit(line, line.start, newStart, 0)
            }
        }

        fun map(offset: Int): Int {
            val line = Markdown.lineAt(lines, offset)
            val e = edits[line.index]
            return if (e.oldPrefixEnd == line.start && e.newPrefixLen == 0) {
                e.newStart + (offset - line.start)
            } else if (offset >= line.contentStart) {
                e.newStart + e.newPrefixLen + (offset - line.contentStart)
            } else {
                e.newStart + e.newPrefixLen
            }
        }
        val text = sb.toString()
        return TextState(
            text,
            map(s.selStart).coerceIn(0, text.length),
            map(s.selEnd).coerceIn(0, text.length)
        )
    }

    /** Bounds of the word around [offset] (empty range if the caret is not on a word). */
    fun wordBounds(t: String, offset: Int): Pair<Int, Int> {
        fun isWord(c: Char) = Character.isLetterOrDigit(c) || c == '_' ||
            Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }
        var a = offset.coerceIn(0, t.length)
        var b = a
        while (a > 0 && isWord(t[a - 1])) a--
        while (b < t.length && isWord(t[b])) b++
        return a to b
    }
}
