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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Unarchive
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
import com.quillnotes.ui.components.BottomFade
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.quillnotes.R
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.ui.screens.editor.NotebookDialog
import kotlinx.coroutines.launch
import com.quillnotes.ui.util.greetingRes
import com.quillnotes.ui.util.headerDate

val NoteSortOptions = listOf(
    "updated" to R.string.sort_recent,
    "updated_asc" to R.string.sort_oldest,
    "title_asc" to R.string.sort_title_az,
    "title_desc" to R.string.sort_title_za
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
    val notebooks: List<NotebookInfo> = emptyList(),
    val archivedCount: Int = 0,
    val covers: Map<String, AttachmentEntity> = emptyMap(),
    val templates: List<NoteEntity> = emptyList(),
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
    val onDeleteSelected: () -> Unit = {},
    val onArchiveSelected: () -> Unit = {},
    val onMoveSelected: (String?) -> Unit = {},
    val onToggleChecklist: (noteId: Long, lineIndex: Int) -> Unit = { _, _ -> },
    val onOpenSearch: () -> Unit = {},
    val onNewFromTemplate: (NoteEntity) -> Unit = {},
    val onNewFromText: (title: String, content: String) -> Unit = { _, _ -> },
    val onRenameNotebook: (from: String, to: String) -> Unit = { _, _ -> },
    val onDeleteNotebook: (String) -> Unit = {}
)

@Composable
fun HomeScreen(
    onNoteClick: (Long) -> Unit,
    onNewNote: () -> Unit,
    onSettingsClick: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit = {},
    onSearchClick: () -> Unit = {},
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
    val notebooks by viewModel.notebooks.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val covers by viewModel.covers.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val now = remember(notes) { System.currentTimeMillis() }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectionMode = selectedIds.isNotEmpty()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    LaunchedEffect(selectionMode) { onSelectionModeChange(selectionMode) }
    DisposableEffect(Unit) { onDispose { onSelectionModeChange(false) } }

    val movedToTrash = stringResource(R.string.moved_to_trash)
    val undo = stringResource(R.string.undo)
    val archivedMsg = stringResource(R.string.archived_notes)
    val unarchivedMsg = stringResource(R.string.unarchived_notes)
    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { id ->
            val result = snackbarHostState.showSnackbar(
                message = movedToTrash,
                actionLabel = undo,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(id)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.batchDeleteEvents.collect { ids ->
            val result = snackbarHostState.showSnackbar(
                message = movedToTrash,
                actionLabel = undo,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoBatchDelete(ids)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.archiveEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = if (event.archived) archivedMsg else unarchivedMsg,
                actionLabel = undo,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoArchive(event)
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
            notebooks = notebooks,
            archivedCount = archivedCount,
            covers = covers,
            templates = templates,
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
            onDeleteSelected = viewModel::deleteSelected,
            onArchiveSelected = viewModel::archiveSelected,
            onMoveSelected = viewModel::moveSelectedToNotebook,
            onToggleChecklist = viewModel::toggleChecklistItem,
            onOpenSearch = onSearchClick,
            onNewFromTemplate = { template -> scope.launch { onNoteClick(viewModel.createFromTemplate(template)) } },
            onNewFromText = { title, body -> scope.launch { onNoteClick(viewModel.createFromText(title, body)) } },
            onRenameNotebook = viewModel::renameNotebook,
            onDeleteNotebook = viewModel::deleteNotebook
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
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showMove by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val showFilters = state.tags.isNotEmpty() || state.colors.isNotEmpty() || state.notebooks.isNotEmpty() ||
        state.archivedCount > 0 || state.filter != NoteFilter.All

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
                HomeHeader(state, actions, onTemplates = { showTemplates = true })
            }
            item(key = "search", span = StaggeredGridItemSpan.FullLine) {
                // One search for everything lives on its own screen; this pill opens it.
                SearchEntryPill(placeholder = stringResource(R.string.search_everything), onClick = actions.onOpenSearch)
            }
            if (showFilters) {
                item(key = "filters", span = StaggeredGridItemSpan.FullLine) {
                    FilterRow(state, actions)
                }
            }
            (state.filter as? NoteFilter.Notebook)?.let { nb ->
                item(key = "notebook_banner", span = StaggeredGridItemSpan.FullLine) {
                    NotebookBanner(
                        name = nb.name,
                        count = state.notebooks.firstOrNull { it.name.equals(nb.name, ignoreCase = true) }?.count ?: state.notes.size,
                        onRename = { renaming = true },
                        onDelete = { deleting = true }
                    )
                }
            }
            when {
                state.notes.isEmpty() -> item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    HomeEmptyState(state, actions, onTemplates = { showTemplates = true })
                }
                sectioned -> {
                    item(key = "label_pinned", span = StaggeredGridItemSpan.FullLine) {
                        SectionLabel(stringResource(R.string.section_pinned), count = pinned.size, padding = PaddingValues(top = 6.dp))
                    }
                    noteItems(pinned, state, actions)
                    item(key = "label_recent", span = StaggeredGridItemSpan.FullLine) {
                        SectionLabel(stringResource(R.string.section_recent), count = others.size, padding = PaddingValues(top = 10.dp))
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
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomFade {
                val selection = state.notes.filter { it.id in state.selectedIds }
                SelectionBar(
                    count = state.selectedIds.size,
                    allPinned = selection.isNotEmpty() && selection.all { it.isPinned },
                    allArchived = selection.isNotEmpty() && selection.all { it.isArchived },
                    onClose = actions.onClearSelection,
                    onPin = actions.onPinSelected,
                    onArchive = actions.onArchiveSelected,
                    onMove = { showMove = true },
                    onDelete = actions.onDeleteSelected
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DockClearance + navBottom - 8.dp)
                .padding(horizontal = 12.dp)
        ) { data -> QuillSnackbar(data) }
    }

    if (showTemplates) {
        TemplateSheet(
            templates = state.templates,
            onDismiss = { showTemplates = false },
            onPickSaved = { showTemplates = false; actions.onNewFromTemplate(it) },
            onPickBuiltIn = { title, body -> showTemplates = false; actions.onNewFromText(title, body) }
        )
    }
    if (showMove) {
        NotebookDialog(
            current = null,
            notebooks = state.notebooks.map { it.name },
            onSelect = { showMove = false; actions.onMoveSelected(it) },
            onDismiss = { showMove = false }
        )
    }
    val activeNotebook = (state.filter as? NoteFilter.Notebook)?.name
    if (renaming && activeNotebook != null) {
        RenameNotebookDialog(
            current = activeNotebook,
            onConfirm = { renaming = false; actions.onRenameNotebook(activeNotebook, it) },
            onDismiss = { renaming = false }
        )
    }
    if (deleting && activeNotebook != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.notebook_delete_title, activeNotebook)) },
            text = { Text(stringResource(R.string.notebook_delete_message)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; actions.onDeleteNotebook(activeNotebook) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun sectionTitle(state: HomeUiState): String = when {
    state.query.isNotBlank() -> stringResource(R.string.section_results)
    else -> when (val f = state.filter) {
        NoteFilter.All -> stringResource(R.string.section_all_notes)
        NoteFilter.Pinned -> stringResource(R.string.section_pinned)
        NoteFilter.Archived -> stringResource(R.string.section_archive)
        is NoteFilter.Tag -> "#${f.name}"
        is NoteFilter.Color -> stringResource(R.string.section_color_notes, f.key.replaceFirstChar { it.uppercase() })
        is NoteFilter.Notebook -> f.name
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, actions: HomeActions, onTemplates: () -> Unit) {
    val count = state.totalCount
    ScreenHeader(
        title = stringResource(R.string.nav_notes),
        eyebrow = headerDate(state.now),
        subtitle = "${stringResource(greetingRes(state.now))} · " +
            if (count == 1) stringResource(R.string.note_count_one) else stringResource(R.string.note_count_many, count),
        actions = {
            SortButton(
                selected = state.sortOrder,
                isGrid = state.isGrid,
                onSelect = actions.onSortChange,
                onToggleViewMode = actions.onToggleViewMode
            )
            CircleIconButton(
                icon = Icons.Rounded.PostAdd,
                contentDescription = stringResource(R.string.new_from_template),
                onClick = onTemplates
            )
            CircleIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings),
                onClick = actions.onOpenSettings
            )
        }
    )
}

/** Sort order and grid/list in one menu, to keep the header uncrowded. */
@Composable
private fun SortButton(selected: String, isGrid: Boolean, onSelect: (String) -> Unit, onToggleViewMode: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(
            icon = Icons.Rounded.SwapVert,
            contentDescription = stringResource(R.string.sort_and_view),
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NoteSortOptions.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    trailingIcon = {
                        if (key == selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (isGrid) R.string.show_as_list else R.string.show_as_grid), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(if (isGrid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, contentDescription = null) },
                onClick = {
                    expanded = false
                    onToggleViewMode()
                }
            )
        }
    }
}

/** Looks like the old search field but opens the search screen, which covers notes, journal and tasks. */
@Composable
private fun SearchEntryPill(placeholder: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
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
            QuillChip(stringResource(R.string.filter_all), state.filter == NoteFilter.All, onClick = { actions.onFilterChange(NoteFilter.All) })
        }
        item(key = "pinned") {
            val selected = state.filter == NoteFilter.Pinned
            QuillChip(
                label = stringResource(R.string.section_pinned),
                selected = selected,
                onClick = { actions.onFilterChange(NoteFilter.Pinned) },
                leading = { Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(15.dp)) }
            )
        }
        items(state.notebooks, key = { "notebook_${it.name}" }) { nb ->
            QuillChip(
                label = nb.name,
                selected = (state.filter as? NoteFilter.Notebook)?.name.equals(nb.name, ignoreCase = true),
                onClick = { actions.onFilterChange(NoteFilter.Notebook(nb.name)) },
                leading = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(15.dp)) }
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
        if (state.archivedCount > 0 || state.filter == NoteFilter.Archived) {
            item(key = "archive") {
                QuillChip(
                    label = stringResource(R.string.section_archive),
                    selected = state.filter == NoteFilter.Archived,
                    onClick = { actions.onFilterChange(NoteFilter.Archived) },
                    leading = { Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
            }
        }
    }
}

/** Shown above a notebook's notes: its name, size, and rename / delete. */
@Composable
private fun NotebookBanner(name: String, count: Int, onRename: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1)
            Text(
                if (count == 1) stringResource(R.string.note_count_one) else stringResource(R.string.note_count_many, count),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.notebook_rename),
            onClick = onRename,
            containerColor = Color.Transparent
        )
        CircleIconButton(
            icon = Icons.Rounded.Delete,
            contentDescription = stringResource(R.string.notebook_delete),
            onClick = onDelete,
            containerColor = Color.Transparent
        )
    }
}

@Composable
private fun RenameNotebookDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.notebook_new_name)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Built-in starting points (written in the app's language) and the user's own templates. */
@Composable
private fun TemplateSheet(
    templates: List<NoteEntity>,
    onDismiss: () -> Unit,
    onPickSaved: (NoteEntity) -> Unit,
    onPickBuiltIn: (title: String, body: String) -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val builtIn = listOf(
        stringResource(R.string.template_meeting_title) to stringResource(R.string.template_meeting_body),
        stringResource(R.string.template_checklist_title) to stringResource(R.string.template_checklist_body),
        stringResource(R.string.template_weekly_title) to stringResource(R.string.template_weekly_body),
        stringResource(R.string.template_book_title) to stringResource(R.string.template_book_body)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenGutter)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.new_from_template), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            builtIn.forEach { (title, body) -> TemplateRow(title, null) { onPickBuiltIn(title, body) } }
            if (templates.isNotEmpty()) {
                SectionLabel(stringResource(R.string.your_templates), padding = PaddingValues(top = 18.dp, bottom = 6.dp))
                templates.forEach { t ->
                    TemplateRow(t.title.ifBlank { stringResource(R.string.untitled) }, null) { onPickSaved(t) }
                }
            } else {
                Text(
                    stringResource(R.string.templates_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TemplateRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                now = state.now,
                cover = state.covers[note.uuid],
                onToggleChecklist = if (state.isSelectionMode) null else { line -> actions.onToggleChecklist(note.id, line) }
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
            label = stringResource(if (note.isPinned) R.string.unpin else R.string.pin),
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
private fun HomeEmptyState(state: HomeUiState, actions: HomeActions, onTemplates: () -> Unit) {
    when {
        state.query.isNotBlank() -> EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.empty_nothing_matches),
            message = stringResource(R.string.empty_no_notes_contain, state.query.trim()),
            actionLabel = stringResource(R.string.clear_search),
            onAction = { actions.onQueryChange("") }
        )
        state.filter == NoteFilter.Archived -> EmptyState(
            icon = Icons.Rounded.Archive,
            title = stringResource(R.string.empty_archive_title),
            message = stringResource(R.string.empty_archive_message),
            actionLabel = stringResource(R.string.show_all_notes),
            onAction = { actions.onFilterChange(NoteFilter.All) }
        )
        state.filter != NoteFilter.All -> EmptyState(
            icon = Icons.Rounded.FilterList,
            title = stringResource(R.string.empty_filter_title),
            message = stringResource(R.string.empty_filter_message),
            actionLabel = stringResource(R.string.show_all_notes),
            onAction = { actions.onFilterChange(NoteFilter.All) }
        )
        else -> EmptyState(
            icon = Icons.Rounded.EditNote,
            title = stringResource(R.string.empty_notes_title),
            message = stringResource(R.string.empty_notes_message),
            actionLabel = stringResource(R.string.write_a_note),
            onAction = actions.onCreate
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allPinned: Boolean,
    allArchived: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = quill.dockContainer,
        contentColor = quill.dockSelectedContainer,
        shadowElevation = 10.dp,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(5.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.clear_selection),
                onClick = onClose,
                containerColor = Color.Transparent,
                contentColor = quill.dockContent,
                size = 44.dp
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 2.dp, end = 14.dp)
            )
            CircleIconButton(
                icon = Icons.Rounded.PushPin,
                contentDescription = stringResource(if (allPinned) R.string.unpin_selected else R.string.pin_selected),
                onClick = onPin,
                containerColor = quill.dockContent.copy(alpha = 0.18f),
                contentColor = quill.dockSelectedContainer,
                size = 44.dp
            )
            Spacer(Modifier.width(6.dp))
            CircleIconButton(
                icon = if (allArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                contentDescription = stringResource(if (allArchived) R.string.unarchive_selected else R.string.archive_selected),
                onClick = onArchive,
                containerColor = quill.dockContent.copy(alpha = 0.18f),
                contentColor = quill.dockSelectedContainer,
                size = 44.dp
            )
            Spacer(Modifier.width(6.dp))
            CircleIconButton(
                icon = Icons.Rounded.Folder,
                contentDescription = stringResource(R.string.move_selected),
                onClick = onMove,
                containerColor = quill.dockContent.copy(alpha = 0.18f),
                contentColor = quill.dockSelectedContainer,
                size = 44.dp
            )
            Spacer(Modifier.width(6.dp))
            CircleIconButton(
                icon = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.trash_selected),
                onClick = onDelete,
                containerColor = scheme.error,
                contentColor = scheme.onError,
                size = 44.dp
            )
        }
    }
}
