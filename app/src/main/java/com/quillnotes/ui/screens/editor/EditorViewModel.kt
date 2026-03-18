package com.quillnotes.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun loadNote(noteId: Long?, noteType: String) {
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
                        tags = note.tags
                    )
                } else {
                    _state.value = EditorState(type = type)
                }
            }
        } else {
            _state.value = EditorState(type = type)
        }
    }

    fun onTitleChange(title: String) {
        _state.value = _state.value.copy(title = title, isSaved = false)
    }

    fun onContentChange(content: String) {
        _state.value = _state.value.copy(content = content, isSaved = false)
    }

    fun onMoodChange(mood: String) {
        _state.value = _state.value.copy(mood = mood, isSaved = false)
    }

    fun onDueDateChange(date: Long?) {
        _state.value = _state.value.copy(dueDate = date, isSaved = false)
    }

    fun onTagsChange(tags: String) {
        _state.value = _state.value.copy(tags = tags, isSaved = false)
    }

    fun saveNote() {
        val current = _state.value
        if (current.title.isBlank() && current.content.isBlank()) return

        viewModelScope.launch {
            val note = NoteEntity(
                id = current.id ?: 0,
                title = current.title,
                content = current.content,
                type = current.type,
                mood = current.mood,
                dueDate = current.dueDate,
                tags = current.tags
            )

            if (current.id != null) {
                repository.updateNote(note)
            } else {
                val newId = repository.createNote(note)
                _state.value = _state.value.copy(id = newId)
            }
            _state.value = _state.value.copy(isSaved = true)
        }
    }
}
