package com.quillnotes.text

/** What a line of Markdown is. */
enum class LineKind { PLAIN, HEADING, BULLET, NUMBERED, CHECK, QUOTE }

/**
 * One line of a note, classified. Offsets index into the full text so the editor can style
 * and edit in place without re-parsing into another structure; the stored note stays plain Markdown.
 */
data class MdLine(
    val index: Int,
    /** Offset of the first character of the line. */
    val start: Int,
    /** Offset just past the last character (the newline is not part of the line). */
    val end: Int,
    val kind: LineKind,
    /** Leading spaces/tabs before the marker (nesting). */
    val indent: Int = 0,
    /** Length of the marker after the indent, including its trailing space: "- [ ] " is 6. */
    val markerLength: Int = 0,
    val level: Int = 0,
    val checked: Boolean = false,
    val number: Int = 0
) {
    val markerStart: Int get() = start + indent
    val contentStart: Int get() = start + indent + markerLength
    val isList: Boolean get() = kind == LineKind.BULLET || kind == LineKind.NUMBERED || kind == LineKind.CHECK
}

enum class SpanType { BOLD, ITALIC, STRIKE, CODE, LINK, WIKILINK, URL }

/**
 * An inline format. [start]..[end] covers the markers too; [contentStart]..[contentEnd] is the
 * text they wrap, so the editor can dim the markers and style the content.
 */
data class MdSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val contentStart: Int,
    val contentEnd: Int,
    /** URL for [SpanType.LINK] / [SpanType.URL], note title for [SpanType.WIKILINK]. */
    val target: String? = null
)

object Markdown {
    private val CHECK = Regex("^([ \\t]*)- \\[([ xX])\\] ")
    private val BULLET = Regex("^([ \\t]*)([-*+]) ")
    private val NUMBER = Regex("^([ \\t]*)(\\d{1,9})([.)]) ")
    private val HEADING = Regex("^(#{1,6}) ")
    private val QUOTE = Regex("^> ?")

    private val CODE = Regex("`([^`\\n]+)`")
    private val WIKI = Regex("\\[\\[([^\\[\\]\\n]+)]]")
    private val LINK = Regex("\\[([^\\[\\]\\n]+)]\\(([^()\\s]+)\\)")
    private val BOLD = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*|__(?=\\S)(.+?)(?<=\\S)__")
    private val STRIKE = Regex("~~(?=\\S)(.+?)(?<=\\S)~~")
    private val ITALIC_STAR = Regex("(?<![*\\w])\\*(?![*\\s])(.+?)(?<![*\\s])\\*(?![*\\w])")
    private val ITALIC_UNDER = Regex("(?<![_\\w])_(?![_\\s])(.+?)(?<![_\\s])_(?![_\\w])")
    private val URL = Regex("https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,;:!?'\"]")

    /** Splits [text] into classified lines. An empty text is one empty plain line. */
    fun lines(text: String): List<MdLine> {
        val out = ArrayList<MdLine>()
        var start = 0
        var index = 0
        while (true) {
            val nl = text.indexOf('\n', start)
            val end = if (nl < 0) text.length else nl
            out += classify(index, start, text.substring(start, end))
            if (nl < 0) break
            start = nl + 1
            index++
        }
        return out
    }

    /** The line containing [offset] (an offset at a line's end belongs to that line). */
    fun lineAt(lines: List<MdLine>, offset: Int): MdLine {
        val o = offset.coerceAtLeast(0)
        return lines.firstOrNull { o in it.start..it.end } ?: lines.last()
    }

    private fun classify(index: Int, start: Int, line: String): MdLine {
        val end = start + line.length
        CHECK.find(line)?.let { m ->
            return MdLine(
                index, start, end, LineKind.CHECK,
                indent = m.groupValues[1].length, markerLength = m.value.length - m.groupValues[1].length,
                checked = m.groupValues[2] != " "
            )
        }
        BULLET.find(line)?.let { m ->
            return MdLine(index, start, end, LineKind.BULLET, indent = m.groupValues[1].length,
                markerLength = m.value.length - m.groupValues[1].length)
        }
        NUMBER.find(line)?.let { m ->
            return MdLine(index, start, end, LineKind.NUMBERED, indent = m.groupValues[1].length,
                markerLength = m.value.length - m.groupValues[1].length,
                number = m.groupValues[2].toIntOrNull() ?: 1)
        }
        HEADING.find(line)?.let { m ->
            return MdLine(index, start, end, LineKind.HEADING, markerLength = m.value.length, level = m.groupValues[1].length)
        }
        QUOTE.find(line)?.let { m ->
            return MdLine(index, start, end, LineKind.QUOTE, markerLength = m.value.length)
        }
        return MdLine(index, start, end, LineKind.PLAIN)
    }

    /** Inline formats across the whole text (they never span lines), with offsets into [text]. */
    fun spans(text: String): List<MdSpan> = lines(text).flatMap { inlineSpans(text.substring(it.contentStart.coerceAtMost(it.end), it.end), it.contentStart.coerceAtMost(it.end)) }

    /** Inline formats within a single line's content. [offset] is where [line] starts in the full text. */
    fun inlineSpans(line: String, offset: Int = 0): List<MdSpan> {
        val spans = ArrayList<MdSpan>()
        val taken = ArrayList<IntRange>()
        fun free(r: IntRange) = taken.none { it.first <= r.last && r.first <= it.last }

        // Code first, then anything bracketed: their contents are not formatted further.
        for (m in CODE.findAll(line)) {
            spans += MdSpan(offset + m.range.first, offset + m.range.last + 1, SpanType.CODE,
                offset + m.range.first + 1, offset + m.range.last)
            taken += m.range
        }
        for (m in WIKI.findAll(line)) {
            if (!free(m.range)) continue
            spans += MdSpan(offset + m.range.first, offset + m.range.last + 1, SpanType.WIKILINK,
                offset + m.range.first + 2, offset + m.range.last - 1, m.groupValues[1].trim())
            taken += m.range
        }
        for (m in LINK.findAll(line)) {
            if (!free(m.range)) continue
            val labelEnd = m.range.first + 1 + m.groupValues[1].length
            spans += MdSpan(offset + m.range.first, offset + m.range.last + 1, SpanType.LINK,
                offset + m.range.first + 1, offset + labelEnd, m.groupValues[2])
            taken += m.range
        }
        fun wrapped(regex: Regex, type: SpanType, markerLen: Int) {
            for (m in regex.findAll(line)) {
                if (!free(m.range)) continue
                spans += MdSpan(offset + m.range.first, offset + m.range.last + 1, type,
                    offset + m.range.first + markerLen, offset + m.range.last + 1 - markerLen)
            }
        }
        // Emphasis may nest inside itself/each other but not inside code or links.
        wrapped(BOLD, SpanType.BOLD, 2)
        wrapped(STRIKE, SpanType.STRIKE, 2)
        wrapped(ITALIC_STAR, SpanType.ITALIC, 1)
        wrapped(ITALIC_UNDER, SpanType.ITALIC, 1)
        for (m in URL.findAll(line)) {
            if (!free(m.range)) continue
            spans += MdSpan(offset + m.range.first, offset + m.range.last + 1, SpanType.URL,
                offset + m.range.first, offset + m.range.last + 1, m.value)
        }
        return spans.sortedBy { it.start }
    }

    /** True if [text] uses any Markdown the plain card preview would show as raw symbols. */
    fun isRich(text: String): Boolean {
        if (text.isEmpty()) return false
        if (lines(text).any { it.kind != LineKind.PLAIN }) return true
        return lines(text).any { inlineSpans(text.substring(it.start, it.end), it.start).any { s -> s.type != SpanType.URL } }
    }

    /** How links are written when Markdown is flattened to plain text. */
    enum class PlainStyle { PREVIEW, SHARE }

    /**
     * Markdown flattened for card previews and "share as text": markers removed, list items
     * turned into bullets / ticks, links reduced to their label (plus the address when sharing).
     */
    fun toPlain(text: String, style: PlainStyle = PlainStyle.PREVIEW): String =
        lines(text).joinToString("\n") { line ->
            val raw = text.substring(line.start, line.end)
            val body = raw.substring((line.contentStart - line.start).coerceIn(0, raw.length))
            val indent = " ".repeat(line.indent)
            val flat = stripInline(body, style)
            when (line.kind) {
                LineKind.CHECK -> indent + (if (line.checked) "☑ " else "☐ ") + flat
                LineKind.BULLET -> "$indent• $flat"
                LineKind.NUMBERED -> "$indent${line.number}. $flat"
                else -> flat
            }
        }

    /** Removes inline markers from one line's content. */
    fun stripInline(content: String, style: PlainStyle = PlainStyle.PREVIEW): String {
        val spans = inlineSpans(content, 0)
        if (spans.isEmpty()) return content
        // Rebuild from the outermost spans; nested emphasis is flattened by recursion on the content.
        val sb = StringBuilder()
        var pos = 0
        for (s in spans.sortedBy { it.start }) {
            if (s.start < pos) continue // nested inside a span already written
            sb.append(content, pos, s.start)
            val inner = content.substring(s.contentStart, s.contentEnd)
            when (s.type) {
                SpanType.CODE, SpanType.URL -> sb.append(inner)
                SpanType.LINK -> {
                    sb.append(stripInline(inner, style))
                    if (style == PlainStyle.SHARE && !s.target.isNullOrBlank()) sb.append(" (").append(s.target).append(')')
                }
                SpanType.WIKILINK -> sb.append(inner)
                else -> sb.append(stripInline(inner, style))
            }
            pos = s.end
        }
        sb.append(content, pos, content.length)
        return sb.toString()
    }

    /** `[[Title]]` references in [text], in order, without duplicates (case-insensitive). */
    fun wikiLinks(text: String): List<String> =
        WIKI.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.toList()
}
