package com.quillnotes.ui.screens.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.domain.JournalStats
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

enum class JournalView { TIMELINE, CALENDAR }

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val prefs: PreferencesRepository
) : ViewModel() {

    val entries: StateFlow<List<NoteEntity>> = repository.getJournalEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val entryCount: StateFlow<Int> = repository.getCountByType(NoteType.JOURNAL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val view: StateFlow<JournalView> = prefs.journalView
        .map { if (it == "calendar") JournalView.CALENDAR else JournalView.TIMELINE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalView.TIMELINE)

    private val _monthStart = MutableStateFlow(JournalStats.startOfMonth(System.currentTimeMillis()))
    val monthStart: StateFlow<Long> = _monthStart.asStateFlow()

    private val _selectedDay = MutableStateFlow<Long?>(null)
    val selectedDay: StateFlow<Long?> = _selectedDay.asStateFlow()

    private val _deleteEvents = MutableSharedFlow<Long>()
    val deleteEvents: SharedFlow<Long> = _deleteEvents.asSharedFlow()

    fun setView(view: JournalView) {
        viewModelScope.launch { prefs.setJournalView(if (view == JournalView.CALENDAR) "calendar" else "timeline") }
    }

    /** Moves the calendar by [delta] months; 0 jumps back to this month. */
    fun shiftMonth(delta: Int) {
        _monthStart.value = if (delta == 0) JournalStats.startOfMonth(System.currentTimeMillis())
        else JournalStats.shiftMonths(_monthStart.value, delta)
        _selectedDay.value = null
    }

    /** Selecting the chosen day again clears it, showing the whole month. */
    fun selectDay(day: Long?) {
        _selectedDay.value = if (_selectedDay.value == day) null else day
    }

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
