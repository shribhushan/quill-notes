package com.quillnotes.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.domain.NoteSearch
import com.quillnotes.domain.SearchFilters
import com.quillnotes.domain.SearchHit
import com.quillnotes.ui.util.parseTags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/** What the user typed and which filters are on. */
data class SearchQuery(
    val text: String = "",
    val types: Set<NoteType> = emptySet(),
    val notebook: String? = null,
    val tag: String? = null,
    val includeArchived: Boolean = true
)

data class SearchUiState(
    val query: SearchQuery = SearchQuery(),
    val results: List<SearchHit> = emptyList(),
    val notebooks: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val totalNotes: Int = 0,
    val now: Long = System.currentTimeMillis()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: NoteRepository
) : ViewModel() {

    private val _query = MutableStateFlow(SearchQuery())
    val query: StateFlow<SearchQuery> = _query.asStateFlow()

    private val notes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Typing is debounced; filter taps apply at once. */
    private val settled = _query
        .debounce { if (it.text.isBlank()) 0L else 120L }
        .onStart { emit(_query.value) }

    val state: StateFlow<SearchUiState> = combine(notes, settled) { all, q ->
        val filters = SearchFilters(types = q.types, tag = q.tag, notebook = q.notebook, includeArchived = q.includeArchived)
        SearchUiState(
            query = q,
            results = NoteSearch.search(all, q.text, filters).take(MAX_RESULTS),
            notebooks = all.mapNotNull { it.notebook?.takeIf { n -> n.isNotBlank() } }
                .distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            tags = all.flatMap { parseTags(it.tags) }.groupingBy { it.lowercase() }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }.take(30),
            totalNotes = all.size
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onTextChange(text: String) = _query.update { it.copy(text = text) }

    /** Tapping the active kind again clears it, so "everything" is one tap away. */
    fun toggleType(type: NoteType?) = _query.update {
        it.copy(types = if (type == null || it.types == setOf(type)) emptySet() else setOf(type))
    }

    fun setNotebook(name: String?) = _query.update { it.copy(notebook = name) }

    fun setTag(tag: String?) = _query.update { it.copy(tag = tag) }

    fun toggleArchived() = _query.update { it.copy(includeArchived = !it.includeArchived) }

    private inline fun MutableStateFlow<SearchQuery>.update(transform: (SearchQuery) -> SearchQuery) {
        value = transform(value)
    }

    private companion object {
        const val MAX_RESULTS = 200
    }
}
