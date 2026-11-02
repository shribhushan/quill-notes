@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.quillnotes.ui.screens.trash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.IconBadge
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.ScreenHeader
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.relativePhrase

private const val RETENTION_DAYS = 30
private const val DAY_MS = 24 * 60 * 60 * 1000L

class TrashActions(
    val onBack: () -> Unit = {},
    val onRestore: (Long) -> Unit = {},
    val onDeleteForever: (Long) -> Unit = {},
    val onEmptyTrash: () -> Unit = {}
)

@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val notes by viewModel.deletedNotes.collectAsState()
    TrashContent(
        notes = notes,
        actions = TrashActions(
            onBack = onBack,
            onRestore = viewModel::restoreNote,
            onDeleteForever = viewModel::deleteForever,
            onEmptyTrash = viewModel::emptyTrash
        )
    )
}

@Composable
fun TrashContent(
    notes: List<NoteEntity>,
    actions: TrashActions,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis()
) {
    val scheme = MaterialTheme.colorScheme
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(scheme.background),
        contentPadding = PaddingValues(
            start = ScreenGutter,
            end = ScreenGutter,
            top = statusTop + 8.dp,
            bottom = navBottom + 32.dp
        )
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "Trash",
                eyebrow = if (notes.isEmpty()) "Empty" else if (notes.size == 1) "1 item" else "${notes.size} items",
                subtitle = "Items are removed for good after $RETENTION_DAYS days",
                leading = {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        onClick = actions.onBack
                    )
                },
                actions = {
                    if (notes.isNotEmpty()) {
                        CircleIconButton(
                            icon = Icons.Rounded.DeleteSweep,
                            contentDescription = "Empty trash",
                            onClick = { confirmEmpty = true },
                            containerColor = scheme.errorContainer,
                            contentColor = scheme.onErrorContainer
                        )
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
        }

        if (notes.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Nothing in the trash",
                    message = "Notes you delete wait here for $RETENTION_DAYS days, in case you change your mind."
                )
            }
        } else {
            items(notes, key = { it.id }) { note ->
                TrashItem(
                    note = note,
                    now = now,
                    onRestore = { actions.onRestore(note.id) },
                    onDeleteForever = { actions.onDeleteForever(note.id) },
                    modifier = Modifier.animateItemPlacement().padding(bottom = 10.dp)
                )
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("Empty trash?") },
            text = {
                Text(
                    if (notes.size == 1) "This item will be deleted permanently. This can't be undone."
                    else "All ${notes.size} items will be deleted permanently. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onEmptyTrash()
                    confirmEmpty = false
                }) { Text("Delete all", color = scheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashItem(
    note: NoteEntity,
    now: Long,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val daysLeft = (RETENTION_DAYS - ((now - note.updatedAt) / DAY_MS).toInt()).coerceAtLeast(0)
    val (icon, tint) = when (note.type) {
        NoteType.NOTE -> Icons.Rounded.Description to scheme.primary
        NoteType.JOURNAL -> Icons.Rounded.AutoStories to scheme.tertiary
        NoteType.TASK -> Icons.Rounded.TaskAlt to quill.success
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = icon, background = tint.copy(alpha = 0.12f), tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = note.title.ifBlank { note.content.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Deleted ${relativePhrase(note.updatedAt, now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (daysLeft == 1) "1 day left" else "$daysLeft days left",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (daysLeft <= 3) scheme.error else scheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onRestore,
                    shape = CircleShape,
                    color = scheme.onBackground,
                    contentColor = scheme.background,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                        Text("Restore", style = MaterialTheme.typography.labelLarge)
                    }
                }
                CircleIconButton(
                    icon = Icons.Rounded.DeleteForever,
                    contentDescription = "Delete forever",
                    onClick = onDeleteForever,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = scheme.error,
                    size = 40.dp
                )
            }
        }
    }
}
