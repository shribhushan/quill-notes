package com.quillnotes.domain

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.text.Markdown
import java.lang.Character.UnicodeBlock
import java.text.Normalizer

/** Narrowing options for [NoteSearch]; an empty [types] set means every kind. */
data class SearchFilters(
    val types: Set<NoteType> = emptySet(),
    val tag: String? = null,
    val notebook: String? = null,
    val includeArchived: Boolean = true,
    val onlyPinned: Boolean = false
)

data class SearchHit(
    val note: NoteEntity,
    val score: Int,
    /** Ranges to highlight within [NoteEntity.title]. */
    val titleMatches: List<IntRange>,
    /** A short plain-text excerpt (around the first body match when there is one). */
    val snippet: String,
    /** Ranges to highlight within [snippet]. */
    val snippetMatches: List<IntRange>
)

/** In-memory search over decrypted notes (text is encrypted at rest, so SQL cannot do this). */
object NoteSearch {
    private const val SNIPPET_LENGTH = 140
    private const val SNIPPET_LEAD = 40

    /**
     * Case- and accent-insensitive, one character in → one character out, so match positions in
     * the folded text are valid in the original. Only Latin letters lose accents; other scripts
     * (Devanagari vowel signs, Hangul…) are left intact.
     */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val lower = c.lowercaseChar()
            val block = UnicodeBlock.of(lower)
            val latin = block == UnicodeBlock.LATIN_1_SUPPLEMENT || block == UnicodeBlock.LATIN_EXTENDED_A ||
                block == UnicodeBlock.LATIN_EXTENDED_B || block == UnicodeBlock.LATIN_EXTENDED_ADDITIONAL
            if (latin) {
                val d = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)
                sb.append(if (d.isNotEmpty() && d[0].isLetter()) d[0] else lower)
            } else {
                sb.append(lower)
            }
        }
        return sb.toString()
    }

    fun search(notes: List<NoteEntity>, query: String, filters: SearchFilters = SearchFilters()): List<SearchHit> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { fold(it.removePrefix("#")) }.filter { it.isNotEmpty() }
        val candidates = notes.filter { passes(it, filters) }

        if (tokens.isEmpty()) {
            return candidates.sortedByDescending { it.updatedAt }.map { note ->
                SearchHit(note, 0, emptyList(), if (note.isLocked) "" else excerpt(Markdown.toPlain(note.content)), emptyList())
            }
        }

        val hits = ArrayList<SearchHit>()
        for (note in candidates) {
            // A locked note's text must not show up in results, so only its title is searchable.
            val plain = if (note.isLocked) "" else Markdown.toPlain(note.content)
            val title = fold(note.title)
            val body = fold(plain)
            val tags = fold(note.tags)
            val notebook = fold(note.notebook.orEmpty())

            var score = 0
            var matchedAll = true
            for (t in tokens) {
                var tokenScore = 0
                val inTitle = title.indexOf(t)
                if (inTitle >= 0) {
                    tokenScore += 100
                    tokenScore += when {
                        title == t -> 100                                   // the title is exactly the word
                        inTitle == 0 -> 70                                  // the title starts with it
                        !title[inTitle - 1].isLetterOrDigit() -> 50         // it starts a word in the title
                        else -> 0
                    }
                }
                if (tags.contains(t)) tokenScore += 40
                if (notebook.contains(t)) tokenScore += 20
                val inBody = body.indexOf(t)
                if (inBody >= 0) tokenScore += 10 + (countOccurrences(body, t) - 1).coerceAtMost(5)
                if (tokenScore == 0) { matchedAll = false; break }
                score += tokenScore
            }
            if (!matchedAll) continue
            if (note.isPinned) score += 5

            val titleMatches = ranges(title, tokens)
            val firstBody = tokens.map { body.indexOf(it) }.filter { it >= 0 }.minOrNull()
            val (snippet, snippetMatches) = if (firstBody != null) {
                val start = windowStart(plain, firstBody)
                val end = minOf(plain.length, start + SNIPPET_LENGTH)
                val text = (if (start > 0) "…" else "") + plain.substring(start, end).replace('\n', ' ') + (if (end < plain.length) "…" else "")
                val shift = if (start > 0) 1 else 0
                text to ranges(body.substring(start, end), tokens).map { (it.first + shift)..(it.last + shift) }
            } else {
                excerpt(plain) to emptyList()
            }
            hits += SearchHit(note, score, titleMatches, snippet, snippetMatches)
        }
        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenByDescending { it.note.updatedAt })
    }

    private fun passes(n: NoteEntity, f: SearchFilters): Boolean {
        if (n.isDeleted || n.isTemplate) return false
        if (f.types.isNotEmpty() && n.type !in f.types) return false
        if (!f.includeArchived && n.isArchived) return false
        if (f.onlyPinned && !n.isPinned) return false
        if (f.tag != null && com.quillnotes.ui.util.parseTags(n.tags).none { it.equals(f.tag, ignoreCase = true) }) return false
        if (f.notebook != null && !f.notebook.equals(n.notebook, ignoreCase = true)) return false
        return true
    }

    private fun excerpt(plain: String): String {
        val flat = plain.replace('\n', ' ').trim()
        return if (flat.length > SNIPPET_LENGTH) flat.substring(0, SNIPPET_LENGTH).trimEnd() + "…" else flat
    }

    /** Start of the excerpt window: a little before the match, on a word boundary if one is near. */
    private fun windowStart(text: String, matchAt: Int): Int {
        var s = (matchAt - SNIPPET_LEAD).coerceAtLeast(0)
        if (s == 0) return 0
        val boundary = text.indexOf(' ', s)
        if (boundary in s until matchAt) s = boundary + 1
        return s
    }

    private fun countOccurrences(text: String, t: String): Int {
        var n = 0
        var i = text.indexOf(t)
        while (i >= 0) { n++; i = text.indexOf(t, i + t.length) }
        return n
    }

    /** Every occurrence of every token in the (folded) text, merged where they touch. */
    private fun ranges(folded: String, tokens: List<String>): List<IntRange> {
        val found = ArrayList<IntRange>()
        for (t in tokens) {
            var i = folded.indexOf(t)
            while (i >= 0) {
                found += i until (i + t.length)
                i = folded.indexOf(t, i + t.length)
            }
        }
        found.sortBy { it.first }
        val merged = ArrayList<IntRange>()
        for (r in found) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            else merged += r
        }
        return merged
    }
}
