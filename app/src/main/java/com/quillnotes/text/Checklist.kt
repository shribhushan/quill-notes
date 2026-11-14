package com.quillnotes.text

data class ChecklistItem(val lineIndex: Int, val checked: Boolean, val text: String, val indent: Int)

/**
 * Checklists live in the note text as `- [ ] item` / `- [x] item` lines, so they export and sync
 * as ordinary Markdown. These helpers read and change them without any other storage.
 */
object Checklist {

    fun items(content: String): List<ChecklistItem> =
        Markdown.lines(content).filter { it.kind == LineKind.CHECK }
            .map { ChecklistItem(it.index, it.checked, content.substring(it.contentStart, it.end), it.indent) }

    /** (done, total) over every checklist line, or null if the text has none. */
    fun progress(content: String): Pair<Int, Int>? {
        val all = items(content)
        return if (all.isEmpty()) null else all.count { it.checked } to all.size
    }

    /**
     * Ticks or unticks the item on [lineIndex] and keeps the list tidy: a ticked item (with its
     * indented sub-items) sinks to the bottom of the items at its level; an unticked one rises
     * above the first ticked item. Anything that is not a checklist line is left alone.
     */
    fun toggle(content: String, lineIndex: Int): String {
        val parsed = Markdown.lines(content)
        val target = parsed.getOrNull(lineIndex) ?: return content
        if (target.kind != LineKind.CHECK) return content

        val lines = content.split('\n').toMutableList()
        val d = target.indent
        val nowChecked = !target.checked
        val line = lines[lineIndex]
        lines[lineIndex] = line.substring(0, d) + (if (nowChecked) "- [x] " else "- [ ] ") + line.substring(d + target.markerLength)

        fun isCheck(i: Int) = parsed[i].kind == LineKind.CHECK
        fun unitEnd(i: Int): Int {
            var e = i + 1
            while (e < parsed.size && isCheck(e) && parsed[e].indent > parsed[i].indent) e++
            return e
        }

        // The run of sibling items around the toggled one.
        var start = lineIndex
        while (start - 1 >= 0 && isCheck(start - 1) && parsed[start - 1].indent >= d) start--
        var end = unitEnd(lineIndex)
        while (end < parsed.size && isCheck(end) && parsed[end].indent >= d) end = unitEnd(end)

        val units = ArrayList<IntRange>()
        var u = start
        while (u < end) {
            val e = unitEnd(u)
            units += u until e
            u = e
        }
        val mine = units.indexOfFirst { it.first == lineIndex }
        if (mine < 0 || units.size < 2) return lines.joinToString("\n")

        // Whether each unit is ticked is judged from the *edited* lines.
        fun unitChecked(r: IntRange): Boolean {
            val l = lines[r.first]
            return l.trimStart().startsWith("- [x] ") || l.trimStart().startsWith("- [X] ")
        }

        val order = units.indices.toMutableList()
        val me = order.removeAt(mine)
        val insertAt = if (nowChecked) {
            order.size // after every other unit
        } else {
            val firstChecked = order.indexOfFirst { unitChecked(units[it]) }
            when {
                firstChecked < 0 -> mine.coerceAtMost(order.size)
                firstChecked > mine -> mine.coerceAtMost(order.size) // already above the ticked ones
                else -> firstChecked
            }
        }
        order.add(insertAt, me)

        val rebuilt = ArrayList<String>()
        for (i in 0 until start) rebuilt += lines[i]
        for (idx in order) for (li in units[idx]) rebuilt += lines[li]
        for (i in end until lines.size) rebuilt += lines[i]
        return rebuilt.joinToString("\n")
    }

    /** Adds `- [ ] text` after the last checklist item, or at the end of the note if there is none. */
    fun append(content: String, text: String): String {
        val item = "- [ ] ${text.trim()}"
        if (content.isBlank()) return item
        val parsed = Markdown.lines(content)
        val lastCheck = parsed.lastOrNull { it.kind == LineKind.CHECK && it.indent == 0 }
        if (lastCheck == null) return content.trimEnd('\n') + "\n" + item
        val lines = content.split('\n').toMutableList()
        // Insert after the last top-level item and its indented children.
        var at = lastCheck.index + 1
        while (at < parsed.size && parsed[at].kind == LineKind.CHECK && parsed[at].indent > 0) at++
        lines.add(at, item)
        return lines.joinToString("\n")
    }

    /** Unticks every item, e.g. when a recurring task starts its next round. */
    fun resetAll(content: String): String {
        if (!content.contains("[x]") && !content.contains("[X]")) return content
        val parsed = Markdown.lines(content)
        val lines = content.split('\n').toMutableList()
        for (l in parsed) {
            if (l.kind == LineKind.CHECK && l.checked) {
                lines[l.index] = lines[l.index].substring(0, l.indent) + "- [ ] " + lines[l.index].substring(l.indent + l.markerLength)
            }
        }
        return lines.joinToString("\n")
    }
}
