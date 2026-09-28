package com.quillnotes.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val sortOrder: StateFlow<String> = prefs.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "updated")

    val viewMode: StateFlow<String> = prefs.notesViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "list")

    private val rawNotes: Flow<List<NoteEntity>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getQuickNotes()
            else repository.searchNotes(query)
        }

    val notes: StateFlow<List<NoteEntity>> = combine(rawNotes, sortOrder) { list, order ->
        sortNotes(list, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noteCount: StateFlow<Int> = repository.getCountByType(NoteType.NOTE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    private val _batchDeleteEvents = MutableSharedFlow<List<Long>>()
    val batchDeleteEvents: SharedFlow<List<Long>> = _batchDeleteEvents.asSharedFlow()

    // ── Multi-select ───────────────────────────────────────────
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
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
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        val allPinned = notes.value.filter { it.id in ids }.let { sel ->
            sel.isNotEmpty() && sel.all { it.isPinned }
        }
        viewModelScope.launch {
            ids.forEach { repository.togglePin(it, !allPinned) }
            _selectedIds.value = emptySet()
        }
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
