@file:OptIn(ExperimentalLayoutApi::class)

package com.quillnotes.ui.screens.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quillnotes.R
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.domain.LinkInfo
import com.quillnotes.ui.components.AttachmentImage
import com.quillnotes.ui.components.DatePickerButton
import com.quillnotes.ui.components.PriorityChip
import com.quillnotes.ui.components.RepeatChip
import com.quillnotes.ui.components.TimeChip
import com.quillnotes.ui.util.withTimeOfDay

/** Due date, reminder time, repeat and priority for a task. */
@Composable
internal fun TaskOptions(state: EditorState, actions: EditorActions, ink: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DatePickerButton(
            selectedDate = state.dueDate,
            onDateSelected = actions.onDueDateChange,
            contentColor = ink
        )
        val due = state.dueDate
        if (due != null) {
            TimeChip(
                selected = due,
                onTimeSelected = { h, m -> actions.onDueDateChange(withTimeOfDay(due, h, m)) },
                contentColor = ink
            )
            RepeatChip(rule = state.repeatRule, onSelect = actions.onRepeatChange, contentColor = ink)
        }
        PriorityChip(level = state.priority, onSelect = actions.onPriorityChange, contentColor = ink)
    }
}

/** Which notebook a note is filed under; opens the picker. */
@Composable
internal fun NotebookChip(notebook: String?, ink: Color, onClick: () -> Unit) {
    val filed = !notebook.isNullOrBlank()
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filed) ink.copy(alpha = 0.08f) else Color.Transparent,
        contentColor = if (filed) ink else ink.copy(alpha = 0.7f),
        border = if (filed) null else BorderStroke(1.dp, ink.copy(alpha = 0.22f)),
        modifier = Modifier.height(36.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (filed) notebook!! else stringResource(R.string.notebook_add),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Pick an existing notebook, make a new one, or take the note out of its notebook. */
@Composable
fun NotebookDialog(
    current: String?,
    notebooks: List<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(notebooks.isEmpty()) }
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notebook_dialog_title)) },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.notebook_new_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        notebooks.forEach { name ->
                            NotebookRow(name, selected = name.equals(current, ignoreCase = true)) { onSelect(name) }
                        }
                        NotebookRow(
                            stringResource(R.string.notebook_new),
                            selected = false,
                            icon = Icons.Rounded.CreateNewFolder
                        ) { creating = true }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { onSelect(draft.trim()) }, enabled = draft.isNotBlank()) {
                    Text(stringResource(R.string.notebook_create))
                }
            } else if (current != null) {
                TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.notebook_remove)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun NotebookRow(
    name: String,
    selected: Boolean,
    icon: ImageVector = Icons.Rounded.Folder,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** A row of the note's pictures: tap to view full screen, × to remove. */
@Composable
internal fun AttachmentStrip(
    attachments: List<AttachmentEntity>,
    ink: Color,
    onOpen: (AttachmentEntity) -> Unit,
    onRemove: (AttachmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        attachments.forEach { a ->
            Box(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(a) }) {
                AttachmentImage(
                    uuid = a.uuid,
                    maxEdge = 512,
                    pending = !a.hasLocalFile,
                    contentDescription = stringResource(R.string.attachment_image),
                    modifier = Modifier.fillMaxWidth().height(112.dp)
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClickLabel = stringResource(R.string.attachment_remove)) { onRemove(a) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.attachment_remove),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Notes this one links to (`[[Title]]`) and notes that link here. */
@Composable
internal fun LinksSection(
    links: LinkInfo,
    ink: Color,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    if (links.outgoing.isEmpty() && links.backlinks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        if (links.outgoing.isNotEmpty()) {
            LinkGroup(stringResource(R.string.links_outgoing), ink) {
                links.outgoing.forEach { link ->
                    val ref = link.target
                    LinkPill(
                        label = if (ref != null) ref.title else stringResource(R.string.links_create, link.title),
                        ink = ink,
                        dashed = ref == null
                    ) { if (ref != null) onOpen(ref.id) else onCreate(link.title) }
                }
            }
        }
        if (links.backlinks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            LinkGroup(stringResource(R.string.links_backlinks), ink) {
                links.backlinks.forEach { ref -> LinkPill(ref.title, ink, dashed = false) { onOpen(ref.id) } }
            }
        }
    }
}

@Composable
private fun LinkGroup(title: String, ink: Color, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = ink.copy(alpha = 0.55f),
        modifier = Modifier.padding(bottom = 8.dp)
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun LinkPill(label: String, ink: Color, dashed: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (dashed) Color.Transparent else ink.copy(alpha = 0.07f),
        contentColor = ink.copy(alpha = 0.9f),
        border = if (dashed) BorderStroke(1.dp, ink.copy(alpha = 0.3f)) else null,
        modifier = Modifier.height(34.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (dashed) Icons.Rounded.Add else Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shown instead of a locked note's text until the user authenticates. */
@Composable
internal fun LockedNotePlaceholder(ink: Color, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.locked_note_title), style = MaterialTheme.typography.headlineSmall, color = ink)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.locked_note_message),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onUnlock) { Text(stringResource(R.string.locked_note_unlock)) }
    }
}

/** A short-lived pill with the result of an action (saved as template, next due date, …). */
@Composable
internal fun EditorMessagePill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/** What the "archived" banner shows at the top of an archived note. */
@Composable
internal fun ArchivedBanner(ink: Color, onRestore: () -> Unit) {
    Surface(
        onClick = onRestore,
        shape = RoundedCornerShape(14.dp),
        color = ink.copy(alpha = 0.07f),
        contentColor = ink,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.archived_banner), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.unarchive), style = MaterialTheme.typography.labelLarge)
        }
    }
}
