package com.quillnotes.ui.screens.planner

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
class PlannerViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    val tasks: StateFlow<List<NoteEntity>> = repository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskCount: StateFlow<Int> = repository.getCountByType(NoteType.TASK)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    fun toggleComplete(id: Long, completed: Boolean) {
        viewModelScope.launch { repository.toggleTaskComplete(id, !completed) }
    }

    fun deleteTask(id: Long) {
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
