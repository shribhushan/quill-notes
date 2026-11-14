@file:OptIn(ExperimentalMaterial3Api::class)

package com.quillnotes.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quillnotes.R
import com.quillnotes.domain.Priority
import com.quillnotes.domain.RepeatRule
import com.quillnotes.domain.RepeatUnit
import com.quillnotes.ui.util.dueLabel
import com.quillnotes.ui.util.hourOf
import com.quillnotes.ui.util.localToPickerDate
import com.quillnotes.ui.util.minuteOf
import com.quillnotes.ui.util.pickerDateToLocal
import com.quillnotes.ui.util.startOfDay
import com.quillnotes.ui.util.timeOfDay
import com.quillnotes.ui.util.LocalTimeText

/** Pill that shows the due date (or a prompt) and opens a date picker. The time of day is kept. */
@Composable
fun DatePickerButton(
    selectedDate: Long?,
    onDateSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val overdue = selectedDate != null && startOfDay(selectedDate) < startOfDay(now)
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        selectedDate == null -> Color.Transparent to contentColor.copy(alpha = 0.7f)
        overdue -> scheme.errorContainer to scheme.onErrorContainer
        else -> contentColor.copy(alpha = 0.08f) to contentColor
    }

    Surface(
        onClick = { showDatePicker = true },
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        border = if (selectedDate == null) BorderStroke(1.dp, contentColor.copy(alpha = 0.22f)) else null,
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (selectedDate != null) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = selectedDate?.let { stringResource(R.string.due_prefix, dueLabel(it, now, LocalTimeText.current)) } ?: stringResource(R.string.due_add),
                style = MaterialTheme.typography.labelLarge
            )
            if (selectedDate != null) {
                Spacer(Modifier.width(2.dp))
                CircleIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.due_clear),
                    onClick = { onDateSelected(null) },
                    containerColor = Color.Transparent,
                    contentColor = fg,
                    size = 28.dp,
                    iconSize = 14.dp
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localToPickerDate(selectedDate ?: now)
        )
        // A date picked for a task that already has a time keeps that time; otherwise 9:00.
        val hour = selectedDate?.let(::hourOf) ?: 9
        val minute = selectedDate?.let(::minuteOf) ?: 0
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis?.let { pickerDateToLocal(it, hour, minute) })
                    showDatePicker = false
                }) { Text(stringResource(R.string.due_set_date)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** The reminder time of a task that has a due date. */
@Composable
fun TimeChip(
    selected: Long,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var show by remember { mutableStateOf(false) }
    MetaChip(
        icon = Icons.Rounded.AccessTime,
        label = timeOfDay(selected),
        description = stringResource(R.string.time_chip_desc),
        onClick = { show = true },
        contentColor = contentColor,
        modifier = modifier
    )
    if (show) {
        QuillTimePickerDialog(
            title = stringResource(R.string.time_pick_title),
            initialHour = hourOf(selected),
            initialMinute = minuteOf(selected),
            onDismiss = { show = false },
            onConfirm = { h, m ->
                show = false
                onTimeSelected(h, m)
            }
        )
    }
}

/** Material's clock-face picker in a dialog sized for it (an [androidx.compose.material3.AlertDialog] is too narrow). */
@Composable
fun QuillTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = is24)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                )
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.time_set)) }
                }
            }
        }
    }
}

/** Repeat rule of a recurring task, chosen from a short menu. */
@Composable
fun RepeatChip(
    rule: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var open by remember { mutableStateOf(false) }
    val current = RepeatRule.parse(rule)
    Box(modifier) {
        MetaChip(
            icon = Icons.Rounded.Repeat,
            label = repeatLabel(current),
            description = stringResource(R.string.repeat_chip_desc),
            onClick = { open = true },
            contentColor = contentColor,
            active = current != null
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.repeat_none)) },
                trailingIcon = { if (current == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = { open = false; onSelect(null) }
            )
            RepeatRule.PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(repeatLabel(preset)) },
                    trailingIcon = { if (current == preset) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { open = false; onSelect(preset.code) }
                )
            }
        }
    }
}

/** "Every week", "Every 2 weeks", … or "Doesn't repeat" for null. */
@Composable
fun repeatLabel(rule: RepeatRule?): String {
    if (rule == null) return stringResource(R.string.repeat_none)
    return when (rule) {
        RepeatRule.EVERY_DAY -> stringResource(R.string.repeat_every_day)
        RepeatRule.EVERY_WEEK -> stringResource(R.string.repeat_every_week)
        RepeatRule.EVERY_TWO_WEEKS -> stringResource(R.string.repeat_every_two_weeks)
        RepeatRule.EVERY_MONTH -> stringResource(R.string.repeat_every_month)
        RepeatRule.EVERY_YEAR -> stringResource(R.string.repeat_every_year)
        else -> when (rule.unit) {
            RepeatUnit.DAY -> stringResource(R.string.repeat_custom_days, rule.interval)
            RepeatUnit.WEEK -> stringResource(R.string.repeat_custom_weeks, rule.interval)
            RepeatUnit.MONTH -> stringResource(R.string.repeat_custom_months, rule.interval)
            RepeatUnit.YEAR -> stringResource(R.string.repeat_custom_years, rule.interval)
        }
    }
}

/** Task priority: a flag that is coloured once a level is set. */
@Composable
fun PriorityChip(
    level: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var open by remember { mutableStateOf(false) }
    val current = Priority.of(level)
    Box(modifier) {
        MetaChip(
            icon = Icons.Rounded.Flag,
            label = priorityLabel(current),
            description = stringResource(R.string.priority_chip_desc),
            onClick = { open = true },
            contentColor = contentColor,
            active = current != Priority.NONE,
            iconTint = priorityColor(current)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Priority.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(priorityLabel(p)) },
                    leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = priorityColor(p) ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = { if (current == p) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = { open = false; onSelect(p.level) }
                )
            }
        }
    }
}

@Composable
fun priorityLabel(p: Priority): String = when (p) {
    Priority.NONE -> stringResource(R.string.priority_none)
    Priority.LOW -> stringResource(R.string.priority_low)
    Priority.MEDIUM -> stringResource(R.string.priority_medium)
    Priority.HIGH -> stringResource(R.string.priority_high)
}

/** Red for high, amber for medium, blue for low; null (no tint) when unset. */
fun priorityColor(p: Priority): Color? = when (p) {
    Priority.NONE -> null
    Priority.LOW -> Color(0xFF3D74DB)
    Priority.MEDIUM -> Color(0xFFE2A21F)
    Priority.HIGH -> Color(0xFFD9534F)
}

/** Shared look for the small "meta" pills under a task's title. */
@Composable
fun MetaChip(
    icon: ImageVector,
    label: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    active: Boolean = true,
    iconTint: Color? = null
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) contentColor.copy(alpha = 0.08f) else Color.Transparent,
        contentColor = if (active) contentColor else contentColor.copy(alpha = 0.7f),
        border = if (active) null else BorderStroke(1.dp, contentColor.copy(alpha = 0.22f)),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = iconTint ?: LocalContentColor.current,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
