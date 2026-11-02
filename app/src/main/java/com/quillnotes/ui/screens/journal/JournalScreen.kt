@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.quillnotes.ui.screens.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.LocalFireDepartment
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.ui.components.DockClearance
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.JournalEntryCard
import com.quillnotes.ui.components.MoodOption
import com.quillnotes.ui.components.QuillSnackbar
import com.quillnotes.ui.components.QuillSwipeBox
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.ScreenHeader
import com.quillnotes.ui.components.SectionLabel
import com.quillnotes.ui.components.StatusBarScrim
import com.quillnotes.ui.components.heroBrush
import com.quillnotes.ui.components.moodFor
import com.quillnotes.ui.components.trashSwipeAction
import com.quillnotes.ui.util.dayLabel
import com.quillnotes.ui.util.headerDate
import com.quillnotes.ui.util.shiftDays
import com.quillnotes.ui.util.shortWeekday
import com.quillnotes.ui.util.startOfDay

data class JournalUiState(
    val entries: List<NoteEntity> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

class JournalActions(
    val onEntryClick: (Long) -> Unit = {},
    val onNewEntry: () -> Unit = {},
    val onDelete: (Long) -> Unit = {}
)

/** One cell of the weekly mood strip. */
data class MoodDay(val dayStart: Long, val entryCount: Int, val mood: MoodOption?)

/** The last seven days ending today, each with its most recent mood. */
fun moodWeek(entries: List<NoteEntity>, now: Long): List<MoodDay> {
    val today = startOfDay(now)
    val byDay = entries.groupBy { startOfDay(it.createdAt) }
    return (6 downTo 0).map { back ->
        val day = shiftDays(today, -back)
        val dayEntries = byDay[day].orEmpty()
        MoodDay(
            dayStart = day,
            entryCount = dayEntries.size,
            mood = dayEntries.maxByOrNull { it.createdAt }?.let { latest ->
                moodFor(latest.mood) ?: dayEntries.firstNotNullOfOrNull { moodFor(it.mood) }
            }
        )
    }
}

/** Consecutive days with an entry, counting back from today (or yesterday if today is still blank). */
fun journalStreak(entries: List<NoteEntity>, now: Long): Int {
    val days = entries.map { startOfDay(it.createdAt) }.toHashSet()
    var day = startOfDay(now)
    if (day !in days) day = shiftDays(day, -1)
    var streak = 0
    while (day in days) {
        streak++
        day = shiftDays(day, -1)
    }
    return streak
}

@Composable
fun JournalScreen(
    onEntryClick: (Long) -> Unit,
    onNewEntry: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val now = remember(entries) { System.currentTimeMillis() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { id ->
            val result = snackbarHostState.showSnackbar(
                message = "Entry moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(id)
        }
    }

    JournalContent(
        state = JournalUiState(entries = entries, now = now),
        actions = JournalActions(
            onEntryClick = onEntryClick,
            onNewEntry = onNewEntry,
            onDelete = viewModel::deleteEntry
        ),
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun JournalContent(
    state: JournalUiState,
    actions: JournalActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val today = startOfDay(state.now)
    val groups = remember(state.entries) {
        state.entries.sortedByDescending { it.createdAt }.groupBy { startOfDay(it.createdAt) }.toList()
    }
    val week = remember(state.entries, today) { moodWeek(state.entries, state.now) }
    val streak = remember(state.entries, today) { journalStreak(state.entries, state.now) }
    val wroteToday = groups.firstOrNull()?.first == today

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = statusTop + 8.dp,
                bottom = DockClearance + navBottom + 16.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                val count = state.entries.size
                ScreenHeader(
                    title = "Journal",
                    eyebrow = headerDate(state.now),
                    subtitle = if (count == 1) "1 entry" else "$count entries"
                )
                Spacer(Modifier.height(16.dp))
            }

            if (state.entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.AutoStories,
                        title = "Your journal awaits",
                        message = "Write a few lines each day. Your moods and streaks will start to show here.",
                        actionLabel = "Write first entry",
                        onAction = actions.onNewEntry
                    )
                }
                return@LazyColumn
            }

            item(key = "week") {
                MoodWeekCard(week = week, streak = streak, today = today)
                Spacer(Modifier.height(12.dp))
            }

            if (!wroteToday) {
                item(key = "prompt") {
                    TodayPrompt(onClick = actions.onNewEntry)
                    Spacer(Modifier.height(4.dp))
                }
            }

            groups.forEach { (day, dayEntries) ->
                item(key = "day_$day") {
                    SectionLabel(dayLabel(day, state.now), count = dayEntries.size, padding = PaddingValues(top = 14.dp, bottom = 10.dp))
                }
                itemsIndexed(dayEntries, key = { _, entry -> entry.id }) { index, entry ->
                    QuillSwipeBox(
                        toStartAction = trashSwipeAction(),
                        onSwipeToStart = { actions.onDelete(entry.id) },
                        modifier = Modifier.animateItemPlacement()
                    ) {
                        JournalEntryCard(
                            entry = entry,
                            onClick = { actions.onEntryClick(entry.id) },
                            showRail = index != dayEntries.lastIndex
                        )
                    }
                }
            }
        }

        StatusBarScrim()

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DockClearance + navBottom - 8.dp)
                .padding(horizontal = 12.dp)
        ) { data -> QuillSnackbar(data) }
    }
}

@Composable
private fun MoodWeekCard(week: List<MoodDay>, streak: Int, today: Long) {
    val scheme = MaterialTheme.colorScheme
    val entriesThisWeek = week.sumOf { it.entryCount }
    val topMood = week.mapNotNull { it.mood }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(heroBrush())
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("This week", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                    Text(
                        text = buildString {
                            append(if (entriesThisWeek == 1) "1 entry" else "$entriesThisWeek entries")
                            if (topMood != null) append(" · mostly ${topMood.label.lowercase()}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (streak > 0) StreakBadge(streak)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { day -> MoodDayCell(day, isToday = day.dayStart == today) }
            }
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(scheme.surface.copy(alpha = 0.75f))
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (streak == 1) "1 day" else "$streak days",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface
        )
    }
}

@Composable
private fun MoodDayCell(day: MoodDay, isToday: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = shortWeekday(day.dayStart),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium),
            color = scheme.onSurface.copy(alpha = if (isToday) 1f else 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        val fill = when {
            day.mood != null -> day.mood.color.copy(alpha = 0.28f)
            day.entryCount > 0 -> scheme.onSurface.copy(alpha = 0.12f)
            else -> Color.Transparent
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(fill)
                .border(
                    BorderStroke(
                        width = if (isToday) 2.dp else 1.dp,
                        color = when {
                            isToday -> scheme.onSurface
                            day.entryCount > 0 -> Color.Transparent
                            else -> scheme.onSurface.copy(alpha = 0.18f)
                        }
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                day.mood != null -> Text(day.mood.emoji, style = MaterialTheme.typography.titleMedium)
                day.entryCount > 0 -> Box(Modifier.size(6.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.6f)))
            }
        }
    }
}

@Composable
private fun TodayPrompt(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("How was today?", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Take a minute to capture it while it's fresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.onBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Write", style = MaterialTheme.typography.labelLarge, color = scheme.background)
            }
        }
    }
}
