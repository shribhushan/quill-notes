package com.quillnotes.ui.screens.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.data.sync.ReminderSink
import com.quillnotes.domain.PlannerStats
import com.quillnotes.ui.util.startOfDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlannerView { LIST, WEEK }

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val reminders: ReminderSink,
    private val prefs: PreferencesRepository
) : ViewModel() {

    val tasks: StateFlow<List<NoteEntity>> = repository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showCompleted = MutableStateFlow(true)
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    val view: StateFlow<PlannerView> = prefs.plannerView
        .map { if (it == "week") PlannerView.WEEK else PlannerView.LIST }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerView.LIST)

    private val _weekStart = MutableStateFlow(PlannerStats.weekStart(System.currentTimeMillis()))
    val weekStart: StateFlow<Long> = _weekStart.asStateFlow()

    private val _selectedDay = MutableStateFlow(startOfDay(System.currentTimeMillis()))
    val selectedDay: StateFlow<Long> = _selectedDay.asStateFlow()

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    fun toggleShowCompleted() {
        _showCompleted.value = !_showCompleted.value
    }

    fun setView(view: PlannerView) {
        viewModelScope.launch { prefs.setPlannerView(if (view == PlannerView.WEEK) "week" else "list") }
    }

    /** Moves the week view by [delta] weeks (0 jumps back to the current week). */
    fun shiftWeek(delta: Int) {
        if (delta == 0) {
            val now = System.currentTimeMillis()
            _weekStart.value = PlannerStats.weekStart(now)
            _selectedDay.value = startOfDay(now)
        } else {
            _weekStart.value = PlannerStats.shiftWeeks(_weekStart.value, delta)
            // Keep the selection inside the visible week, on the same weekday.
            _selectedDay.value = PlannerStats.shiftWeeks(_selectedDay.value, delta)
        }
    }

    fun selectDay(dayStart: Long) {
        _selectedDay.value = dayStart
    }

    /**
     * Ticks or unticks a task. A recurring task does not finish: it starts its next round, so the
     * reminder is re-armed for the new due date.
     */
    fun toggleComplete(task: NoteEntity) {
        viewModelScope.launch {
            val updated = repository.setTaskCompleted(task.id, !task.isCompleted) ?: return@launch
            val due = updated.dueDate
            if (!updated.isCompleted && due != null && due > System.currentTimeMillis()) {
                reminders.schedule(updated.id, updated.title, due)
            } else {
                reminders.cancel(updated.id)
            }
        }
    }

    /** Creates a task straight from the planner's quick-add field. */
    fun quickAdd(title: String, dueToday: Boolean) {
        val now = System.currentTimeMillis()
        quickAddOn(title, if (dueToday) startOfDay(now) else null)
    }

    /** Quick-adds a task for a given day (the week view's selected day), or with no date. */
    fun quickAddOn(title: String, dayStart: Long?) {
        val text = title.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // Today gets an evening reminder (skipped if it has passed); other days start at 9:00.
            val due = dayStart?.let { if (it == startOfDay(now)) it + 18 * HOUR else it + 9 * HOUR }
            val id = repository.createNote(
                NoteEntity(title = text, content = "", type = NoteType.TASK, dueDate = due)
            )
            if (due != null && due > now) reminders.schedule(id, text, due)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            reminders.cancel(id)
            _deleteEvents.emit(id)
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch {
            repository.restoreNote(id)
            val task = repository.getNoteById(id) ?: return@launch
            val due = task.dueDate
            if (!task.isCompleted && due != null && due > System.currentTimeMillis()) {
                reminders.schedule(id, task.title, due)
            }
        }
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
