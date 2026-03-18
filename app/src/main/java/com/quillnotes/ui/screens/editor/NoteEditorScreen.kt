package com.quillnotes.ui.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.ui.components.MoodSelector
import com.quillnotes.ui.components.DatePickerButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    noteType: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(noteId, noteType) {
        viewModel.loadNote(noteId, noteType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.type) {
                            NoteType.NOTE -> if (state.id != null) "Edit Note" else "New Note"
                            NoteType.JOURNAL -> if (state.id != null) "Edit Entry" else "New Entry"
                            NoteType.TASK -> if (state.id != null) "Edit Task" else "New Task"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveNote()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveNote()
                    }) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Save",
                            tint = if (state.isSaved)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
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
