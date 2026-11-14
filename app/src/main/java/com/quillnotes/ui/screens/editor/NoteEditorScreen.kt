@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.quillnotes.ui.screens.editor

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.R
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.domain.LinkInfo
import com.quillnotes.text.Markdown
import com.quillnotes.text.MarkdownEditing
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.ImageViewerDialog
import com.quillnotes.ui.components.MoodSelector
import com.quillnotes.ui.components.QuillCheck
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.authenticateForNote
import com.quillnotes.ui.components.canAuthenticate
import com.quillnotes.ui.components.rememberNotificationPermissionRequest
import com.quillnotes.ui.theme.NoteColorKeys
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.dayLabel
import com.quillnotes.ui.util.dueLabel
import com.quillnotes.ui.util.joinTags
import com.quillnotes.ui.util.parseTags
import com.quillnotes.ui.util.readingMinutes
import com.quillnotes.ui.util.relativePhrase
import com.quillnotes.ui.util.startOfDay
import com.quillnotes.ui.util.timeOfDay
import com.quillnotes.ui.util.wordCount
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class EditorActions(
    val onBack: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onContentChange: (String) -> Unit = {},
    val onMoodChange: (String) -> Unit = {},
    val onDueDateChange: (Long?) -> Unit = {},
    val onRepeatChange: (String?) -> Unit = {},
    val onPriorityChange: (Int) -> Unit = {},
    val onTagsChange: (String) -> Unit = {},
    val onNotebookChange: (String?) -> Unit = {},
    val onColorChange: (String?) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onToggleCompleted: () -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onToggleLocked: () -> Unit = {},
    val onSaveTemplate: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenNote: (Long) -> Unit = {},
    val onCreateLinked: (String) -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
    val onGallery: () -> Unit = {},
    val onCamera: () -> Unit = {},
    val onVoice: () -> Unit = {},
    val onScanGallery: () -> Unit = {},
    val onScanCamera: () -> Unit = {},
    val onUnlock: () -> Unit = {}
)

@Composable
fun NoteEditorScreen(
    noteId: Long?,
    noteType: String,
    initialContent: String? = null,
    startVoice: Boolean = false,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val links by viewModel.links.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fieldState = remember { MarkdownFieldState() }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(noteId, noteType) {
        viewModel.loadNote(noteId, noteType, initialContent)
    }

    val requestNotifications = rememberNotificationPermissionRequest()
    val leave = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = leave)

    // ── Locked notes ──
    val authTitle = stringResource(R.string.locked_note_title)
    val authSubtitle = stringResource(R.string.locked_note_prompt)
    val unlock = {
        if (!canAuthenticate(context)) unlocked = true // nothing to check against: cannot enforce the lock
        else authenticateForNote(context, authTitle, authSubtitle, onSuccess = { unlocked = true })
    }
    val locked = state.isLocked && !unlocked && !state.isLoading
    LaunchedEffect(locked) { if (locked) unlock() }

    // ── Pictures, voice and scanning ──
    val insertScanned: (Uri) -> Unit = { uri ->
        scope.launch {
            viewModel.scanText(uri)?.let { fieldState.insertAtCaret(it, viewModel::onContentChange, newLine = true) }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }
    val pickScan = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) insertScanned(uri)
    }
    var pendingCapture by rememberSaveable { mutableStateOf<Uri?>(null) }
    var captureIsScan by rememberSaveable { mutableStateOf(false) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture
        if (ok && uri != null) {
            if (captureIsScan) insertScanned(uri) else viewModel.attachImage(uri)
        }
    }
    fun capture(forScan: Boolean) {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCapture = uri
        captureIsScan = forScan
        takePicture.launch(uri)
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !heard.isNullOrBlank()) {
            fieldState.insertAtCaret(heard, viewModel::onContentChange)
        }
    }
    val startVoiceInput: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // on-device recognition where the phone has it
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
        }
        try {
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.msg_voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(startVoice, state.isLoading) {
        if (startVoice && !voiceStarted && !state.isLoading) {
            voiceStarted = true
            startVoiceInput()
        }
    }

    EditorContent(
        state = state,
        actions = EditorActions(
            onBack = leave,
            onTitleChange = viewModel::onTitleChange,
            onContentChange = viewModel::onContentChange,
            onMoodChange = viewModel::onMoodChange,
            onDueDateChange = { due ->
                if (due != null) requestNotifications()
                viewModel.onDueDateChange(due)
            },
            onRepeatChange = viewModel::onRepeatChange,
            onPriorityChange = viewModel::onPriorityChange,
            onTagsChange = viewModel::onTagsChange,
            onNotebookChange = viewModel::onNotebookChange,
            onColorChange = viewModel::onColorChange,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onTogglePin = viewModel::togglePin,
            onToggleCompleted = viewModel::toggleCompleted,
            onToggleArchived = viewModel::toggleArchived,
            onToggleLocked = {
                if (!state.isLocked && !canAuthenticate(context)) {
                    Toast.makeText(context, R.string.msg_lock_needs_screen_lock, Toast.LENGTH_LONG).show()
                } else {
                    unlocked = true // the person who just locked it is looking at it
                    viewModel.toggleLocked()
                }
            },
            onSaveTemplate = viewModel::saveAsTemplate,
            onShare = {
                val text = listOf(state.title, Markdown.toPlain(state.content, Markdown.PlainStyle.SHARE))
                    .filter { it.isNotBlank() }.joinToString("\n\n")
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, state.title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            onExport = {
                scope.launch {
                    val file = viewModel.buildExport(context.cacheDir) ?: return@launch
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = if (file.extension == "zip") "application/zip" else "text/markdown"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            },
            onDelete = {
                viewModel.moveToTrash()
                onBack()
            },
            onOpenNote = { id ->
                viewModel.flush()
                onOpenNote(id)
            },
            onCreateLinked = { title ->
                // A link to a note that does not exist yet: make it, then go there.
                viewModel.flush()
                scope.launch { viewModel.createLinkedNote(title)?.let(onOpenNote) }
            },
            onRemoveAttachment = viewModel::removeAttachment,
            onGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCamera = { capture(forScan = false) },
            onVoice = startVoiceInput,
            onScanGallery = { pickScan.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onScanCamera = { capture(forScan = true) },
            onUnlock = unlock
        ),
        autoFocus = noteId == null || noteId <= 0,
        links = links,
        notebooks = notebooks,
        fieldState = fieldState,
        locked = locked
    )
}

@Composable
fun EditorContent(
    state: EditorState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    now: Long = System.currentTimeMillis(),
    links: LinkInfo = LinkInfo(),
    notebooks: List<String> = emptyList(),
    fieldState: MarkdownFieldState = remember { MarkdownFieldState(state.content) },
    locked: Boolean = false
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val tone = quill.toneFor(state.color)
    val background by animateColorAsState(tone?.container ?: scheme.background, label = "editorBg")
    val ink by animateColorAsState(tone?.content ?: scheme.onBackground, label = "editorInk")
    val accent = tone?.accent ?: scheme.primary
    val titleFocus = remember { FocusRequester() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var notebookDialog by rememberSaveable { mutableStateOf(false) }
    var scanChoice by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var linkDialog by remember { mutableStateOf<LinkEdit?>(null) }
    val uriHandler = LocalUriHandler.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(autoFocus, state.isLoading) {
        if (autoFocus && !state.isLoading && state.title.isEmpty()) titleFocus.requestFocus()
    }

    Box(modifier.fillMaxSize().background(background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            EditorTopBar(state, actions, ink, onRequestDelete = { confirmDelete = true }, onMoveNotebook = { notebookDialog = true })

            if (locked) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LockedNotePlaceholder(ink = ink, onUnlock = actions.onUnlock)
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenGutter)
                ) {
                    Spacer(Modifier.height(4.dp))
                    val words = remember(state.title, state.content) { wordCount(state.content) + wordCount(state.title) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = eyebrowFor(state, now).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                            color = ink.copy(alpha = 0.6f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (words > 0) {
                            Text(
                                text = stringResource(if (words == 1) R.string.word_count_one else R.string.word_count_many, words) +
                                    " · " + stringResource(R.string.reading_minutes, readingMinutes(words)),
                                style = MaterialTheme.typography.labelMedium,
                                color = ink.copy(alpha = 0.5f)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (state.isArchived) {
                        ArchivedBanner(ink = ink, onRestore = actions.onToggleArchived)
                        Spacer(Modifier.height(10.dp))
                    }

                    if (state.type == NoteType.TASK) {
                        Row(verticalAlignment = Alignment.Top) {
                            QuillCheck(
                                checked = state.isCompleted,
                                onCheckedChange = actions.onToggleCompleted,
                                accent = accent,
                                modifier = Modifier.offset(x = (-9).dp)
                            )
                            TitleField(state, actions, ink, accent, titleFocus, Modifier.weight(1f).padding(top = 2.dp))
                        }
                    } else {
                        TitleField(state, actions, ink, accent, titleFocus, Modifier.fillMaxWidth())
                    }

                    when (state.type) {
                        NoteType.JOURNAL -> {
                            Spacer(Modifier.height(18.dp))
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = ink.copy(alpha = 0.04f),
                                border = BorderStroke(1.dp, ink.copy(alpha = 0.08f))
                            ) {
                                MoodSelector(
                                    selectedMood = state.mood,
                                    onMoodSelected = actions.onMoodChange,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                                )
                            }
                        }
                        NoteType.TASK -> {
                            Spacer(Modifier.height(14.dp))
                            TaskOptions(state, actions, ink)
                        }
                        NoteType.NOTE -> Unit
                    }

                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotebookChip(state.notebook, ink) { notebookDialog = true }
                    }
                    Spacer(Modifier.height(10.dp))
                    TagEditor(
                        tags = state.tags,
                        onTagsChange = actions.onTagsChange,
                        ink = ink
                    )

                    if (state.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        AttachmentStrip(
                            attachments = state.attachments,
                            ink = ink,
                            onOpen = { viewing = it.uuid },
                            onRemove = { actions.onRemoveAttachment(it.uuid) }
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    MarkdownField(
                        state = fieldState,
                        text = state.content,
                        onTextChange = actions.onContentChange,
                        ink = ink,
                        accent = accent,
                        placeholder = when (state.type) {
                            NoteType.NOTE -> stringResource(R.string.placeholder_content)
                            NoteType.JOURNAL -> stringResource(R.string.placeholder_journal_content)
                            NoteType.TASK -> stringResource(R.string.placeholder_task_content)
                        }
                    )

                    LinksSection(links = links, ink = ink, onOpen = actions.onOpenNote, onCreate = actions.onCreateLinked)

                    Spacer(Modifier.height(32.dp))
                }

                // Pill with the outcome of an action.
                AnimatedVisibility(
                    visible = state.message != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    state.message?.let { m ->
                        EditorMessagePill(messageText(m, state.messageDate, now), Modifier.padding(bottom = 10.dp))
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))
                FormatBar(
                    actions = FormatActions(
                        onBold = { fieldState.edit(actions.onContentChange) { MarkdownEditing.toggleWrap(it, "**") } },
                        onItalic = { fieldState.edit(actions.onContentChange) { MarkdownEditing.toggleWrap(it, "_") } },
                        onHeading = { fieldState.edit(actions.onContentChange) { MarkdownEditing.cycleHeading(it) } },
                        onBullets = { fieldState.edit(actions.onContentChange) { MarkdownEditing.toggleBullet(it) } },
                        onNumbered = { fieldState.edit(actions.onContentChange) { MarkdownEditing.toggleNumbered(it) } },
                        onChecklist = { fieldState.edit(actions.onContentChange) { MarkdownEditing.toggleChecklist(it) } },
                        onIndent = { fieldState.edit(actions.onContentChange) { MarkdownEditing.changeIndent(it, 1) } },
                        onOutdent = { fieldState.edit(actions.onContentChange) { MarkdownEditing.changeIndent(it, -1) } },
                        onLink = {
                            val existing = fieldState.linkAtCaret()
                            linkDialog = if (existing != null) {
                                val label = fieldState.text.substring(existing.contentStart, existing.contentEnd)
                                LinkEdit(label, existing.target.orEmpty(), existing.start, existing.end)
                            } else {
                                LinkEdit(fieldState.selectedText(), "", null, null)
                            }
                        },
                        onGallery = actions.onGallery,
                        onCamera = actions.onCamera,
                        onVoice = actions.onVoice,
                        onScan = { scanChoice = true }
                    ),
                    ink = ink
                )
                // The colour dots take room the keyboard needs, so they hide while typing.
                if (!imeVisible) {
                    EditorBottomBar(state = state, ink = ink, onColorChange = actions.onColorChange)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trash_confirm_title)) },
            text = { Text(stringResource(R.string.trash_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.menu_trash), color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (notebookDialog) {
        NotebookDialog(
            current = state.notebook,
            notebooks = notebooks,
            onSelect = {
                notebookDialog = false
                actions.onNotebookChange(it)
            },
            onDismiss = { notebookDialog = false }
        )
    }

    if (scanChoice) {
        AlertDialog(
            onDismissRequest = { scanChoice = false },
            title = { Text(stringResource(R.string.scan_title)) },
            text = { Text(stringResource(R.string.scan_message)) },
            confirmButton = {
                TextButton(onClick = { scanChoice = false; actions.onScanCamera() }) { Text(stringResource(R.string.scan_camera)) }
            },
            dismissButton = {
                TextButton(onClick = { scanChoice = false; actions.onScanGallery() }) { Text(stringResource(R.string.scan_gallery)) }
            }
        )
    }

    linkDialog?.let { edit ->
        LinkDialog(
            initialLabel = edit.label,
            initialUrl = edit.url,
            onConfirm = { label, url ->
                linkDialog = null
                fieldState.edit(actions.onContentChange) { ts ->
                    if (edit.start != null && edit.end != null) {
                        MarkdownEditing.replaceRange(ts, edit.start, edit.end, "[${label.ifBlank { url }}]($url)")
                    } else {
                        MarkdownEditing.insertLink(ts, label.ifBlank { null }, url)
                    }
                }
            },
            onDismiss = { linkDialog = null },
            onOpen = { url ->
                runCatching { uriHandler.openUri(url) }
            }
        )
    }

    viewing?.let { uuid -> ImageViewerDialog(uuid = uuid, onDismiss = { viewing = null }) }
}

/** The link being added or edited in [LinkDialog]; [start]/[end] are set when editing an existing one. */
private data class LinkEdit(val label: String, val url: String, val start: Int?, val end: Int?)

@Composable
private fun messageText(message: EditorMessage, date: Long?, now: Long): String = when (message) {
    EditorMessage.SAVED_AS_TEMPLATE -> stringResource(R.string.msg_saved_template)
    EditorMessage.ATTACHMENT_FAILED -> stringResource(R.string.msg_attach_failed)
    EditorMessage.NEXT_ROUND -> stringResource(R.string.msg_next_round, dueLabel(date ?: now, now))
    EditorMessage.SCAN_EMPTY -> stringResource(R.string.msg_scan_empty)
    EditorMessage.EXPORT_FAILED -> stringResource(R.string.msg_export_failed)
}

@Composable
private fun eyebrowFor(state: EditorState, now: Long): String {
    val kind = when (state.type) {
        NoteType.NOTE -> stringResource(R.string.kind_note)
        NoteType.JOURNAL -> stringResource(R.string.kind_journal)
        NoteType.TASK -> stringResource(R.string.kind_task)
    }
    val created = state.createdAt
    val detail = when {
        state.type == NoteType.JOURNAL -> {
            val at = created ?: now
            "${dayLabel(startOfDay(at), now)} · ${timeOfDay(at)}"
        }
        state.id == null -> stringResource(R.string.eyebrow_new)
        state.savedAt != null -> stringResource(R.string.eyebrow_edited, relativePhrase(state.savedAt, now))
        else -> stringResource(R.string.eyebrow_editing)
    }
    return "$kind · $detail"
}

@Composable
private fun TitleField(
    state: EditorState,
    actions: EditorActions,
    ink: Color,
    accent: Color,
    focusRequester: FocusRequester,
    modifier: Modifier
) {
    val done = state.type == NoteType.TASK && state.isCompleted
    val style = MaterialTheme.typography.headlineLarge.copy(
        color = if (done) ink.copy(alpha = 0.5f) else ink,
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
    )
    BasicTextField(
        value = state.title,
        onValueChange = actions.onTitleChange,
        textStyle = style,
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = { inner ->
            Box {
                if (state.title.isEmpty()) {
                    Text(
                        text = when (state.type) {
                            NoteType.NOTE -> stringResource(R.string.placeholder_title)
                            NoteType.JOURNAL -> stringResource(R.string.placeholder_journal_title)
                            NoteType.TASK -> stringResource(R.string.placeholder_task_title)
                        },
                        style = style.copy(color = ink.copy(alpha = 0.3f), textDecoration = TextDecoration.None)
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun EditorTopBar(
    state: EditorState,
    actions: EditorActions,
    ink: Color,
    onRequestDelete: () -> Unit,
    onMoveNotebook: () -> Unit
) {
    val chip = ink.copy(alpha = 0.07f)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = actions.onBack,
            containerColor = chip,
            contentColor = ink
        )
        Spacer(Modifier.width(10.dp))
        SaveIndicator(state, ink)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.clip(CircleShape).background(chip),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.Undo,
                contentDescription = stringResource(R.string.undo),
                onClick = actions.onUndo,
                enabled = state.canUndo,
                containerColor = Color.Transparent,
                contentColor = ink
            )
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.Redo,
                contentDescription = stringResource(R.string.redo),
                onClick = actions.onRedo,
                enabled = state.canRedo,
                containerColor = Color.Transparent,
                contentColor = ink
            )
        }
        if (state.type == NoteType.NOTE) {
            Spacer(Modifier.width(8.dp))
            PinButton(pinned = state.isPinned, ink = ink, chip = chip, onClick = actions.onTogglePin)
        }
        Spacer(Modifier.width(8.dp))
        Box {
            CircleIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                onClick = { menuOpen = true },
                containerColor = chip,
                contentColor = ink
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem(Icons.Rounded.Share, R.string.menu_share, enabled = !state.isEmpty) { menuOpen = false; actions.onShare() }
                MenuItem(Icons.Rounded.FileDownload, R.string.menu_export_markdown, enabled = !state.isEmpty) { menuOpen = false; actions.onExport() }
                if (state.type == NoteType.NOTE) {
                    MenuItem(Icons.Rounded.Folder, R.string.menu_move_notebook) { menuOpen = false; onMoveNotebook() }
                    MenuItem(
                        if (state.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        if (state.isArchived) R.string.unarchive else R.string.menu_archive,
                        enabled = state.id != null
                    ) { menuOpen = false; actions.onToggleArchived() }
                }
                MenuItem(
                    if (state.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    if (state.isLocked) R.string.menu_unlock else R.string.menu_lock
                ) { menuOpen = false; actions.onToggleLocked() }
                MenuItem(Icons.Rounded.PostAdd, R.string.menu_save_template, enabled = !state.isEmpty) { menuOpen = false; actions.onSaveTemplate() }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_trash), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        menuOpen = false
                        if (state.id == null) actions.onBack() else onRequestDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun PinButton(pinned: Boolean, ink: Color, chip: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (pinned) 0f else 35f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pinRotation"
    )
    CircleIconButton(
        icon = if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
        contentDescription = stringResource(if (pinned) R.string.unpin else R.string.pin),
        onClick = onClick,
        containerColor = if (pinned) scheme.primary else chip,
        contentColor = if (pinned) scheme.onPrimary else ink,
        modifier = Modifier.graphicsLayer { rotationZ = rotation }
    )
}

@Composable
private fun SaveIndicator(state: EditorState, ink: Color) {
    val label = when {
        state.isLoading -> null
        state.isSaving -> stringResource(R.string.saving)
        state.savedAt != null -> stringResource(R.string.saved)
        state.id == null && state.isEmpty -> null
        else -> stringResource(R.string.eyebrow_editing)
    }
    AnimatedVisibility(visible = label != null, enter = fadeIn(), exit = fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.savedAt != null && !state.isSaving) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label.orEmpty(), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun TagEditor(tags: String, onTagsChange: (String) -> Unit, ink: Color) {
    val list = remember(tags) { parseTags(tags) }
    var draft by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun commit() {
        val newTags = parseTags(draft)
        if (newTags.isNotEmpty()) {
            onTagsChange(joinTags((list + newTags).distinctBy { it.lowercase() }))
        }
        draft = ""
    }

    LaunchedEffect(adding) { if (adding) focus.requestFocus() }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        list.forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.07f))
                    .padding(start = 12.dp, end = 4.dp)
            ) {
                Text("#$tag", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.85f))
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onTagsChange(joinTags(list - tag)) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tag_remove, tag), tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                }
            }
        }
        if (adding) {
            BasicTextField(
                value = draft,
                onValueChange = { value ->
                    if (value.endsWith(",") || value.endsWith(" ")) {
                        draft = value
                        commit()
                    } else {
                        draft = value
                    }
                },
                singleLine = true,
                textStyle = TextStyle.Default.merge(MaterialTheme.typography.labelLarge).copy(color = ink),
                cursorBrush = SolidColor(ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    commit()
                    adding = false
                }),
                modifier = Modifier
                    .height(34.dp)
                    .widthIn(min = 96.dp, max = 200.dp)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, ink.copy(alpha = 0.3f), CircleShape)
                            .padding(horizontal = 12.dp)
                            .height(34.dp)
                    ) {
                        Text("#", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.5f))
                        Box(Modifier.padding(start = 2.dp)) {
                            if (draft.isEmpty()) {
                                Text(stringResource(R.string.tag_hint), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.35f))
                            }
                            inner()
                        }
                    }
                }
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, ink.copy(alpha = 0.18f), CircleShape)
                    .clickable { adding = true }
                    .padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tag_add), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun EditorBottomBar(state: EditorState, ink: Color, onColorChange: (String?) -> Unit) {
    val quill = QuillTheme.colors
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorOption(
                fill = Color.Transparent,
                ring = ink.copy(alpha = 0.3f),
                selected = state.color == null,
                label = stringResource(R.string.color_none),
                ink = ink,
                onClick = { onColorChange(null) },
                icon = true
            )
            NoteColorKeys.forEach { key ->
                val tone = quill.toneFor(key) ?: return@forEach
                ColorOption(
                    fill = tone.accent,
                    ring = tone.accent,
                    selected = state.color == key,
                    label = key.replaceFirstChar { it.uppercase() },
                    ink = ink,
                    onClick = { onColorChange(key) }
                )
            }
        }
    }
}

@Composable
private fun ColorOption(
    fill: Color,
    ring: Color,
    selected: Boolean,
    label: String,
    ink: Color,
    onClick: () -> Unit,
    icon: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.78f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "colorOptionScale"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(fill)
                .border(if (selected) 3.dp else 1.5.dp, if (selected) ink.copy(alpha = 0.85f) else ring, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon) {
                Icon(Icons.Rounded.FormatColorReset, contentDescription = label, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            } else if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
