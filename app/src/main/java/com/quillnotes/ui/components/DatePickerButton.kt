@file:OptIn(ExperimentalMaterial3Api::class)

package com.quillnotes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quillnotes.ui.util.dueLabel
import com.quillnotes.ui.util.localToPickerDate
import com.quillnotes.ui.util.pickerDateToLocal
import com.quillnotes.ui.util.startOfDay

/** Pill that shows the due date (or a prompt) and opens a date picker. */
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
                text = selectedDate?.let { "Due ${dueLabel(it, now)}" } ?: "Add due date",
                style = MaterialTheme.typography.labelLarge
            )
            if (selectedDate != null) {
                Spacer(Modifier.width(2.dp))
                CircleIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Clear due date",
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
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateSelected(datePickerState.selectedDateMillis?.let { pickerDateToLocal(it) })
                    showDatePicker = false
                }) { Text("Set date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
