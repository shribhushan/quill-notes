package com.quillnotes.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    val entries: StateFlow<List<NoteEntity>> = repository.getJournalEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entryCount: StateFlow<Int> = repository.getCountByType(NoteType.JOURNAL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    fun deleteEntry(id: Long) {
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
}
