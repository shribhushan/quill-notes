package com.quillnotes.ui.screens.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.notifications.AlarmScheduler
import com.quillnotes.ui.util.startOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val tasks: StateFlow<List<NoteEntity>> = repository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showCompleted = MutableStateFlow(true)
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    fun toggleShowCompleted() {
        _showCompleted.value = !_showCompleted.value
    }

    fun toggleComplete(task: NoteEntity) {
        val completing = !task.isCompleted
        viewModelScope.launch {
            repository.toggleTaskComplete(task.id, completing)
            val due = task.dueDate
            if (completing) {
                alarmScheduler.cancelReminder(task.id)
            } else if (due != null && due > System.currentTimeMillis()) {
                alarmScheduler.scheduleReminder(task.id, task.title, due)
            }
        }
    }

    /** Creates a task straight from the planner's quick-add field. */
    fun quickAdd(title: String, dueToday: Boolean) {
        val text = title.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // "Today" tasks get an evening reminder; it's skipped if that time has already passed.
            val due = if (dueToday) startOfDay(now) + 18 * 60 * 60 * 1000L else null
            val id = repository.createNote(
                NoteEntity(title = text, content = "", type = NoteType.TASK, dueDate = due)
            )
            if (due != null && due > now) alarmScheduler.scheduleReminder(id, text, due)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            alarmScheduler.cancelReminder(id)
            _deleteEvents.emit(id)
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch {
            repository.restoreNote(id)
            val task = repository.getNoteById(id) ?: return@launch
            val due = task.dueDate
            if (!task.isCompleted && due != null && due > System.currentTimeMillis()) {
                alarmScheduler.scheduleReminder(id, task.title, due)
            }
        }
    }
}
