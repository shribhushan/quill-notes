package com.quillnotes.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
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
    data class Tag(val name: String) : NoteFilter
    data class Color(val key: String) : NoteFilter
}

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

    private val allNotes: StateFlow<List<NoteEntity>> = repository.getQuickNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Searching happens in memory: note text is encrypted at rest, so SQL LIKE can't see it. */
    private val debouncedQuery: Flow<String> = _searchQuery
        .debounce(150)
        .onStart { emit(_searchQuery.value) }

    val notes: StateFlow<List<NoteEntity>> =
        combine(allNotes, debouncedQuery, _filter, sortOrder) { list, query, filter, order ->
            sortNotes(list.filter { it.matches(query) && it.matches(filter) }, order)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noteCount: StateFlow<Int> = allNotes.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Most-used tags first, so the chip row surfaces what matters. */
    val tags: StateFlow<List<String>> = allNotes.map { list ->
        list.flatMap { parseTags(it.tags) }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usedColors: StateFlow<List<String>> = allNotes.map { list ->
        val used = list.mapNotNull { it.color }.toSet()
        NoteColorKeys.filter { it in used }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    private val _batchDeleteEvents = MutableSharedFlow<List<Long>>()
    val batchDeleteEvents: SharedFlow<List<Long>> = _batchDeleteEvents.asSharedFlow()

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

    private fun NoteEntity.matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, ignoreCase = true) ||
            content.contains(q, ignoreCase = true) ||
            tags.contains(q.removePrefix("#"), ignoreCase = true)
    }

    private fun NoteEntity.matches(filter: NoteFilter): Boolean = when (filter) {
        NoteFilter.All -> true
        NoteFilter.Pinned -> isPinned
        is NoteFilter.Tag -> parseTags(tags).any { it.equals(filter.name, ignoreCase = true) }
        is NoteFilter.Color -> color == filter.key
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
