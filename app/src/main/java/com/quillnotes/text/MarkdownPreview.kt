package com.quillnotes.text

/** One line of a card preview: markers removed, with what a card needs to draw it and to tick it. */
data class PreviewLine(
    val kind: LineKind,
    /** The line's text with inline Markdown stripped. */
    val text: String,
    val checked: Boolean = false,
    /** Index of the line in the note, so a tap on its checkbox can toggle it. */
    val lineIndex: Int = 0,
    val indent: Int = 0,
    val level: Int = 0,
    val number: Int = 0
)

data class Preview(val lines: List<PreviewLine>, val truncated: Boolean)

/** Builds the lines a note card shows. */
object MarkdownPreview {

    /**
     * Up to [maxLines] non-blank lines. When the note has a checklist, its items are listed first-class
     * (so unticked items are not pushed off the card by an introduction); otherwise lines keep note order.
     */
    fun build(content: String, maxLines: Int): Preview {
        val all = Markdown.lines(content).mapNotNull { line ->
            val raw = content.substring(line.start, line.end)
            if (raw.isBlank()) return@mapNotNull null
            val body = raw.substring((line.contentStart - line.start).coerceIn(0, raw.length))
            val text = Markdown.stripInline(body)
            if (text.isBlank() && line.kind != LineKind.CHECK) return@mapNotNull null
            PreviewLine(line.kind, text, line.checked, line.index, line.indent, line.level, line.number)
        }
        if (all.size <= maxLines) return Preview(all, truncated = false)
        return Preview(all.take(maxLines), truncated = true)
    }
}
