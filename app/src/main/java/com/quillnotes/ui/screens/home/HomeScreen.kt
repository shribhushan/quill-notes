@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.quillnotes.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.ColorDot
import com.quillnotes.ui.components.DockClearance
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.NoteCard
import com.quillnotes.ui.components.QuillChip
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.ScreenHeader
import com.quillnotes.ui.components.SearchPill
import com.quillnotes.ui.components.SectionLabel
import com.quillnotes.ui.components.StatusBarScrim
import com.quillnotes.ui.components.SwipeAction
import com.quillnotes.ui.components.QuillSnackbar
import com.quillnotes.ui.components.QuillSwipeBox
import com.quillnotes.ui.components.trashSwipeAction
import com.quillnotes.ui.components.bleed
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.greeting
import com.quillnotes.ui.util.headerDate

val NoteSortOptions = listOf(
    "updated" to "Recently edited",
    "updated_asc" to "Oldest first",
    "title_asc" to "Title A–Z",
    "title_desc" to "Title Z–A"
)

/** Everything the home screen renders, so it can be previewed and screenshot-tested. */
data class HomeUiState(
    val notes: List<NoteEntity> = emptyList(),
    val query: String = "",
    val filter: NoteFilter = NoteFilter.All,
    val tags: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val sortOrder: String = "updated",
    val viewMode: String = "grid",
    val selectedIds: Set<Long> = emptySet(),
    val totalCount: Int = 0,
    val now: Long = System.currentTimeMillis()
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val isGrid: Boolean get() = viewMode == "grid"
}

class HomeActions(
    val onNoteClick: (Long) -> Unit = {},
    val onNoteLongClick: (Long) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (NoteFilter) -> Unit = {},
    val onSortChange: (String) -> Unit = {},
    val onToggleViewMode: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onTogglePin: (NoteEntity) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onPinSelected: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {}
)

@Composable
fun HomeScreen(
    onNoteClick: (Long) -> Unit,
    onNewNote: () -> Unit,
    onSettingsClick: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val colors by viewModel.usedColors.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val noteCount by viewModel.noteCount.collectAsState()
    val now = remember(notes) { System.currentTimeMillis() }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    LaunchedEffect(selectionMode) { onSelectionModeChange(selectionMode) }
    DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { id ->
            val result = snackbarHostState.showSnackbar(
                message = "Moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(id)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.batchDeleteEvents.collect { ids ->
            val result = snackbarHostState.showSnackbar(
                message = if (ids.size == 1) "1 note moved to trash" else "${ids.size} notes moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoBatchDelete(ids)
        }
    }

    HomeContent(
        state = HomeUiState(
            notes = notes,
            query = query,
            filter = filter,
            tags = tags,
            colors = colors,
            sortOrder = sortOrder,
            viewMode = viewMode,
            selectedIds = selectedIds,
            totalCount = noteCount,
            now = now
        ),
        actions = HomeActions(
            onNoteClick = { id -> if (selectionMode) viewModel.toggleSelection(id) else onNoteClick(id) },
            onNoteLongClick = { id -> if (selectionMode) viewModel.toggleSelection(id) else viewModel.startSelection(id) },
            onQueryChange = viewModel::onSearchQueryChange,
            onFilterChange = viewModel::setFilter,
            onSortChange = viewModel::setSortOrder,
            onToggleViewMode = { viewModel.setViewMode(if (viewMode == "grid") "list" else "grid") },
            onOpenSettings = onSettingsClick,
            onCreate = onNewNote,
            onDelete = viewModel::deleteNote,
            onTogglePin = { note -> viewModel.togglePin(note.id, note.isPinned) },
            onClearSelection = viewModel::clearSelection,
            onPinSelected = viewModel::pinSelected,
            onDeleteSelected = viewModel::deleteSelected
        ),
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pinned = remember(state.notes) { state.notes.filter { it.isPinned } }
    val others = remember(state.notes) { state.notes.filterNot { it.isPinned } }
    val sectioned = state.filter == NoteFilter.All && state.query.isBlank() &&
        pinned.isNotEmpty() && others.isNotEmpty()

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(if (state.isGrid) 2 else 1),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = statusTop + 8.dp,
                bottom = DockClearance + navBottom + 16.dp
            ),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                HomeHeader(state, actions)
            }
            item(key = "search", span = StaggeredGridItemSpan.FullLine) {
                SearchPill(
                    query = state.query,
                    onQueryChange = actions.onQueryChange,
                    placeholder = "Search your notes"
                )
            }
            if (state.tags.isNotEmpty() || state.colors.isNotEmpty() || state.filter != NoteFilter.All) {
                item(key = "filters", span = StaggeredGridItemSpan.FullLine) {
                    FilterRow(state, actions)
                }
            }
            when {
                state.notes.isEmpty() -> item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    HomeEmptyState(state, actions)
                }
                sectioned -> {
                    item(key = "label_pinned", span = StaggeredGridItemSpan.FullLine) {
                        SectionLabel("Pinned", count = pinned.size, padding = PaddingValues(top = 6.dp))
                    }
                    noteItems(pinned, state, actions)
                    item(key = "label_recent", span = StaggeredGridItemSpan.FullLine) {
                        SectionLabel("Recent", count = others.size, padding = PaddingValues(top = 10.dp))
                    }
                    noteItems(others, state, actions)
                }
                else -> {
                    item(key = "label_all", span = StaggeredGridItemSpan.FullLine) {
                        SectionLabel(
                            text = sectionTitle(state),
                            count = state.notes.size,
                            padding = PaddingValues(top = 6.dp)
                        )
                    }
                    noteItems(state.notes, state, actions)
                }
            }
        }

        StatusBarScrim()

        AnimatedVisibility(
            visible = state.isSelectionMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        ) {
            SelectionBar(
                count = state.selectedIds.size,
                allPinned = state.notes.filter { it.id in state.selectedIds }.let { sel ->
                    sel.isNotEmpty() && sel.all { it.isPinned }
                },
                onClose = actions.onClearSelection,
                onPin = actions.onPinSelected,
                onDelete = actions.onDeleteSelected
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DockClearance + navBottom - 8.dp)
                .padding(horizontal = 12.dp)
        ) { data -> QuillSnackbar(data) }
    }
}

private fun sectionTitle(state: HomeUiState): String = when {
    state.query.isNotBlank() -> "Results"
    else -> when (val f = state.filter) {
        NoteFilter.All -> "All notes"
        NoteFilter.Pinned -> "Pinned"
        is NoteFilter.Tag -> "#${f.name}"
        is NoteFilter.Color -> "${f.key.replaceFirstChar { it.uppercase() }} notes"
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, actions: HomeActions) {
    val count = state.totalCount
    ScreenHeader(
        title = "Notes",
        eyebrow = headerDate(state.now),
        subtitle = "${greeting(state.now)} · " + if (count == 1) "1 note" else "$count notes",
        actions = {
            SortButton(selected = state.sortOrder, onSelect = actions.onSortChange)
            CircleIconButton(
                icon = if (state.isGrid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                contentDescription = if (state.isGrid) "Show as list" else "Show as grid",
                onClick = actions.onToggleViewMode
            )
            CircleIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = actions.onOpenSettings
            )
        }
    )
}

@Composable
private fun SortButton(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(
            icon = Icons.Rounded.SwapVert,
            contentDescription = "Sort notes",
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NoteSortOptions.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    trailingIcon = {
                        if (key == selected) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterRow(state: HomeUiState, actions: HomeActions) {
    val quill = QuillTheme.colors
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = ScreenGutter),
        modifier = Modifier.fillMaxWidth().bleed(ScreenGutter)
    ) {
        item(key = "all") {
            QuillChip("All", state.filter == NoteFilter.All, onClick = { actions.onFilterChange(NoteFilter.All) })
        }
        item(key = "pinned") {
            val selected = state.filter == NoteFilter.Pinned
            QuillChip(
                label = "Pinned",
                selected = selected,
                onClick = { actions.onFilterChange(NoteFilter.Pinned) },
                leading = { Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(15.dp)) }
            )
        }
        items(state.colors, key = { "color_$it" }) { key ->
            val tone = quill.toneFor(key) ?: return@items
            QuillChip(
                label = key.replaceFirstChar { it.uppercase() },
                selected = state.filter == NoteFilter.Color(key),
                onClick = { actions.onFilterChange(NoteFilter.Color(key)) },
                leading = { ColorDot(tone.accent, size = 10.dp) }
            )
        }
        items(state.tags, key = { "tag_$it" }) { tag ->
            QuillChip(
                label = "#$tag",
                selected = state.filter == NoteFilter.Tag(tag),
                onClick = { actions.onFilterChange(NoteFilter.Tag(tag)) }
            )
        }
    }
}

private fun LazyStaggeredGridScope.noteItems(
    notes: List<NoteEntity>,
    state: HomeUiState,
    actions: HomeActions
) {
    items(notes, key = { it.id }) { note ->
        val card: @Composable () -> Unit = {
            NoteCard(
                note = note,
                onClick = { actions.onNoteClick(note.id) },
                onLongClick = { actions.onNoteLongClick(note.id) },
                selectionMode = state.isSelectionMode,
                isSelected = note.id in state.selectedIds,
                previewLines = if (state.isGrid) 7 else 3,
                now = state.now
            )
        }
        if (state.isGrid || state.isSelectionMode) {
            card()
        } else {
            SwipeableNote(
                note = note,
                onDelete = actions.onDelete,
                onTogglePin = actions.onTogglePin,
                content = card
            )
        }
    }
}

@Composable
private fun SwipeableNote(
    note: NoteEntity,
    onDelete: (Long) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    QuillSwipeBox(
        toEndAction = SwipeAction(
            icon = Icons.Rounded.PushPin,
            label = if (note.isPinned) "Unpin" else "Pin",
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        ),
        onSwipeToEnd = { onTogglePin(note) },
        toStartAction = trashSwipeAction(),
        onSwipeToStart = { onDelete(note.id) },
        content = content
    )
}

@Composable
private fun HomeEmptyState(state: HomeUiState, actions: HomeActions) {
    when {
        state.query.isNotBlank() -> EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = "Nothing matches",
            message = "No notes contain “${state.query.trim()}”. Try another word.",
            actionLabel = "Clear search",
            onAction = { actions.onQueryChange("") }
        )
        state.filter != NoteFilter.All -> EmptyState(
            icon = Icons.Rounded.FilterList,
            title = "Nothing here yet",
            message = "No notes match this filter.",
            actionLabel = "Show all notes",
            onAction = { actions.onFilterChange(NoteFilter.All) }
        )
        else -> EmptyState(
            icon = Icons.Rounded.EditNote,
            title = "A blank page",
            message = "Capture your first thought — ideas, lists, anything worth keeping.",
            actionLabel = "Write a note",
            onAction = actions.onCreate
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allPinned: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = quill.dockContainer,
        contentColor = quill.dockSelectedContainer,
        shadowElevation = 10.dp,
        modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(6.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Clear selection",
                onClick = onClose,
                containerColor = Color.Transparent,
                contentColor = quill.dockContent,
                size = 52.dp
            )
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, end = 20.dp)
            )
            CircleIconButton(
                icon = Icons.Rounded.PushPin,
                contentDescription = if (allPinned) "Unpin selected" else "Pin selected",
                onClick = onPin,
                containerColor = quill.dockContent.copy(alpha = 0.18f),
                contentColor = quill.dockSelectedContainer,
                size = 52.dp
            )
            Spacer(Modifier.width(6.dp))
            CircleIconButton(
                icon = Icons.Rounded.DeleteOutline,
                contentDescription = "Move selected to trash",
                onClick = onDelete,
                containerColor = scheme.error,
                contentColor = scheme.onError,
                size = 52.dp
            )
        }
    }
}
