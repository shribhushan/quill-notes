package com.quillnotes.ui.screens.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quillnotes.data.export.NoteExporter
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.data.media.ImageImporter
import com.quillnotes.data.media.TextScanner
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.data.sync.NotePayload
import com.quillnotes.data.sync.ReminderSink
import com.quillnotes.data.sync.toRef
import com.quillnotes.domain.LinkInfo
import com.quillnotes.domain.NoteLinks
import com.quillnotes.domain.RepeatRule
import com.quillnotes.text.Checklist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** One-off notices the editor shows briefly; the screen turns them into (translated) text. */
enum class EditorMessage { SAVED_AS_TEMPLATE, ATTACHMENT_FAILED, NEXT_ROUND, SCAN_EMPTY, EXPORT_FAILED }

data class EditorState(
    val id: Long? = null,
    /** Stable identity (also the key for attachments); known before the note is first saved. */
    val uuid: String = "",
    val title: String = "",
    val content: String = "",
    val type: NoteType = NoteType.NOTE,
    val mood: String? = null,
    val dueDate: Long? = null,
    val repeatRule: String? = null,
    val priority: Int = 0,
    val tags: String = "",
    val notebook: String? = null,
    val color: String? = null,
    val isPinned: Boolean = false,
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val isTemplate: Boolean = false,
    val attachments: List<AttachmentEntity> = emptyList(),
    val createdAt: Long? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedAt: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Set briefly after something worth telling the user about (see [EditorMessage]). */
    val message: EditorMessage? = null,
    /** For [EditorMessage.NEXT_ROUND]: the date the recurring task moved to. */
    val messageDate: Long? = null
) {
    val isEmpty: Boolean get() = title.isBlank() && content.isBlank() && attachments.isEmpty()
}

private data class TextSnapshot(val title: String, val content: String)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val reminders: ReminderSink,
    private val imageImporter: ImageImporter,
    private val textScanner: TextScanner,
    prefs: PreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Links to and from this note, kept current as the text changes. */
    private val linkIndex = repository.getAllNotes()
        .map { NoteLinks.index(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteLinks.index(emptyList()))

    val links: StateFlow<LinkInfo> = combine(
        _state.map { Triple(it.id, it.title, it.content) }.distinctUntilChanged(),
        linkIndex
    ) { (id, title, content), index -> NoteLinks.info(index, id, title, content) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkInfo())

    /** Every notebook the user could file this note under. */
    val notebooks: StateFlow<List<String>> = combine(prefs.notebookNames, repository.getQuickNotes()) { saved, notes ->
        (saved + notes.mapNotNull { it.notebook?.takeIf { n -> n.isNotBlank() } })
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The entity as last read from / written to the database, so saves keep fields the editor doesn't own. */
    private var persisted: NoteEntity? = null

    private val undoStack = ArrayDeque<TextSnapshot>()
    private val redoStack = ArrayDeque<TextSnapshot>()
    private var lastSnapshotAt = 0L
    private var autosaveJob: Job? = null
    private var attachmentsJob: Job? = null
    private var messageJob: Job? = null
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
                _state.value = note?.toState() ?: EditorState(type = type, uuid = UUID.randomUUID().toString())
                note?.let { observeAttachments(it.uuid) }
                undoStack.clear()
                redoStack.clear()
            }
        } else {
            val uuid = UUID.randomUUID().toString()
            _state.value = EditorState(type = type, uuid = uuid, content = initialContent.orEmpty())
            observeAttachments(uuid)
            if (!initialContent.isNullOrBlank()) markDirty()
        }
    }

    /** Starts a new note from a template's text (used by "new from template"). */
    fun startFrom(template: NoteEntity) {
        _state.update {
            it.copy(
                title = template.title, content = template.content, tags = template.tags, color = template.color,
                notebook = template.notebook, priority = template.priority, repeatRule = template.repeatRule
            )
        }
        markDirty()
    }

    private fun observeAttachments(uuid: String) {
        attachmentsJob?.cancel()
        attachmentsJob = viewModelScope.launch {
            repository.observeAttachments(uuid).collect { list -> _state.update { it.copy(attachments = list) } }
        }
    }

    fun onTitleChange(title: String) = editText(title = title)

    fun onContentChange(content: String) = editText(content = content)

    fun onMoodChange(mood: String) = edit { copy(mood = if (mood == this.mood) null else mood) }

    fun onDueDateChange(date: Long?) = edit { copy(dueDate = date, repeatRule = if (date == null) null else repeatRule) }

    fun onRepeatChange(code: String?) = edit { copy(repeatRule = code) }

    fun onPriorityChange(level: Int) = edit { copy(priority = level.coerceIn(0, 3)) }

    fun onTagsChange(tags: String) = edit { copy(tags = tags) }

    fun onNotebookChange(name: String?) = edit { copy(notebook = name?.trim()?.takeIf { it.isNotEmpty() }) }

    fun onColorChange(color: String?) = edit { copy(color = color) }

    fun togglePin() = edit { copy(isPinned = !isPinned) }

    fun toggleArchived() = edit { copy(isArchived = !isArchived) }

    fun toggleLocked() = edit { copy(isLocked = !isLocked) }

    /**
     * Completing a recurring task starts its next round (next due date, sub-items unticked)
     * rather than finishing it; everything else just flips.
     */
    fun toggleCompleted() {
        val s = _state.value
        val rule = RepeatRule.parse(s.repeatRule)
        val due = s.dueDate
        if (!s.isCompleted && rule != null && due != null) {
            val next = rule.nextAfter(due, System.currentTimeMillis())
            edit { copy(dueDate = next, isCompleted = false, content = Checklist.resetAll(content)) }
            showMessage(EditorMessage.NEXT_ROUND, next)
        } else {
            edit { copy(isCompleted = !isCompleted) }
        }
    }

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
                reminders.cancel(id)
            }
        }
    }

    // ── Attachments ────────────────────────────────────────────

    /** Adds a photo from the gallery or camera. The note is saved first so the image has a home. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val image = imageImporter.process(uri)
            if (image == null) {
                showMessage(EditorMessage.ATTACHMENT_FAILED)
                return@launch
            }
            saveIfNew()
            repository.addAttachment(_state.value.uuid, image.bytes, image.mime, image.width, image.height)
        }
    }

    fun removeAttachment(attachmentUuid: String) {
        viewModelScope.launch { repository.removeAttachment(_state.value.uuid, attachmentUuid) }
    }

    /** OCR of a photo of a document; the caller decides where the text goes. Null if nothing was found. */
    suspend fun scanText(uri: Uri): String? {
        val text = textScanner.recognize(uri)
        if (text == null) showMessage(EditorMessage.SCAN_EMPTY)
        return text
    }

    /** Creates the note a `[[wiki link]]` points at when none exists yet, and returns its id. */
    suspend fun createLinkedNote(title: String): Long? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        return repository.createNote(NoteEntity(title = clean, content = "", type = NoteType.NOTE))
    }

    // ── Templates and export ───────────────────────────────────

    /** Keeps a copy of this note as a template for "new from template". */
    fun saveAsTemplate() {
        val s = _state.value
        viewModelScope.launch {
            repository.createNote(
                NoteEntity(
                    title = s.title, content = s.content, type = s.type, tags = s.tags, color = s.color,
                    notebook = s.notebook, priority = s.priority, repeatRule = s.repeatRule, isTemplate = true
                )
            )
            showMessage(EditorMessage.SAVED_AS_TEMPLATE)
        }
    }

    /** The note as a shareable file: Markdown, or a zip with its images when it has any. Null on failure. */
    suspend fun buildExport(cacheDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val s = _state.value
            val payload = s.toPayload()
            val dir = File(cacheDir, "exported").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
            val taken = HashSet<String>()
            if (payload.attachments.isEmpty()) {
                File(dir, NoteExporter.fileName(payload, taken)).also {
                    it.writeText(NoteExporter.markdown(payload))
                }
            } else {
                File(dir, NoteExporter.fileName(payload, taken, "zip")).also { file ->
                    file.outputStream().use { out ->
                        NoteExporter.writeZip(out, listOf(payload)) { repository.readAttachmentBlocking(it) }
                    }
                }
            }
        } catch (e: Exception) {
            showMessage(EditorMessage.EXPORT_FAILED)
            null
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

    // ── Internals ──────────────────────────────────────────────

    private fun showMessage(message: EditorMessage, date: Long? = null) {
        _state.update { it.copy(message = message, messageDate = date) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(3_500)
            _state.update { it.copy(message = null, messageDate = null) }
        }
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
            saveMutex.withLock { saveLocked(force = false) }
        }
    }

    /** A new note that gets an image before any text still has to exist for the image to belong to. */
    private suspend fun saveIfNew() {
        autosaveJob?.cancel()
        saveMutex.withLock {
            if (_state.value.id == null) saveLocked(force = true) else if (dirty) saveLocked(force = false)
        }
    }

    private suspend fun saveLocked(force: Boolean) {
        dirty = false
        val current = _state.value
        if (current.isEmpty && current.id == null && !force) return
        _state.value = _state.value.copy(isSaving = true)

        val base = persisted ?: NoteEntity(uuid = current.uuid.ifBlank { UUID.randomUUID().toString() }, title = "", content = "", type = current.type)
        val note = base.copy(
            id = current.id ?: 0,
            title = current.title,
            content = current.content,
            type = current.type,
            mood = current.mood,
            dueDate = current.dueDate,
            repeatRule = current.repeatRule,
            priority = current.priority,
            tags = current.tags,
            notebook = current.notebook,
            color = current.color,
            isPinned = current.isPinned,
            isCompleted = current.isCompleted,
            isArchived = current.isArchived,
            isLocked = current.isLocked,
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
                reminders.schedule(savedId, current.title, due)
            } else {
                reminders.cancel(savedId)
            }
        }
    }

    private fun NoteEntity.toState() = EditorState(
        id = id,
        uuid = uuid,
        title = title,
        content = content,
        type = type,
        mood = mood,
        dueDate = dueDate,
        repeatRule = repeatRule,
        priority = priority,
        tags = tags,
        notebook = notebook,
        color = color,
        isPinned = isPinned,
        isCompleted = isCompleted,
        isArchived = isArchived,
        isLocked = isLocked,
        isTemplate = isTemplate,
        createdAt = createdAt,
        savedAt = updatedAt
    )

    private fun EditorState.toPayload(): NotePayload = NotePayload(
        uuid = uuid.ifBlank { "note" }, title = title, content = content, type = type.name,
        createdAt = createdAt ?: System.currentTimeMillis(), updatedAt = savedAt ?: System.currentTimeMillis(),
        isPinned = isPinned, isCompleted = isCompleted, dueDate = dueDate, mood = mood, tags = tags, color = color,
        priority = priority, repeatRule = repeatRule, notebook = notebook, isArchived = isArchived,
        isTemplate = isTemplate, isLocked = isLocked, attachments = attachments.map { it.toRef() }
    )
}
