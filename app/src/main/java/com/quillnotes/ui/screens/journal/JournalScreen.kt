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
import androidx.compose.foundation.lazy.LazyListScope
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
import com.quillnotes.ui.components.CircleIconButton
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.res.stringResource
import com.quillnotes.R
import com.quillnotes.domain.JournalStats
import com.quillnotes.text.Markdown
import com.quillnotes.ui.components.QuillChip
import com.quillnotes.ui.components.moods
import com.quillnotes.ui.util.dayOfMonth
import com.quillnotes.ui.util.monthYear
import com.quillnotes.ui.util.yearOf
import com.quillnotes.ui.theme.QuillTheme

data class JournalUiState(
    val entries: List<NoteEntity> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val view: JournalView = JournalView.TIMELINE,
    val monthStart: Long = JournalStats.startOfMonth(now),
    val selectedDay: Long? = null
)

class JournalActions(
    val onEntryClick: (Long) -> Unit = {},
    val onNewEntry: () -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onViewChange: (JournalView) -> Unit = {},
    val onShiftMonth: (Int) -> Unit = {},
    val onSelectDay: (Long) -> Unit = {}
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
    val view by viewModel.view.collectAsState()
    val monthStart by viewModel.monthStart.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
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
        state = JournalUiState(entries = entries, now = now, view = view, monthStart = monthStart, selectedDay = selectedDay),
        actions = JournalActions(
            onEntryClick = onEntryClick,
            onNewEntry = onNewEntry,
            onDelete = viewModel::deleteEntry,
            onViewChange = viewModel::setView,
            onShiftMonth = viewModel::shiftMonth,
            onSelectDay = viewModel::selectDay
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
    val onThisDay = remember(state.entries, today) { JournalStats.onThisDay(state.entries, state.now) }

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

            item(key = "view_switch") {
                JournalViewSwitch(state.view, actions.onViewChange)
                Spacer(Modifier.height(12.dp))
            }
            if (state.view == JournalView.CALENDAR) {
                calendarSection(state, actions)
                return@LazyColumn
            }

            item(key = "week") {
                MoodWeekCard(week = week, streak = streak, today = today)
                Spacer(Modifier.height(12.dp))
            }
            if (onThisDay.isNotEmpty()) {
                item(key = "on_this_day") {
                    OnThisDayCard(onThisDay, state.now, actions.onEntryClick)
                    Spacer(Modifier.height(12.dp))
                }
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

@Composable
private fun JournalViewSwitch(view: JournalView, onChange: (JournalView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuillChip(stringResource(R.string.journal_view_timeline), view == JournalView.TIMELINE, onClick = { onChange(JournalView.TIMELINE) })
        QuillChip(stringResource(R.string.journal_view_calendar), view == JournalView.CALENDAR, onClick = { onChange(JournalView.CALENDAR) })
    }
}

/** Month grid, this month's mood trend, and the entries of the chosen day (or the whole month). */
private fun LazyListScope.calendarSection(state: JournalUiState, actions: JournalActions) {
    val byDay = state.entries.groupBy { JournalStats.dayStart(it.createdAt) }
    item(key = "calendar") {
        JournalCalendarCard(state, byDay, actions)
        Spacer(Modifier.height(12.dp))
    }
    item(key = "trend") {
        val summary = remember(state.entries, state.monthStart) { JournalStats.summarize(state.entries, state.monthStart) }
        MoodTrendCard(summary)
        Spacer(Modifier.height(4.dp))
    }
    val monthEnd = JournalStats.shiftMonths(state.monthStart, 1)
    val shown = state.entries
        .filter { e -> if (state.selectedDay != null) JournalStats.dayStart(e.createdAt) == state.selectedDay else e.createdAt in state.monthStart until monthEnd }
        .sortedByDescending { it.createdAt }
    item(key = "calendar_label") {
        SectionLabel(
            text = state.selectedDay?.let { dayLabel(it, state.now) } ?: monthYear(state.monthStart),
            count = shown.size,
            padding = PaddingValues(top = 14.dp, bottom = 10.dp)
        )
    }
    if (shown.isEmpty()) {
        item(key = "calendar_empty") {
            Text(
                stringResource(R.string.journal_nothing_here),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    } else {
        itemsIndexed(shown, key = { _, e -> "cal_${e.id}" }) { index, entry ->
            QuillSwipeBox(
                toStartAction = trashSwipeAction(),
                onSwipeToStart = { actions.onDelete(entry.id) },
                modifier = Modifier.animateItemPlacement()
            ) {
                JournalEntryCard(entry = entry, onClick = { actions.onEntryClick(entry.id) }, showRail = index != shown.lastIndex)
            }
        }
    }
}

@Composable
private fun JournalCalendarCard(state: JournalUiState, byDay: Map<Long, List<NoteEntity>>, actions: JournalActions) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val grid = remember(state.monthStart) { JournalStats.monthGrid(state.monthStart) }
    val today = startOfDay(state.now)
    val thisMonth = state.monthStart == JournalStats.startOfMonth(state.now)
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthYear(state.monthStart),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                if (!thisMonth) {
                    androidx.compose.material3.TextButton(onClick = { actions.onShiftMonth(0) }) { Text(stringResource(R.string.journal_this_month)) }
                }
                CircleIconButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.journal_previous_month),
                    onClick = { actions.onShiftMonth(-1) },
                    size = 40.dp,
                    containerColor = Color.Transparent
                )
                CircleIconButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.journal_next_month),
                    onClick = { actions.onShiftMonth(1) },
                    size = 40.dp,
                    containerColor = Color.Transparent
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                grid.first().forEach { cell ->
                    Text(
                        text = shortWeekday(cell.dayStart),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            grid.forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        val entries = byDay[cell.dayStart].orEmpty()
                        val mood = entries.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { moodFor(it.mood) }
                        CalendarDayCell(
                            day = cell.dayStart,
                            inMonth = cell.inMonth,
                            isToday = cell.dayStart == today,
                            selected = cell.dayStart == state.selectedDay,
                            hasEntries = entries.isNotEmpty(),
                            mood = mood,
                            modifier = Modifier.weight(1f),
                            onClick = { if (cell.inMonth) actions.onSelectDay(cell.dayStart) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Long,
    inMonth: Boolean,
    isToday: Boolean,
    selected: Boolean,
    hasEntries: Boolean,
    mood: MoodOption?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.height(44.dp), contentAlignment = Alignment.Center) {
        val fill = when {
            mood != null -> mood.color.copy(alpha = 0.30f)
            hasEntries -> scheme.onSurface.copy(alpha = 0.10f)
            else -> Color.Transparent
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(fill)
                .border(
                    width = if (selected) 2.dp else if (isToday) 1.5.dp else 0.dp,
                    color = if (selected) scheme.onSurface else if (isToday) scheme.onSurface.copy(alpha = 0.6f) else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(enabled = inMonth, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayOfMonth(day).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface.copy(alpha = if (inMonth) 1f else 0.25f)
            )
        }
    }
}

/** How the month has felt: a line through the days that have a mood, and a tally per mood. */
@Composable
private fun MoodTrendCard(summary: JournalStats.MonthSummary) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.journal_mood_trend), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            val avg = summary.averageScore
            Text(
                text = when {
                    avg == null -> stringResource(R.string.journal_trend_none)
                    avg >= 1.0 -> stringResource(R.string.journal_trend_positive)
                    avg >= 0.25 -> stringResource(R.string.journal_trend_good)
                    avg > -0.25 -> stringResource(R.string.journal_trend_balanced)
                    avg > -1.0 -> stringResource(R.string.journal_trend_low)
                    else -> stringResource(R.string.journal_trend_hard)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            if (summary.entryCount > 0) {
                Text(
                    text = stringResource(R.string.journal_trend_counts, summary.entryCount, summary.daysWritten),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (summary.dailyScores.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                MoodSparkline(summary, scheme.primary, scheme.onSurface.copy(alpha = 0.12f))
            }
            if (summary.moodCounts.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val max = summary.moodCounts.values.max().coerceAtLeast(1)
                moods.forEach { mood ->
                    val count = summary.moodCounts[mood.key] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(mood.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(mood.color.copy(alpha = 0.14f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(count.toFloat() / max)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(mood.color)
                            )
                        }
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp).padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A line over the days of the month that have a mood: high is happy, low is sad. */
@Composable
private fun MoodSparkline(summary: JournalStats.MonthSummary, line: Color, grid: Color) {
    val days = remember(summary.monthStart) {
        val end = JournalStats.shiftMonths(summary.monthStart, 1)
        ((end - summary.monthStart) / (24L * 3_600_000L)).toInt().coerceIn(28, 31)
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        val w = size.width
        val h = size.height
        // Faint guide lines at the happiest, neutral and saddest levels.
        listOf(0f, 0.5f, 1f).forEach { f -> drawLine(grid, androidx.compose.ui.geometry.Offset(0f, h * f), androidx.compose.ui.geometry.Offset(w, h * f), 1.dp.toPx()) }
        val points = summary.dailyScores.entries
            .map { (day, score) ->
                val index = ((day - summary.monthStart) / (24L * 3_600_000L)).toInt().coerceIn(0, days - 1)
                androidx.compose.ui.geometry.Offset(w * index / (days - 1), (h * (1f - ((score.toFloat() + 2f) / 4f))).coerceIn(4.dp.toPx(), h - 4.dp.toPx()))
            }
            .sortedBy { it.x }
        if (points.size > 1) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        points.forEach { drawCircle(line, 4.dp.toPx(), it) }
    }
}

/** Entries written on today's date in earlier years. */
@Composable
private fun OnThisDayCard(entries: List<NoteEntity>, now: Long, onClick: (Long) -> Unit) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.journal_on_this_day), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            entries.take(3).forEach { entry ->
                val mood = moodFor(entry.mood)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onClick(entry.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = yearOf(entry.createdAt).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title.ifBlank { stringResource(R.string.untitled_entry) },
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        val snippet = Markdown.toPlain(entry.content).lineSequence().firstOrNull { it.isNotBlank() }
                        if (!snippet.isNullOrBlank() && !entry.isLocked) {
                            Text(
                                snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (mood != null) Text(mood.emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
