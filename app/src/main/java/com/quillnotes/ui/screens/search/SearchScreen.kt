package com.quillnotes.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.R
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.domain.SearchHit
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.QuillChip
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.bleed
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.relativeTime
import com.quillnotes.ui.util.LocalTimeText

class SearchActions(
    val onBack: () -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onToggleType: (NoteType?) -> Unit = {},
    val onNotebook: (String?) -> Unit = {},
    val onTag: (String?) -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onOpen: (SearchHit) -> Unit = {}
)

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (id: Long, type: NoteType) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    SearchContent(
        state = state,
        actions = SearchActions(
            onBack = onBack,
            onTextChange = viewModel::onTextChange,
            onToggleType = viewModel::toggleType,
            onNotebook = viewModel::setNotebook,
            onTag = viewModel::setTag,
            onToggleArchived = viewModel::toggleArchived,
            onOpen = { hit -> onOpen(hit.note.id, hit.note.type) }
        ),
        autoFocus = true
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }
    val q = state.query

    Column(
        modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = actions.onBack
            )
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(scheme.surfaceVariant, CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.text.isEmpty()) {
                        Text(stringResource(R.string.search_everything), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = q.text,
                        onValueChange = actions.onTextChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (q.text.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { actions.onTextChange("") }
                            .padding(7.dp)
                    )
                }
            }
        }

        FilterChips(state, actions)

        val results = state.results
        if (results.isEmpty()) {
            val filtering = q.text.isNotBlank() || q.types.isNotEmpty() || q.notebook != null || q.tag != null
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(if (filtering) R.string.empty_nothing_matches else R.string.search_nothing_yet),
                    message = stringResource(if (filtering) R.string.search_try_another else R.string.search_nothing_yet_message),
                    actionLabel = null,
                    onAction = {}
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 4.dp, bottom = 24.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
            ) {
                item(key = "count") {
                    Text(
                        text = if (results.size == 1) stringResource(R.string.search_result_one) else stringResource(R.string.search_result_many, results.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(results, key = { it.note.id }) { hit ->
                    SearchResultRow(hit, state.now) { actions.onOpen(hit) }
                }
            }
        }
    }
}

@Composable
private fun FilterChips(state: SearchUiState, actions: SearchActions) {
    val q = state.query
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = ScreenGutter),
        modifier = Modifier.fillMaxWidth().bleed(ScreenGutter).padding(bottom = 8.dp)
    ) {
        item { QuillChip(stringResource(R.string.filter_all), q.types.isEmpty(), onClick = { actions.onToggleType(null) }) }
        item { QuillChip(stringResource(R.string.nav_notes), q.types == setOf(NoteType.NOTE), onClick = { actions.onToggleType(NoteType.NOTE) }) }
        item { QuillChip(stringResource(R.string.nav_journal), q.types == setOf(NoteType.JOURNAL), onClick = { actions.onToggleType(NoteType.JOURNAL) }) }
        item { QuillChip(stringResource(R.string.nav_planner), q.types == setOf(NoteType.TASK), onClick = { actions.onToggleType(NoteType.TASK) }) }
        if (state.notebooks.isNotEmpty()) {
            item { PickerChip(stringResource(R.string.search_notebook), q.notebook, state.notebooks, Icons.Rounded.Folder, actions.onNotebook) }
        }
        if (state.tags.isNotEmpty()) {
            item { PickerChip(stringResource(R.string.search_tag), q.tag, state.tags, null, actions.onTag, prefix = "#") }
        }
        item {
            QuillChip(
                label = stringResource(R.string.search_archived),
                selected = q.includeArchived,
                onClick = actions.onToggleArchived,
                leading = { Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(15.dp)) }
            )
        }
    }
}

/** A chip that opens a menu of choices; shows the chosen one and clears it when tapped again. */
@Composable
private fun PickerChip(
    label: String,
    selected: String?,
    options: List<String>,
    icon: ImageVector?,
    onSelect: (String?) -> Unit,
    prefix: String = ""
) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuillChip(
            label = if (selected != null) "$prefix$selected" else label,
            selected = selected != null,
            onClick = { if (selected != null) onSelect(null) else open = true },
            leading = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(15.dp)) } }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text("$prefix$option") }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, now: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val quill = QuillTheme.colors
    val note = hit.note
    val tone = quill.toneFor(note.color)
    val mark = SpanStyle(background = (tone?.accent ?: scheme.primary).copy(alpha = 0.28f), fontWeight = FontWeight.SemiBold)
    val icon = when (note.type) {
        NoteType.NOTE -> Icons.Rounded.StickyNote2
        NoteType.JOURNAL -> Icons.Rounded.AutoStories
        NoteType.TASK -> Icons.Rounded.CheckCircle
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(tone?.container ?: quill.card, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tone?.accent ?: scheme.onSurfaceVariant, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = note.title.ifBlank { stringResource(R.string.untitled) }
            Text(
                text = if (note.title.isBlank()) AnnotatedString(title) else highlighted(title, hit.titleMatches, mark),
                style = MaterialTheme.typography.titleMedium,
                color = tone?.content ?: scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (note.isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.locked_note_label), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            } else if (hit.snippet.isNotBlank()) {
                Text(
                    text = highlighted(hit.snippet, hit.snippetMatches, mark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = (tone?.content ?: scheme.onSurface).copy(alpha = 0.72f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(relativeTime(note.updatedAt, now, LocalTimeText.current), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                note.notebook?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (note.isArchived) {
                    Icon(Icons.Rounded.Archive, contentDescription = stringResource(R.string.section_archive), tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/** [text] with every range in [ranges] drawn in [style]; ranges outside the text are ignored. */
fun highlighted(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (r in ranges) {
        val start = r.first.coerceIn(0, text.length)
        val end = (r.last + 1).coerceIn(start, text.length)
        if (start < pos) continue
        append(text.substring(pos, start))
        withStyle(style) { append(text.substring(start, end)) }
        pos = end
    }
    if (pos < text.length) append(text.substring(pos))
}
