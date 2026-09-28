package com.quillnotes.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.ui.components.NoteCard
import com.quillnotes.ui.components.EmptyState

private val sortOptions = listOf(
    "updated" to "Newest first",
    "updated_asc" to "Oldest first",
    "title_asc" to "Title A–Z",
    "title_desc" to "Title Z–A"
)

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
    val sortOrder by viewModel.sortOrder.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

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

    LaunchedEffect(Unit) {
        viewModel.batchDeleteEvents.collect { deletedIds ->
            val result = snackbarHostState.showSnackbar(
                message = "${deletedIds.size} note(s) deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoBatchDelete(deletedIds)
            }
        }
    }

    LaunchedEffect(showSearch) {
        if (showSearch) searchFocusRequester.requestFocus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.pinSelected() }) {
                            Icon(Icons.Outlined.PushPin, contentDescription = "Pin or unpin selected")
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                placeholder = { Text("Search notes...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
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
                        if (showSearch) {
                            IconButton(onClick = {
                                showSearch = false
                                viewModel.onSearchQueryChange("")
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Close search")
                            }
                        } else {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search")
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Outlined.Sort, contentDescription = "Sort notes")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    sortOptions.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setSortOrder(key)
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (sortOrder == key) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                viewModel.setViewMode(if (viewMode == "grid") "list" else "grid")
                            }) {
                                Icon(
                                    imageVector = if (viewMode == "grid") Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                    contentDescription = if (viewMode == "grid") "Switch to list view" else "Switch to grid view"
                                )
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = onNewNote,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New Note")
                }
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
        } else if (viewMode == "grid") {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalItemSpacing = 8.dp,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = notes, key = { it.id }) { note ->
                    NoteCard(
                        title = note.title,
                        content = note.content,
                        updatedAt = note.updatedAt,
                        isPinned = note.isPinned,
                        color = note.color,
                        selectionMode = isSelectionMode,
                        isSelected = note.id in selectedIds,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleSelection(note.id) else onNoteClick(note.id)
                        },
                        onLongClick = {
                            if (isSelectionMode) viewModel.toggleSelection(note.id) else viewModel.startSelection(note.id)
                        },
                        onPin = { viewModel.togglePin(note.id, note.isPinned) },
                        onDelete = { viewModel.deleteNote(note.id) }
                    )
                }
            }
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
                    if (isSelectionMode) {
                        NoteCard(
                            title = note.title,
                            content = note.content,
                            updatedAt = note.updatedAt,
                            isPinned = note.isPinned,
                            color = note.color,
                            selectionMode = true,
                            isSelected = note.id in selectedIds,
                            onClick = { viewModel.toggleSelection(note.id) },
                            onLongClick = { viewModel.toggleSelection(note.id) },
                            onPin = { viewModel.togglePin(note.id, note.isPinned) },
                            onDelete = { viewModel.deleteNote(note.id) }
                        )
                    } else {
                        val dismissState = rememberDismissState(
                            confirmValueChange = { value ->
                                when (value) {
                                    DismissValue.DismissedToStart -> {
                                        viewModel.deleteNote(note.id)
                                        true
                                    }
                                    DismissValue.DismissedToEnd -> {
                                        viewModel.togglePin(note.id, note.isPinned)
                                        false // don't dismiss — reset below
                                    }
                                    else -> false
                                }
                            }
                        )
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == DismissValue.DismissedToEnd) {
                                dismissState.reset()
                            }
                        }

                        SwipeToDismiss(
                            state = dismissState,
                            background = {
                                SwipeBackground(
                                    direction = dismissState.dismissDirection,
                                    startIcon = Icons.Outlined.PushPin,
                                    startLabel = if (note.isPinned) "Unpin" else "Pin",
                                    startColor = MaterialTheme.colorScheme.secondaryContainer,
                                    endIcon = Icons.Outlined.Delete,
                                    endLabel = "Delete",
                                    endColor = MaterialTheme.colorScheme.errorContainer
                                )
                            },
                            dismissContent = {
                                NoteCard(
                                    title = note.title,
                                    content = note.content,
                                    updatedAt = note.updatedAt,
                                    isPinned = note.isPinned,
                                    color = note.color,
                                    onClick = { onNoteClick(note.id) },
                                    onLongClick = { viewModel.startSelection(note.id) },
                                    onPin = { viewModel.togglePin(note.id, note.isPinned) },
                                    onDelete = { viewModel.deleteNote(note.id) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeBackground(
    direction: DismissDirection?,
    startIcon: androidx.compose.ui.graphics.vector.ImageVector,
    startLabel: String,
    startColor: Color,
    endIcon: androidx.compose.ui.graphics.vector.ImageVector,
    endLabel: String,
    endColor: Color
) {
    if (direction == null) return
    val isStartToEnd = direction == DismissDirection.StartToEnd
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
