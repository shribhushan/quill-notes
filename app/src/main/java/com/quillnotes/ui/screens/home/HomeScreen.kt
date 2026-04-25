package com.quillnotes.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.ui.components.NoteCard
import com.quillnotes.ui.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNoteClick: (Long) -> Unit,
    onNewNote: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val noteCount by viewModel.noteCount.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { deletedId ->
            val result = snackbarHostState.showSnackbar(
                message = "Note deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(deletedId)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search notes...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Column {
                            Text("Notes", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "$noteCount notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewNote,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Note")
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                title = if (searchQuery.isBlank()) "No notes yet" else "No results",
                subtitle = if (searchQuery.isBlank())
                    "Tap + to create your first note"
                else
                    "Try a different search term"
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = notes,
                    key = { it.id }
                ) { note ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    viewModel.deleteNote(note.id)
                                    true
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    scope.launch {
                                        viewModel.togglePin(note.id, note.isPinned)
                                        dismissState.reset()
                                    }
                                    false // don't dismiss — reset after pin
                                }
                                else -> false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            SwipeBackground(
                                direction = dismissState.dismissDirection,
                                startIcon = Icons.Outlined.PushPin,
                                startLabel = if (note.isPinned) "Unpin" else "Pin",
                                startColor = MaterialTheme.colorScheme.secondaryContainer,
                                endIcon = Icons.Outlined.Delete,
                                endLabel = "Delete",
                                endColor = MaterialTheme.colorScheme.errorContainer
                            )
                        }
                    ) {
                        NoteCard(
                            title = note.title,
                            content = note.content,
                            updatedAt = note.updatedAt,
                            isPinned = note.isPinned,
                            onClick = { onNoteClick(note.id) },
                            onPin = { viewModel.togglePin(note.id, note.isPinned) },
                            onDelete = { viewModel.deleteNote(note.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeBackground(
    direction: SwipeToDismissBoxValue?,
    startIcon: androidx.compose.ui.graphics.vector.ImageVector,
    startLabel: String,
    startColor: Color,
    endIcon: androidx.compose.ui.graphics.vector.ImageVector,
    endLabel: String,
    endColor: Color
) {
    if (direction == null || direction == SwipeToDismissBoxValue.Settled) return
    val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
    val color = if (isStartToEnd) startColor else endColor
    val icon = if (isStartToEnd) startIcon else endIcon
    val label = if (isStartToEnd) startLabel else endLabel
    val arrangement = if (isStartToEnd) Arrangement.Start else Arrangement.End

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
