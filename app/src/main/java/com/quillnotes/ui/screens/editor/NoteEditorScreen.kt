package com.quillnotes.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.ui.components.MoodSelector
import com.quillnotes.ui.components.DatePickerButton
import com.quillnotes.ui.theme.noteColorPalette
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    noteType: String,
    initialContent: String? = null,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(noteId, noteType) {
        viewModel.loadNote(noteId, noteType, initialContent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (state.type) {
                                NoteType.NOTE -> if (state.id != null) "Edit Note" else "New Note"
                                NoteType.JOURNAL -> if (state.id != null) "Edit Entry" else "New Entry"
                                NoteType.TASK -> if (state.id != null) "Edit Task" else "New Task"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        SavedStatus(state)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Undo",
                            tint = if (state.canUndo)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "Redo",
                            tint = if (state.canRedo)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ── Title Field ────────────────────────────────────
            BasicTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                decorationBox = { innerTextField ->
                    if (state.title.isEmpty()) {
                        Text(
                            text = when (state.type) {
                                NoteType.NOTE -> "Title"
                                NoteType.JOURNAL -> "Today's entry..."
                                NoteType.TASK -> "Task name"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            )

            // ── Journal: Mood Selector ─────────────────────────
            if (state.type == NoteType.JOURNAL) {
                Spacer(modifier = Modifier.height(8.dp))
                MoodSelector(
                    selectedMood = state.mood,
                    onMoodSelected = viewModel::onMoodChange
                )
            }

            // ── Task: Due Date ─────────────────────────────────
            if (state.type == NoteType.TASK) {
                Spacer(modifier = Modifier.height(8.dp))
                DatePickerButton(
                    selectedDate = state.dueDate,
                    onDateSelected = viewModel::onDueDateChange
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // ── Content Field ──────────────────────────────────
            BasicTextField(
                value = state.content,
                onValueChange = viewModel::onContentChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 300.dp)
                    .focusRequester(contentFocusRequester),
                decorationBox = { innerTextField ->
                    if (state.content.isEmpty()) {
                        Text(
                            text = when (state.type) {
                                NoteType.NOTE -> "Start writing..."
                                NoteType.JOURNAL -> "How are you feeling today?"
                                NoteType.TASK -> "Add details..."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Color Label ──────────────────────────────────────
            ColorSwatchPicker(
                selectedColor = state.color,
                onColorSelected = viewModel::onColorChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tags ───────────────────────────────────────────
            OutlinedTextField(
                value = state.tags,
                onValueChange = viewModel::onTagsChange,
                label = { Text("Tags") },
                placeholder = { Text("work, personal, ideas...") },
                leadingIcon = { Icon(Icons.Outlined.Tag, "Tags") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ColorSwatchPicker(
    selectedColor: String?,
    onColorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Color",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorSwatch(
                color = null,
                isSelected = selectedColor == null,
                onClick = { onColorSelected(null) }
            )
            noteColorPalette.forEach { (key, color) ->
                ColorSwatch(
                    color = color,
                    isSelected = selectedColor == key,
                    onClick = { onColorSelected(key) }
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: androidx.compose.ui.graphics.Color?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "No color",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                modifier = Modifier.size(16.dp),
                tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun SavedStatus(state: EditorState) {
    val text = when {
        state.isSaving -> "Saving…"
        state.savedAt != null -> {
            val secondsAgo = (System.currentTimeMillis() - state.savedAt) / 1000
            when {
                secondsAgo < 5 -> "Saved"
                secondsAgo < 60 -> "Saved · ${secondsAgo}s ago"
                else -> {
                    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                    "Saved · ${fmt.format(Date(state.savedAt))}"
                }
            }
        }
        else -> null
    }

    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
