package com.quillnotes.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.notifications.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val isPinned: Boolean = false,
    val isCompleted: Boolean = false,
    val createdAt: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedAt: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val isEmpty: Boolean get() = title.isBlank() && content.isBlank()
}

private data class TextSnapshot(val title: String, val content: String)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** The entity as last read from / written to the database, so saves keep fields the editor doesn't own. */
    private var persisted: NoteEntity? = null

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var lastSnapshotAt = 0L
    private var autosaveJob: Job? = null
    private var dirty = false
    private var loadedKey: String? = null
    private val saveMutex = Mutex()

    fun loadNote(noteId: Long?, noteType: String, initialContent: String? = null) {
        val key = "$noteId/$noteType"
        if (loadedKey == key) return // survive recomposition / configuration change
        loadedKey = key

        val type = when (noteType) {
            "journal" -> NoteType.JOURNAL
            "task" -> NoteType.TASK
            else -> NoteType.NOTE
        }

        if (noteId != null && noteId > 0) {
            _state.value = EditorState(type = type, isLoading = true)
            viewModelScope.launch {
                val note = repository.getNoteById(noteId)
                persisted = note
                _state.value = note?.toState() ?: EditorState(type = type)
                undoStack.clear()
                redoStack.clear()
            }
        } else {
            _state.value = EditorState(type = type, content = initialContent.orEmpty())
            if (!initialContent.isNullOrBlank()) markDirty()
        }
    }

    fun onTitleChange(title: String) = editText(title = title)

    fun onContentChange(content: String) = editText(content = content)

    fun onMoodChange(mood: String) = edit { copy(mood = if (mood == this.mood) null else mood) }

    fun onDueDateChange(date: Long?) = edit { copy(dueDate = date) }

    fun onTagsChange(tags: String) = edit { copy(tags = tags) }

    fun onColorChange(color: String?) = edit { copy(color = color) }

    fun togglePin() = edit { copy(isPinned = !isPinned) }

    fun toggleCompleted() = edit { copy(isCompleted = !isCompleted) }

    fun undo() {
        val current = _state.value
        var previous = undoStack.removeLastOrNull() ?: return
        val currentSnap = TextSnapshot(current.title, current.content)
        if (previous == currentSnap) previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(currentSnap)
        lastSnapshotAt = 0L
        _state.value = current.copy(
            title = previous.title,
            content = previous.content,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
        markDirty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val current = _state.value
        undoStack.addLast(TextSnapshot(current.title, current.content))
        lastSnapshotAt = 0L
        _state.value = current.copy(
            title = next.title,
            content = next.content,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
        markDirty()
    }

    /** Persist pending edits right away (used when leaving the editor). */
    fun flush() {
        autosaveJob?.cancel()
        if (dirty) persist()
    }

    /** Soft-deletes the note (it can be restored from Trash). */
    fun moveToTrash() {
        autosaveJob?.cancel()
        dirty = false
        viewModelScope.launch(NonCancellable) {
            saveMutex.withLock {
                val id = _state.value.id ?: return@withLock
                repository.deleteNote(id)
                alarmScheduler.cancelReminder(id)
            }
        }
    }

    override fun onCleared() {
        // Back navigation can clear the ViewModel before the debounced autosave fires.
        if (dirty) {
            autosaveJob?.cancel()
            persist()
        }
        super.onCleared()
    }

    private fun editText(title: String? = null, content: String? = null) {
        val current = _state.value
        recordSnapshot(current)
        _state.value = current.copy(
            title = title ?: current.title,
            content = content ?: current.content,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
        markDirty()
    }

    private inline fun edit(transform: EditorState.() -> EditorState) {
        _state.value = _state.value.transform()
        markDirty()
    }

    private fun markDirty() {
        dirty = true
        _state.value = _state.value.copy(savedAt = null)
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800)
            persist()
        }
    }

    /** Groups bursts of typing into a single undo step. */
    private fun recordSnapshot(current: EditorState) {
        val now = System.currentTimeMillis()
        val snap = TextSnapshot(current.title, current.content)
        redoStack.clear()
        if (undoStack.isNotEmpty() && now - lastSnapshotAt < 1_000L) return
        if (undoStack.lastOrNull() != snap) {
            undoStack.addLast(snap)
            if (undoStack.size > 100) undoStack.removeFirst()
        }
        lastSnapshotAt = now
    }

    /**
     * Writes the current state. Runs detached from [viewModelScope]'s cancellation so a save
     * that starts as the screen closes still completes; the mutex keeps a new note from being
     * inserted twice when an autosave and a flush overlap.
     */
    private fun persist() {
        dirty = false
        viewModelScope.launch(NonCancellable) {
            saveMutex.withLock {
                val current = _state.value
                if (current.isEmpty && current.id == null) return@withLock
                _state.value = _state.value.copy(isSaving = true)

                val base = persisted ?: NoteEntity(title = "", content = "", type = current.type)
                val note = base.copy(
                    id = current.id ?: 0,
                    title = current.title,
                    content = current.content,
                    type = current.type,
                    mood = current.mood,
                    dueDate = current.dueDate,
                    tags = current.tags,
                    color = current.color,
                    isPinned = current.isPinned,
                    isCompleted = current.isCompleted,
                    isSynced = false
                )

                val savedId = if (current.id != null) {
                    repository.updateNote(note)
                    current.id
                } else {
                    repository.createNote(note)
                }
                val now = System.currentTimeMillis()
                persisted = note.copy(id = savedId, updatedAt = now)
                _state.value = _state.value.copy(
                    id = savedId,
                    createdAt = _state.value.createdAt ?: note.createdAt,
                    isSaving = false,
                    savedAt = if (dirty) null else now
                )

                if (current.type == NoteType.TASK) {
                    val due = current.dueDate
                    if (due != null && !current.isCompleted && due > now) {
                        alarmScheduler.scheduleReminder(savedId, current.title, due)
                    } else {
                        alarmScheduler.cancelReminder(savedId)
                    }
                }
            }
        }
    }

    private fun NoteEntity.toState() = EditorState(
        id = id,
        title = title,
        content = content,
        type = type,
        mood = mood,
        dueDate = dueDate,
        tags = tags,
        color = color,
        isPinned = isPinned,
        isCompleted = isCompleted,
        createdAt = createdAt,
        savedAt = updatedAt
    )
}
