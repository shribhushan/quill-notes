package com.quillnotes.data.export

import com.quillnotes.data.sync.NotePayload
import com.quillnotes.domain.Priority
import com.quillnotes.domain.RepeatRule
import com.quillnotes.domain.RepeatUnit
import com.quillnotes.text.Markdown
import com.quillnotes.ui.util.parseTags
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Notes as Markdown / plain text files, for export and for leaving Quill without lock-in. */
object NoteExporter {

    /** Markdown with a small YAML header (Obsidian, Jekyll and most editors read it). */
    fun markdown(p: NotePayload, attachmentLinks: List<String> = emptyList()): String = buildString {
        append("---\n")
        yaml("title", p.title.ifBlank { null }?.let(::quote))
        yaml("type", p.type.lowercase())
        yaml("created", iso(p.createdAt))
        yaml("updated", iso(p.updatedAt))
        parseTags(p.tags).takeIf { it.isNotEmpty() }?.let { yaml("tags", it.joinToString(", ", "[", "]") { t -> quote(t) }) }
        yaml("notebook", p.notebook?.takeIf { it.isNotBlank() }?.let(::quote))
        if (p.isPinned) yaml("pinned", "true")
        yaml("color", p.color)
        yaml("mood", p.mood?.takeIf { it.isNotBlank() })
        p.dueDate?.let { yaml("due", iso(it)) }
        RepeatRule.parse(p.repeatRule)?.let { yaml("repeat", repeatWord(it)) }
        if (p.priority > 0) yaml("priority", Priority.of(p.priority).name.lowercase())
        if (p.type.equals("TASK", ignoreCase = true)) yaml("completed", p.isCompleted.toString())
        if (p.isArchived) yaml("archived", "true")
        if (p.isTemplate) yaml("template", "true")
        if (p.isLocked) yaml("locked", "true")
        append("---\n\n")

        val body = p.content.trimEnd()
        if (p.title.isNotBlank() && !body.startsWith("# ${p.title.trim()}")) {
            append("# ").append(p.title.trim()).append("\n\n")
        }
        if (body.isNotEmpty()) append(body).append('\n')
        if (attachmentLinks.isNotEmpty()) {
            append('\n')
            attachmentLinks.forEach { append("![](").append(it).append(")\n") }
        }
    }

    /** Title, a blank line, then the text with Markdown flattened (links keep their addresses). */
    fun plainText(p: NotePayload): String =
        listOf(p.title.trim(), Markdown.toPlain(p.content, Markdown.PlainStyle.SHARE).trim())
            .filter { it.isNotEmpty() }.joinToString("\n\n")

    /** A safe file name (with extension) for a note, unique among [taken] (compared case-insensitively). */
    fun fileName(p: NotePayload, taken: MutableSet<String>, extension: String = "md"): String {
        val firstLine = p.content.lineSequence().map { it.trim().trimStart('#', ' ', '-', '[', ']', 'x', 'X') }.firstOrNull { it.isNotEmpty() }
        val base = sanitize(p.title.ifBlank { firstLine.orEmpty() }).ifEmpty { "Untitled" }
        var candidate = "$base.$extension"
        var n = 2
        while (!taken.add(candidate.lowercase())) candidate = "$base ($n).$extension".also { n++ }
        return candidate
    }

    /** Folder inside a bulk export: notebook name, "Journal", "Tasks" or "Templates". */
    fun folderFor(p: NotePayload): String = when {
        p.isTemplate -> "Templates"
        p.type.equals("JOURNAL", ignoreCase = true) -> "Journal"
        p.type.equals("TASK", ignoreCase = true) -> "Tasks"
        else -> sanitize(p.notebook.orEmpty()).ifEmpty { "Notes" }
    }

    /**
     * Writes a zip of Markdown files (one folder per notebook / kind) plus the images in
     * `attachments/`. Trashed notes are left out. [attachmentBytes] returns null for files that
     * are not on this phone (they are then skipped, not failed).
     */
    fun writeZip(out: OutputStream, notes: List<NotePayload>, attachmentBytes: (String) -> ByteArray?): ZipSummary {
        var written = 0
        var images = 0
        val writtenImages = HashSet<String>()
        val takenByFolder = HashMap<String, MutableSet<String>>()
        ZipOutputStream(out).use { zip ->
            for (p in notes.filter { !it.isTrashed && !it.isPurged }) {
                val links = ArrayList<String>()
                for (a in p.attachments) {
                    val file = "${a.uuid}.${extensionFor(a.mime)}"
                    if (writtenImages.contains(a.uuid)) { links += "../attachments/$file"; continue }
                    val bytes = attachmentBytes(a.uuid) ?: continue
                    zip.putNextEntry(ZipEntry("attachments/$file"))
                    zip.write(bytes)
                    zip.closeEntry()
                    writtenImages += a.uuid
                    images++
                    links += "../attachments/$file"
                }
                val folder = folderFor(p)
                val name = fileName(p, takenByFolder.getOrPut(folder) { HashSet() })
                zip.putNextEntry(ZipEntry("$folder/$name"))
                zip.write(markdown(p, links).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                written++
            }
        }
        return ZipSummary(written, images)
    }

    data class ZipSummary(val notes: Int, val images: Int)

    fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    // ── helpers ──

    private fun StringBuilder.yaml(key: String, value: String?) {
        if (value != null) append(key).append(": ").append(value).append('\n')
    }

    private fun iso(ms: Long): String = Instant.ofEpochSecond(ms / 1000).toString()

    private fun repeatWord(r: RepeatRule): String {
        val unit = when (r.unit) {
            RepeatUnit.DAY -> "day"; RepeatUnit.WEEK -> "week"; RepeatUnit.MONTH -> "month"; RepeatUnit.YEAR -> "year"
        }
        return if (r.interval == 1) "every $unit" else "every ${r.interval} ${unit}s"
    }

    /** Plain when it is safe as a bare YAML scalar, otherwise double-quoted with escapes. */
    private fun quote(s: String): String {
        val plain = s.isNotEmpty() && Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} _./+-]*").matches(s) &&
            s.lowercase() !in setOf("true", "false", "null", "yes", "no", "on", "off", "~") && s.toDoubleOrNull() == null
        if (plain) return s
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }

    private val RESERVED = setOf("con", "prn", "aux", "nul") + (1..9).flatMap { listOf("com$it", "lpt$it") }

    /** File-system safe, human readable, at most 80 characters. */
    fun sanitize(name: String): String {
        var s = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trim('.')
        if (s.length > 80) s = s.substring(0, 80).trim().trimEnd('.')
        if (s.lowercase() in RESERVED) s = "_$s"
        return s
    }
}
