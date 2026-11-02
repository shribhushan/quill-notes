@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.quillnotes.ui.screens.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.DatePickerButton
import com.quillnotes.ui.components.MoodSelector
import com.quillnotes.ui.components.QuillCheck
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.theme.NoteColorKeys
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.dayLabel
import com.quillnotes.ui.util.joinTags
import com.quillnotes.ui.util.parseTags
import com.quillnotes.ui.util.readingMinutes
import com.quillnotes.ui.util.relativeTime
import com.quillnotes.ui.util.startOfDay
import com.quillnotes.ui.util.timeOfDay
import com.quillnotes.ui.util.wordCount

class EditorActions(
    val onBack: () -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onContentChange: (String) -> Unit = {},
    val onMoodChange: (String) -> Unit = {},
    val onDueDateChange: (Long?) -> Unit = {},
    val onTagsChange: (String) -> Unit = {},
    val onColorChange: (String?) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onToggleCompleted: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onDelete: () -> Unit = {}
)

@Composable
fun NoteEditorScreen(
    noteId: Long?,
    noteType: String,
    initialContent: String? = null,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(noteId, noteType) {
        viewModel.loadNote(noteId, noteType, initialContent)
    }

    val leave = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = leave)

    EditorContent(
        state = state,
        actions = EditorActions(
            onBack = leave,
            onTitleChange = viewModel::onTitleChange,
            onContentChange = viewModel::onContentChange,
            onMoodChange = viewModel::onMoodChange,
            onDueDateChange = viewModel::onDueDateChange,
            onTagsChange = viewModel::onTagsChange,
            onColorChange = viewModel::onColorChange,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onTogglePin = viewModel::togglePin,
            onToggleCompleted = viewModel::toggleCompleted,
            onShare = {
                val text = listOf(state.title, state.content).filter { it.isNotBlank() }.joinToString("\n\n")
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, state.title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            onDelete = {
                viewModel.moveToTrash()
                onBack()
            }
        ),
        autoFocus = noteId == null || noteId <= 0
    )
}

@Composable
fun EditorContent(
    state: EditorState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    now: Long = System.currentTimeMillis()
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val tone = quill.toneFor(state.color)
    val background by animateColorAsState(tone?.container ?: scheme.background, label = "editorBg")
    val ink by animateColorAsState(tone?.content ?: scheme.onBackground, label = "editorInk")
    val accent = tone?.accent ?: scheme.primary
    val titleFocus = remember { FocusRequester() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(autoFocus, state.isLoading) {
        if (autoFocus && !state.isLoading && state.title.isEmpty()) titleFocus.requestFocus()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .imePadding()
    ) {
        EditorTopBar(state, actions, ink, onRequestDelete = { confirmDelete = true })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenGutter)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = eyebrowFor(state, now).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = ink.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(10.dp))

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
                    DatePickerButton(
                        selectedDate = state.dueDate,
                        onDateSelected = actions.onDueDateChange,
                        contentColor = ink
                    )
                }
                NoteType.NOTE -> Unit
            }

            Spacer(Modifier.height(18.dp))
            BasicTextField(
                value = state.content,
                onValueChange = actions.onContentChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink.copy(alpha = 0.88f), lineHeight = 27.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 220.dp),
                decorationBox = { inner ->
                    Box {
                        if (state.content.isEmpty()) {
                            Text(
                                text = when (state.type) {
                                    NoteType.NOTE -> "Start writing…"
                                    NoteType.JOURNAL -> "What's on your mind today?"
                                    NoteType.TASK -> "Add details, links or sub-steps…"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = ink.copy(alpha = 0.38f)
                            )
                        }
                        inner()
                    }
                }
            )

            Spacer(Modifier.height(20.dp))
            TagEditor(
                tags = state.tags,
                onTagsChange = actions.onTagsChange,
                ink = ink
            )
            Spacer(Modifier.height(32.dp))
        }

        EditorBottomBar(
            state = state,
            ink = ink,
            onColorChange = actions.onColorChange
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move to trash?") },
            text = { Text("You can restore it from Trash for 30 days.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text("Move to trash", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun eyebrowFor(state: EditorState, now: Long): String {
    val kind = when (state.type) {
        NoteType.NOTE -> "Note"
        NoteType.JOURNAL -> "Journal"
        NoteType.TASK -> "Task"
    }
    val created = state.createdAt
    val detail = when {
        state.type == NoteType.JOURNAL -> {
            val at = created ?: now
            "${dayLabel(startOfDay(at), now)} · ${timeOfDay(at)}"
        }
        state.id == null -> "New"
        state.savedAt != null -> "Edited ${relativeTime(state.savedAt, now).lowercase()}"
        else -> "Editing"
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
                            NoteType.NOTE -> "Title"
                            NoteType.JOURNAL -> "Give today a title"
                            NoteType.TASK -> "What needs doing?"
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
    onRequestDelete: () -> Unit
) {
    val chip = ink.copy(alpha = 0.07f)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
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
                contentDescription = "Undo",
                onClick = actions.onUndo,
                enabled = state.canUndo,
                containerColor = Color.Transparent,
                contentColor = ink
            )
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.Redo,
                contentDescription = "Redo",
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
                contentDescription = "More options",
                onClick = { menuOpen = true },
                containerColor = chip,
                contentColor = ink
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    enabled = !state.isEmpty,
                    onClick = {
                        menuOpen = false
                        actions.onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
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
private fun PinButton(pinned: Boolean, ink: Color, chip: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (pinned) 0f else 35f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pinRotation"
    )
    CircleIconButton(
        icon = if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
        contentDescription = if (pinned) "Unpin" else "Pin",
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
        state.isSaving -> "Saving…"
        state.savedAt != null -> "Saved"
        state.id == null && state.isEmpty -> null
        else -> "Editing"
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
                    Icon(Icons.Rounded.Close, contentDescription = "Remove tag $tag", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
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
                                Text("tag", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.35f))
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
                Text("Add tag", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun EditorBottomBar(state: EditorState, ink: Color, onColorChange: (String?) -> Unit) {
    val quill = QuillTheme.colors
    val words = remember(state.title, state.content) { wordCount(state.content) + wordCount(state.title) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.08f)))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = ScreenGutter, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorOption(
                    fill = Color.Transparent,
                    ring = ink.copy(alpha = 0.3f),
                    selected = state.color == null,
                    label = "No colour",
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
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (words == 1) "1 word" else "$words words",
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.7f)
                )
                if (words > 0) {
                    Text(
                        text = "${readingMinutes(words)} min read",
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.45f)
                    )
                }
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
