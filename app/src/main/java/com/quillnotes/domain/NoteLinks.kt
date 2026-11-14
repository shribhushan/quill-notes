package com.quillnotes.domain

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.text.Markdown

/** A note that can be the target of a `[[wiki link]]`. */
data class NoteRef(val id: Long, val title: String, val type: NoteType)

/** A `[[Title]]` in a note: [target] is null when no note has that title yet. */
data class LinkTarget(val title: String, val target: NoteRef?)

data class LinkInfo(
    /** Notes whose text contains `[[This note's title]]`. */
    val backlinks: List<NoteRef> = emptyList(),
    /** Every `[[...]]` in the current note, resolved where a matching note exists. */
    val outgoing: List<LinkTarget> = emptyList()
)

/** Wiki-style links between notes, resolved by title (case-insensitive). */
object NoteLinks {

    /** Built once per list of notes so a keystroke in the editor only does cheap lookups. */
    class Index internal constructor(
        internal val byTitle: Map<String, NoteRef>,
        /** note id -> lowercase titles it links to */
        internal val linksFrom: Map<Long, Set<String>>
    )

    fun index(notes: List<NoteEntity>): Index {
        val live = notes.filter { !it.isDeleted && !it.isTemplate }
        val byTitle = LinkedHashMap<String, NoteRef>()
        // Most recently edited wins when two notes share a title.
        for (n in live.sortedByDescending { it.updatedAt }) {
            val key = n.title.trim().lowercase()
            if (key.isNotEmpty() && key !in byTitle) byTitle[key] = NoteRef(n.id, n.title.trim(), n.type)
        }
        val links = HashMap<Long, Set<String>>()
        for (n in live) {
            val titles = Markdown.wikiLinks(n.content).map { it.lowercase() }.toSet()
            if (titles.isNotEmpty()) links[n.id] = titles
        }
        return Index(byTitle, links)
    }

    /** Links in and out of the note being edited ([selfId] is null until it has been saved). */
    fun info(index: Index, selfId: Long?, title: String, content: String): LinkInfo {
        val key = title.trim().lowercase()
        val backlinks = if (key.isEmpty()) emptyList() else index.linksFrom
            .filter { (id, titles) -> id != selfId && key in titles }
            .keys.mapNotNull { id -> index.byTitle.values.firstOrNull { it.id == id } }
            .sortedBy { it.title.lowercase() }
        val outgoing = Markdown.wikiLinks(content).map { LinkTarget(it, index.byTitle[it.lowercase()]?.takeIf { ref -> ref.id != selfId }) }
        return LinkInfo(backlinks, outgoing)
    }

    /** The note a `[[title]]` points at, if any. */
    fun resolve(index: Index, title: String): NoteRef? = index.byTitle[title.trim().lowercase()]
}
