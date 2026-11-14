package com.quillnotes.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.ui.theme.NoteColorKeys
import com.quillnotes.ui.util.parseTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Narrows the notes list; chosen from the chip row on the home screen. */
sealed interface NoteFilter {
    data object All : NoteFilter
    data object Pinned : NoteFilter
    data object Archived : NoteFilter
    data class Tag(val name: String) : NoteFilter
    data class Color(val key: String) : NoteFilter
    data class Notebook(val name: String) : NoteFilter
}

/** A notebook and how many (unarchived) notes are in it. */
data class NotebookInfo(val name: String, val count: Int)

/** Something that happened to a batch of notes and can be undone. */
data class ArchiveEvent(val ids: List<Long>, val archived: Boolean)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow<NoteFilter>(NoteFilter.All)
    val filter: StateFlow<NoteFilter> = _filter.asStateFlow()

    val sortOrder: StateFlow<String> = prefs.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "updated")

    val viewMode: StateFlow<String> = prefs.notesViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "grid")

    /** Every note that is not in the trash, archived ones included. */
    private val allNotes: StateFlow<List<NoteEntity>> = repository.getQuickNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val active: Flow<List<NoteEntity>> = allNotes.map { list -> list.filterNot { it.isArchived } }

    /** Searching happens in memory: note text is encrypted at rest, so SQL LIKE can't see it. */
    private val debouncedQuery: Flow<String> = _searchQuery
        .debounce(150)
        .onStart { emit(_searchQuery.value) }

    val notes: StateFlow<List<NoteEntity>> =
        combine(allNotes, debouncedQuery, _filter, sortOrder) { list, query, filter, order ->
            val scoped = list.filter { if (filter == NoteFilter.Archived) it.isArchived else !it.isArchived }
            sortNotes(scoped.filter { it.matches(query) && it.matches(filter) }, order)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noteCount: StateFlow<Int> = active.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val archivedCount: StateFlow<Int> = allNotes.map { list -> list.count { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Most-used tags first, so the chip row surfaces what matters. */
    val tags: StateFlow<List<String>> = active.map { list ->
        list.flatMap { parseTags(it.tags) }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usedColors: StateFlow<List<String>> = active.map { list ->
        val used = list.mapNotNull { it.color }.toSet()
        NoteColorKeys.filter { it in used }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Notebooks in use, plus ones the user made that are still empty, A to Z. */
    val notebooks: StateFlow<List<NotebookInfo>> = combine(active, prefs.notebookNames) { list, saved ->
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        for (n in list) {
            val name = n.notebook?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val key = name.lowercase()
            counts[key] = (counts[key]?.first ?: name) to ((counts[key]?.second ?: 0) + 1)
        }
        for (name in saved) counts.putIfAbsent(name.lowercase(), name to 0)
        counts.values.map { NotebookInfo(it.first, it.second) }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The first picture of each note, by note uuid, for card covers. */
    val covers: StateFlow<Map<String, AttachmentEntity>> = repository.observeAllAttachments()
        .map { all -> all.mapValues { it.value.first() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val templates: StateFlow<List<NoteEntity>> = repository.getTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    private val _batchDeleteEvents = MutableSharedFlow<List<Long>>()
    val batchDeleteEvents: SharedFlow<List<Long>> = _batchDeleteEvents.asSharedFlow()

    private val _archiveEvents = MutableSharedFlow<ArchiveEvent>()
    val archiveEvents: SharedFlow<ArchiveEvent> = _archiveEvents.asSharedFlow()

    // ── Multi-select ───────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Selecting the active filter again clears it. */
    fun setFilter(filter: NoteFilter) {
        _filter.value = if (_filter.value == filter) NoteFilter.All else filter
    }

    fun setSortOrder(order: String) {
        viewModelScope.launch { prefs.setSortOrder(order) }
    }

    fun setViewMode(mode: String) {
        viewModelScope.launch { prefs.setNotesViewMode(mode) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            _deleteEvents.emit(id)
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restoreNote(id) }
    }

    fun togglePin(id: Long, pinned: Boolean) {
        viewModelScope.launch { repository.togglePin(id, !pinned) }
    }

    /** Ticks a checklist item straight from a card. */
    fun toggleChecklistItem(noteId: Long, lineIndex: Int) {
        viewModelScope.launch { repository.toggleChecklistItem(noteId, lineIndex) }
    }

    fun startSelection(id: Long) {
        _selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.deleteNote(it) }
            _selectedIds.value = emptySet()
            _batchDeleteEvents.emit(ids)
        }
    }

    fun undoBatchDelete(ids: List<Long>) {
        viewModelScope.launch { ids.forEach { repository.restoreNote(it) } }
    }

    fun pinSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        val selection = allNotes.value.filter { it.id in ids }
        val allPinned = selection.isNotEmpty() && selection.all { it.isPinned }
        viewModelScope.launch {
            ids.forEach { repository.togglePin(it, !allPinned) }
            _selectedIds.value = emptySet()
        }
    }

    /** Archives the selection (or restores it when everything selected is already archived). */
    fun archiveSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        val selection = allNotes.value.filter { it.id in ids }
        val archive = !(selection.isNotEmpty() && selection.all { it.isArchived })
        viewModelScope.launch {
            repository.setArchived(ids, archive)
            _selectedIds.value = emptySet()
            _archiveEvents.emit(ArchiveEvent(ids, archive))
        }
    }

    fun undoArchive(event: ArchiveEvent) {
        viewModelScope.launch { repository.setArchived(event.ids, !event.archived) }
    }

    fun archiveNote(id: Long) {
        viewModelScope.launch {
            repository.setArchived(listOf(id), true)
            _archiveEvents.emit(ArchiveEvent(listOf(id), true))
        }
    }

    fun moveSelectedToNotebook(name: String?) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            name?.takeIf { it.isNotBlank() }?.let { prefs.addNotebook(it) }
            repository.moveToNotebook(ids, name)
            _selectedIds.value = emptySet()
        }
    }

    // ── Notebooks ──────────────────────────────────────────────

    fun createNotebook(name: String) {
        viewModelScope.launch { prefs.addNotebook(name) }
    }

    fun renameNotebook(from: String, to: String) {
        val target = to.trim()
        if (target.isEmpty()) return
        viewModelScope.launch {
            repository.renameNotebook(from, target)
            prefs.renameNotebook(from, target)
            if ((_filter.value as? NoteFilter.Notebook)?.name.equals(from, ignoreCase = true)) {
                _filter.value = NoteFilter.Notebook(target)
            }
        }
    }

    /** Removes the notebook; its notes stay, unfiled. */
    fun deleteNotebook(name: String) {
        viewModelScope.launch {
            repository.deleteNotebook(name)
            prefs.removeNotebook(name)
            if ((_filter.value as? NoteFilter.Notebook)?.name.equals(name, ignoreCase = true)) {
                _filter.value = NoteFilter.All
            }
        }
    }

    // ── Templates ──────────────────────────────────────────────

    /** Creates a note from a saved template and returns its id. */
    suspend fun createFromTemplate(template: NoteEntity): Long = repository.createFromTemplate(template)

    /** Creates a note from the built-in template text and returns its id. */
    suspend fun createFromText(title: String, content: String): Long =
        repository.createNote(NoteEntity(title = title, content = content, type = NoteType.NOTE))

    // ── Filtering ──────────────────────────────────────────────

    private fun NoteEntity.matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, ignoreCase = true) ||
            (!isLocked && content.contains(q, ignoreCase = true)) ||
            tags.contains(q.removePrefix("#"), ignoreCase = true) ||
            (notebook?.contains(q, ignoreCase = true) == true)
    }

    private fun NoteEntity.matches(filter: NoteFilter): Boolean = when (filter) {
        NoteFilter.All, NoteFilter.Archived -> true
        NoteFilter.Pinned -> isPinned
        is NoteFilter.Tag -> parseTags(tags).any { it.equals(filter.name, ignoreCase = true) }
        is NoteFilter.Color -> color == filter.key
        is NoteFilter.Notebook -> notebook.equals(filter.name, ignoreCase = true)
    }

    private fun sortNotes(list: List<NoteEntity>, order: String): List<NoteEntity> {
        val comparator = when (order) {
            "updated_asc" -> compareBy<NoteEntity> { it.updatedAt }
            "title_asc" -> compareBy { it.title.lowercase() }
            "title_desc" -> compareByDescending<NoteEntity> { it.title.lowercase() }
            else -> compareByDescending { it.updatedAt } // "updated" — newest first (default)
        }
        return list.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.then(comparator))
    }
}
