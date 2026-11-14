@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.quillnotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.dueLabel
import com.quillnotes.ui.util.parseTags
import com.quillnotes.ui.util.relativeTime
import com.quillnotes.ui.util.startOfDay
import com.quillnotes.ui.util.timeOfDay
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.quillnotes.R
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.domain.Priority
import com.quillnotes.domain.RepeatRule
import com.quillnotes.text.Checklist
import com.quillnotes.text.LineKind
import com.quillnotes.text.Markdown
import com.quillnotes.text.MarkdownPreview
import com.quillnotes.ui.util.LocalTimeText

@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    previewLines: Int = 6,
    now: Long = System.currentTimeMillis(),
    cover: AttachmentEntity? = null,
    onToggleChecklist: ((Int) -> Unit)? = null
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val tone = quill.toneFor(note.color)
    val container = tone?.container ?: quill.card
    val contentColor = tone?.content ?: scheme.onSurface
    val secondary = contentColor.copy(alpha = 0.66f)
    val accent = tone?.accent ?: scheme.primary
    val tags = remember(note.tags) { parseTags(note.tags) }
    val interaction = remember { MutableInteractionSource() }

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> scheme.primary
            tone == null -> quill.cardBorder
            else -> Color.Transparent
        },
        label = "noteBorder"
    )

    Box(modifier.fillMaxWidth().pressScale(interaction)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(container)
                .border(if (isSelected) 2.dp else 1.dp, borderColor, shape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            val hasTitle = note.title.isNotBlank()
            val hasBody = note.content.isNotBlank() && !note.isLocked
            if (cover != null && !note.isLocked) {
                val ratio = if (cover.width > 0 && cover.height > 0) (cover.width.toFloat() / cover.height).coerceIn(0.75f, 1.8f) else 1.4f
                AttachmentImage(
                    uuid = cover.uuid,
                    maxEdge = 512,
                    pending = !cover.hasLocalFile,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(10.dp))
            }
            if (hasTitle) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = contentColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = if (selectionMode) 24.dp else 0.dp)
                )
            }
            if (note.isLocked) {
                if (hasTitle) Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.locked_note_label), style = MaterialTheme.typography.bodyMedium, color = secondary)
                }
            } else if (hasBody) {
                if (hasTitle) Spacer(Modifier.height(6.dp))
                if (Markdown.isRich(note.content)) {
                    MarkdownPreviewBlock(
                        content = note.content,
                        maxLines = previewLines,
                        color = secondary,
                        accent = accent,
                        onToggle = onToggleChecklist
                    )
                } else {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                        maxLines = previewLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!hasTitle && !hasBody && !note.isLocked && cover == null) {
                Text(
                    text = stringResource(R.string.empty_note),
                    style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
                    color = secondary
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.take(2).forEach { TagPill(it, contentColor) }
                    if (tags.size > 2) {
                        Text(
                            text = "+${tags.size - 2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondary,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = relativeTime(note.updatedAt, now, LocalTimeText.current),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                    modifier = Modifier.weight(1f)
                )
                if (note.isLocked) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = secondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                if (note.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = stringResource(R.string.pinned),
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) {
            SelectionBadge(isSelected)
        }
    }
}

/**
 * A note's text drawn as the Markdown it is: headings bold, bullets as bullets, and checklist
 * items as boxes that can be ticked straight from the card.
 */
@Composable
fun MarkdownPreviewBlock(
    content: String,
    maxLines: Int,
    color: Color,
    accent: Color,
    onToggle: ((lineIndex: Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val preview = remember(content, maxLines) { MarkdownPreview.build(content, maxLines) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        preview.lines.forEach { line ->
            val indent = (line.indent / 2 * 14).dp
            when (line.kind) {
                LineKind.CHECK -> Row(Modifier.padding(start = indent), verticalAlignment = Alignment.Top) {
                    MiniCheck(
                        checked = line.checked,
                        accent = accent,
                        outline = color,
                        onClick = onToggle?.let { toggle -> { toggle(line.lineIndex) } }
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.checked) color.copy(alpha = 0.55f) else color,
                        textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                LineKind.BULLET -> Row(Modifier.padding(start = indent)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.width(14.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                LineKind.NUMBERED -> Row(Modifier.padding(start = indent)) {
                    Text("${line.number}.", style = MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.width(22.dp))
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                LineKind.HEADING -> Text(
                    line.text,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LineKind.QUOTE -> Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = color.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LineKind.PLAIN -> Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (preview.truncated) Text("…", style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** A small round checkbox with a comfortable touch area; read-only when [onClick] is null. */
@Composable
private fun MiniCheck(checked: Boolean, accent: Color, outline: Color, onClick: (() -> Unit)?) {
    val fill by animateColorAsState(if (checked) accent else Color.Transparent, label = "miniCheckFill")
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, if (checked) accent else outline.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else scheme.surface, label = "badgeBg")
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.a11y_selected),
                tint = scheme.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun TagPill(tag: String, contentColor: Color, modifier: Modifier = Modifier) {
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelSmall,
        color = contentColor.copy(alpha = 0.82f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Journal entry with a mood-coloured timeline rail on the left. */
@Composable
fun JournalEntryCard(
    entry: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = true
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val mood = moods.find { it.key == entry.mood }
    val tone = quill.toneFor(entry.color)
    val container = tone?.container ?: quill.card
    val contentColor = tone?.content ?: scheme.onSurface
    val interaction = remember { MutableInteractionSource() }

    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(24.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(mood?.color ?: scheme.outline)
            )
            if (showRail) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(scheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .pressScale(interaction)
                .clip(shape)
                .background(container)
                .border(1.dp, if (tone == null) quill.cardBorder else Color.Transparent, shape)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeOfDay(entry.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.66f),
                    modifier = Modifier.weight(1f)
                )
                if (mood != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(mood.color.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(mood.emoji, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(mood.labelRes), style = MaterialTheme.typography.labelMedium, color = contentColor)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.title.ifBlank { stringResource(R.string.untitled_entry) },
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Circular animated checkbox with a generous touch target. */
@Composable
fun QuillCheck(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    checkColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) accent else Color.Transparent, label = "checkFill")
    val ring by animateColorAsState(
        if (checked) accent else scheme.onSurfaceVariant.copy(alpha = 0.55f),
        label = "checkRing"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale"
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(if (checked) R.string.task_mark_incomplete else R.string.task_mark_complete),
                tint = checkColor,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    }
            )
        }
    }
}

@Composable
fun DueChip(dueDate: Long, completed: Boolean, modifier: Modifier = Modifier, now: Long = System.currentTimeMillis()) {
    val scheme = MaterialTheme.colorScheme
    val today = startOfDay(now)
    val due = startOfDay(dueDate)
    val (bg, fg) = when {
        completed -> scheme.surfaceVariant to scheme.onSurfaceVariant
        due < today -> scheme.errorContainer to scheme.onErrorContainer
        due == today -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(dueLabel(dueDate, now, LocalTimeText.current) + " · " + timeOfDay(dueDate), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** "2/5" for a task whose notes hold a checklist of sub-steps. */
@Composable
fun SubtaskChip(done: Int, total: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val finished = done == total
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(if (finished) scheme.tertiaryContainer else scheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(
            Icons.Rounded.Checklist,
            contentDescription = null,
            tint = if (finished) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$done/$total",
            style = MaterialTheme.typography.labelSmall,
            color = if (finished) scheme.onTertiaryContainer else scheme.onSurfaceVariant
        )
    }
}

@Composable
fun TaskRow(
    task: NoteEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis()
) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val tone = quill.toneFor(task.color)
    val done = task.isCompleted
    val tags = remember(task.tags) { parseTags(task.tags) }
    val progress = remember(task.content) { Checklist.progress(task.content) }
    val detail = remember(task.content, progress) {
        if (progress != null) null
        else Markdown.toPlain(task.content).lineSequence().firstOrNull { it.isNotBlank() }
    }
    val priority = Priority.of(task.priority)
    val interaction = remember { MutableInteractionSource() }
    val titleColor by animateColorAsState(
        if (done) scheme.onSurfaceVariant.copy(alpha = 0.7f) else scheme.onSurface,
        label = "taskTitle"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(quill.card)
            .border(1.dp, quill.cardBorder, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        QuillCheck(
            checked = done,
            onCheckedChange = onToggle,
            accent = tone?.accent ?: scheme.primary
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (priority != Priority.NONE && !done) {
                    Icon(
                        Icons.Rounded.Flag,
                        contentDescription = priorityLabel(priority),
                        tint = priorityColor(priority) ?: scheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = task.title.ifBlank { stringResource(R.string.untitled_task) },
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val repeats = RepeatRule.parse(task.repeatRule) != null
            if (task.dueDate != null || tags.isNotEmpty() || progress != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    task.dueDate?.let { DueChip(it, done, now = now) }
                    if (repeats) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = stringResource(R.string.repeat_chip_desc),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    progress?.let { (d, t) -> SubtaskChip(d, t) }
                    tags.firstOrNull()?.let { TagPill(it, scheme.onSurface) }
                }
            }
        }
        if (tone != null) {
            Spacer(Modifier.width(8.dp))
            ColorDot(tone.accent, size = 10.dp)
        }
    }
}
