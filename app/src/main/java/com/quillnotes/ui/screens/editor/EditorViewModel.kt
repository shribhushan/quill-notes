package com.quillnotes.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.notifications.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorState(
    val id: Long? = null,
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.NOTE,
    val mood: String? = null,
    val dueDate: Long? = null,
    val tags: String = "",
    val color: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedAt: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

private data class TextSnapshot(val title: String, val content: String)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var autosaveJob: Job? = null

    fun loadNote(noteId: Long?, noteType: String, initialContent: String? = null) {
        val type = when (noteType) {
            "journal" -> NoteType.JOURNAL
            "task" -> NoteType.TASK
            else -> NoteType.NOTE
        }

        if (noteId != null && noteId > 0) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true)
                val note = repository.getNoteById(noteId)
                if (note != null) {
                    _state.value = EditorState(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        type = note.type,
                        mood = note.mood,
                        dueDate = note.dueDate,
                        tags = note.tags,
                        color = note.color,
                        savedAt = note.updatedAt
                    )
                    undoStack.clear()
                    redoStack.clear()
                    undoStack.addLast(TextSnapshot(note.title, note.content))
                } else {
                    _state.value = EditorState(type = type)
                }
            }
        } else {
            _state.value = EditorState(type = type, content = initialContent ?: "")
        }
    }

    fun onTitleChange(title: String) {
        recordSnapshot()
        _state.value = _state.value.copy(title = title, savedAt = null, canUndo = undoStack.isNotEmpty())
        scheduleAutosave()
    }

    fun onContentChange(content: String) {
        recordSnapshot()
        _state.value = _state.value.copy(content = content, savedAt = null, canUndo = undoStack.isNotEmpty())
        scheduleAutosave()
    }

    fun onMoodChange(mood: String) {
        _state.value = _state.value.copy(mood = mood, savedAt = null)
        scheduleAutosave()
    }

    fun onDueDateChange(date: Long?) {
        _state.value = _state.value.copy(dueDate = date, savedAt = null)
        scheduleAutosave()
    }

    fun onTagsChange(tags: String) {
        _state.value = _state.value.copy(tags = tags, savedAt = null)
        scheduleAutosave()
    }

    fun onColorChange(color: String?) {
        _state.value = _state.value.copy(color = color, savedAt = null)
        scheduleAutosave()
    }

    fun undo() {
        if (undoStack.size < 2) return
        val currentSnap = TextSnapshot(_state.value.title, _state.value.content)
        redoStack.addLast(currentSnap)
        undoStack.removeLast() // discard current (just recorded)
        val prev = undoStack.last()
        _state.value = _state.value.copy(
            title = prev.title,
            content = prev.content,
            savedAt = null,
            canUndo = undoStack.size >= 2,
            canRedo = true
        )
        scheduleAutosave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentSnap = TextSnapshot(_state.value.title, _state.value.content)
        undoStack.addLast(currentSnap)
        val next = redoStack.removeLast()
        _state.value = _state.value.copy(
            title = next.title,
            content = next.content,
            savedAt = null,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
        scheduleAutosave()
    }

    fun saveNote() {
        val current = _state.value
        if (current.title.isBlank() && current.content.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val note = NoteEntity(
                id = current.id ?: 0,
                title = current.title,
                content = current.content,
                type = current.type,
                mood = current.mood,
                dueDate = current.dueDate,
                tags = current.tags,
                color = current.color
            )

            if (current.id != null) {
                repository.updateNote(note)
            } else {
                val newId = repository.createNote(note)
                _state.value = _state.value.copy(id = newId)
            }
            _state.value = _state.value.copy(isSaving = false, savedAt = System.currentTimeMillis())

            // Schedule or cancel the task reminder based on due date
            if (current.type == NoteType.TASK) {
                val savedId = _state.value.id ?: return@launch
                if (current.dueDate != null) {
                    alarmScheduler.scheduleReminder(savedId, current.title, current.dueDate)
                } else {
                    alarmScheduler.cancelReminder(savedId)
                }
            }
        }
    }

    private fun recordSnapshot() {
        val snap = TextSnapshot(_state.value.title, _state.value.content)
        if (undoStack.lastOrNull() != snap) {
            undoStack.addLast(snap)
            if (undoStack.size > 50) undoStack.removeFirst()
            redoStack.clear()
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800)
            saveNote()
        }
    }
}
