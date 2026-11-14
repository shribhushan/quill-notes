@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.quillnotes.ui.screens.planner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.ui.components.rememberNotificationPermissionRequest
import com.quillnotes.ui.components.CircleIconButton
import com.quillnotes.ui.components.DockClearance
import com.quillnotes.ui.components.EmptyState
import com.quillnotes.ui.components.ProgressRing
import com.quillnotes.ui.components.QuillSnackbar
import com.quillnotes.ui.components.QuillSwipeBox
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.ScreenHeader
import com.quillnotes.ui.components.SectionLabel
import com.quillnotes.ui.components.StatusBarScrim
import com.quillnotes.ui.components.SwipeAction
import com.quillnotes.ui.components.TaskRow
import com.quillnotes.ui.components.trashSwipeAction
import com.quillnotes.ui.theme.QuillTheme
import com.quillnotes.ui.util.headerDate
import com.quillnotes.ui.util.shiftDays
import com.quillnotes.ui.util.startOfDay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.quillnotes.R
import com.quillnotes.domain.PlannerStats
import com.quillnotes.domain.Priority
import com.quillnotes.ui.components.QuillChip
import com.quillnotes.ui.components.priorityColor
import com.quillnotes.ui.util.dayLabel
import com.quillnotes.ui.util.dayOfMonth
import com.quillnotes.ui.util.shortDate
import com.quillnotes.ui.util.weekdayAbbrev

data class PlannerUiState(
    val tasks: List<NoteEntity> = emptyList(),
    val showCompleted: Boolean = true,
    val view: PlannerView = PlannerView.LIST,
    val weekStart: Long = PlannerStats.weekStart(System.currentTimeMillis()),
    val selectedDay: Long = startOfDay(System.currentTimeMillis()),
    val now: Long = System.currentTimeMillis()
)

class PlannerActions(
    val onTaskClick: (Long) -> Unit = {},
    val onToggle: (NoteEntity) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onQuickAdd: (String, Boolean) -> Unit = { _, _ -> },
    val onToggleShowCompleted: () -> Unit = {},
    val onViewChange: (PlannerView) -> Unit = {},
    val onShiftWeek: (Int) -> Unit = {},
    val onSelectDay: (Long) -> Unit = {},
    val onQuickAddOn: (String, Long?) -> Unit = { _, _ -> }
)

data class TaskGroups(
    val overdue: List<NoteEntity>,
    val today: List<NoteEntity>,
    val upcoming: List<NoteEntity>,
    val someday: List<NoteEntity>,
    val completed: List<NoteEntity>
) {
    val open: Int get() = overdue.size + today.size + upcoming.size + someday.size
}

fun groupTasks(tasks: List<NoteEntity>, now: Long): TaskGroups {
    val today = startOfDay(now)
    val tomorrow = shiftDays(today, 1)
    val (done, open) = tasks.partition { it.isCompleted }
    // By day, then most important first, then earliest time.
    val dated = open.filter { it.dueDate != null }
        .sortedWith(compareBy<NoteEntity> { startOfDay(it.dueDate!!) }.thenByDescending { it.priority }.thenBy { it.dueDate })
    return TaskGroups(
        overdue = dated.filter { it.dueDate!! < today },
        today = dated.filter { it.dueDate!! in today until tomorrow },
        upcoming = dated.filter { it.dueDate!! >= tomorrow },
        someday = open.filter { it.dueDate == null }.sortedByDescending { it.updatedAt },
        completed = done.sortedByDescending { it.updatedAt }
    )
}

@Composable
fun PlannerScreen(
    onTaskClick: (Long) -> Unit,
    onNewTask: () -> Unit,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val requestNotifications = rememberNotificationPermissionRequest()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val view by viewModel.view.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val now = remember(tasks) { System.currentTimeMillis() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { id ->
            val result = snackbarHostState.showSnackbar(
                message = "Task moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(id)
        }
    }

    PlannerContent(
        state = PlannerUiState(
            tasks = tasks, showCompleted = showCompleted, view = view,
            weekStart = weekStart, selectedDay = selectedDay, now = now
        ),
        actions = PlannerActions(
            onTaskClick = onTaskClick,
            onToggle = viewModel::toggleComplete,
            onDelete = viewModel::deleteTask,
            onQuickAdd = { title, dueToday ->
                if (dueToday) requestNotifications()
                viewModel.quickAdd(title, dueToday)
            },
            onToggleShowCompleted = viewModel::toggleShowCompleted,
            onViewChange = viewModel::setView,
            onShiftWeek = viewModel::shiftWeek,
            onSelectDay = viewModel::selectDay,
            onQuickAddOn = { title, day ->
                requestNotifications()
                viewModel.quickAddOn(title, day)
            }
        ),
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun PlannerContent(
    state: PlannerUiState,
    actions: PlannerActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val scheme = MaterialTheme.colorScheme
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val groups = remember(state.tasks, state.now) { groupTasks(state.tasks, state.now) }

    Box(modifier.fillMaxSize().background(scheme.background)) {
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
                ScreenHeader(
                    title = "Planner",
                    eyebrow = headerDate(state.now),
                    subtitle = "${groups.open} to do · ${groups.completed.size} done"
                )
                Spacer(Modifier.height(16.dp))
            }
            item(key = "view_switch") {
                ViewSwitch(state.view, actions.onViewChange)
                Spacer(Modifier.height(12.dp))
            }
            if (state.view == PlannerView.WEEK) {
                weekSection(state, actions)
                return@LazyColumn
            }
            if (state.tasks.isNotEmpty()) {
                item(key = "progress") {
                    ProgressCard(groups)
                    Spacer(Modifier.height(12.dp))
                }
            }
            item(key = "quick_add") {
                QuickAddField(onAdd = actions.onQuickAdd)
            }

            if (state.tasks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = "Nothing on your plate",
                        message = "Add a task above, or tap + to plan one with a due date and notes."
                    )
                }
                return@LazyColumn
            }

            taskSection("overdue", "Overdue", groups.overdue, state, actions, labelColor = scheme.error)
            taskSection("today", "Today", groups.today, state, actions)
            taskSection("upcoming", "Upcoming", groups.upcoming, state, actions)
            taskSection("someday", "Someday", groups.someday, state, actions)

            if (groups.open == 0) {
                item(key = "all_done") { AllDoneBanner() }
            }

            if (groups.completed.isNotEmpty()) {
                item(key = "label_completed") {
                    SectionLabel(
                        text = "Completed",
                        count = groups.completed.size,
                        padding = PaddingValues(top = 18.dp, bottom = 6.dp),
                        trailing = {
                            val rotation by animateFloatAsState(if (state.showCompleted) 180f else 0f, label = "chevron")
                            CircleIconButton(
                                icon = Icons.Rounded.ExpandMore,
                                contentDescription = if (state.showCompleted) "Hide completed" else "Show completed",
                                onClick = actions.onToggleShowCompleted,
                                containerColor = Color.Transparent,
                                contentColor = scheme.onSurfaceVariant,
                                size = 36.dp,
                                modifier = Modifier.rotate(rotation)
                            )
                        }
                    )
                }
                if (state.showCompleted) {
                    taskItems(groups.completed, state, actions)
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

private fun LazyListScope.taskSection(
    key: String,
    title: String,
    tasks: List<NoteEntity>,
    state: PlannerUiState,
    actions: PlannerActions,
    labelColor: Color? = null
) {
    if (tasks.isEmpty()) return
    item(key = "label_$key") {
        if (labelColor != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(top = 10.dp).size(8.dp).clip(CircleShape).background(labelColor))
                Spacer(Modifier.width(8.dp))
                SectionLabel(title, count = tasks.size, padding = PaddingValues(top = 18.dp, bottom = 8.dp))
            }
        } else {
            SectionLabel(title, count = tasks.size, padding = PaddingValues(top = 18.dp, bottom = 8.dp))
        }
    }
    taskItems(tasks, state, actions)
}

private fun LazyListScope.taskItems(tasks: List<NoteEntity>, state: PlannerUiState, actions: PlannerActions) {
    items(tasks, key = { it.id }) { task ->
        val quill = QuillTheme.colors
        val scheme = MaterialTheme.colorScheme
        Box(Modifier.animateItemPlacement().padding(bottom = 8.dp)) {
            QuillSwipeBox(
                toEndAction = SwipeAction(
                    icon = Icons.Rounded.CheckCircle,
                    label = if (task.isCompleted) "Reopen" else "Done",
                    container = quill.successContainer,
                    content = if (quill.isDark) quill.success else scheme.onSurface
                ),
                onSwipeToEnd = { actions.onToggle(task) },
                toStartAction = trashSwipeAction(),
                onSwipeToStart = { actions.onDelete(task.id) }
            ) {
                TaskRow(
                    task = task,
                    onToggle = { actions.onToggle(task) },
                    onClick = { actions.onTaskClick(task.id) },
                    now = state.now
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(groups: TaskGroups) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val total = groups.open + groups.completed.size
    val done = groups.completed.size
    val progress = if (total == 0) 0f else done.toFloat() / total
    val headline = when {
        total == 0 -> "Plan your day"
        done == total -> "All clear"
        progress >= 0.66f -> "Almost there"
        progress > 0f -> "Making progress"
        else -> "Let's get going"
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = progress,
                color = scheme.primary,
                trackColor = scheme.primary.copy(alpha = 0.14f),
                diameter = 76.dp,
                strokeWidth = 8.dp
            ) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                Text(
                    text = "$done of $total tasks complete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                if (groups.overdue.isNotEmpty() || groups.today.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (groups.overdue.isNotEmpty()) {
                            StatPill("${groups.overdue.size} overdue", scheme.errorContainer, scheme.onErrorContainer)
                        }
                        if (groups.today.isNotEmpty()) {
                            StatPill("${groups.today.size} today", scheme.primaryContainer, scheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun QuickAddField(
    onAdd: (String, Boolean) -> Unit,
    placeholder: String = stringResource(R.string.planner_add_task),
    showTodayToggle: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var dueToday by rememberSaveable { mutableStateOf(false) }
    val submit: () -> Unit = {
        if (text.isNotBlank()) {
            onAdd(text, dueToday)
            text = ""
        }
    }
    val todayBg by animateColorAsState(
        if (dueToday) scheme.primary else Color.Transparent,
        label = "todayToggle"
    )
    val todayFg by animateColorAsState(
        if (dueToday) scheme.onPrimary else scheme.onSurfaceVariant,
        label = "todayToggleFg"
    )

    Surface(
        shape = CircleShape,
        color = scheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        submit()
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showTodayToggle) Surface(
                onClick = { dueToday = !dueToday },
                shape = CircleShape,
                color = todayBg,
                contentColor = todayFg,
                border = if (dueToday) null else BorderStroke(1.dp, scheme.outlineVariant),
                modifier = Modifier.height(36.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Today", style = MaterialTheme.typography.labelMedium)
                }
            }
            AnimatedVisibility(
                visible = text.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                CircleIconButton(
                    icon = Icons.Rounded.ArrowUpward,
                    contentDescription = "Add task",
                    onClick = submit,
                    containerColor = scheme.onBackground,
                    contentColor = scheme.background,
                    size = 44.dp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AllDoneBanner() {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(quill.successContainer)
            .padding(16.dp)
    ) {
        Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = quill.success, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("You're all caught up", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text("Enjoy the quiet, or plan what's next.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ViewSwitch(view: PlannerView, onChange: (PlannerView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuillChip(stringResource(R.string.planner_view_list), view == PlannerView.LIST, onClick = { onChange(PlannerView.LIST) })
        QuillChip(stringResource(R.string.planner_view_week), view == PlannerView.WEEK, onClick = { onChange(PlannerView.WEEK) })
    }
}

/** Week strip, the chosen day's tasks, and a quick-add that files tasks on that day. */
private fun LazyListScope.weekSection(state: PlannerUiState, actions: PlannerActions) {
    item(key = "week_strip") {
        WeekStrip(state, actions)
        Spacer(Modifier.height(12.dp))
    }
    item(key = "week_add") {
        QuickAddField(
            onAdd = { title, _ -> actions.onQuickAddOn(title, state.selectedDay) },
            placeholder = stringResource(R.string.planner_add_for_day, dayLabel(state.selectedDay, state.now)),
            showTodayToggle = false
        )
    }
    val dayTasks = PlannerStats.tasksByDay(state.tasks)[state.selectedDay].orEmpty()
    item(key = "week_day_label") {
        SectionLabel(dayLabel(state.selectedDay, state.now), count = dayTasks.size, padding = PaddingValues(top = 18.dp, bottom = 8.dp))
    }
    if (dayTasks.isEmpty()) {
        item(key = "week_empty") {
            Text(
                stringResource(R.string.planner_day_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    } else {
        taskItems(dayTasks, state, actions)
    }
    val undated = state.tasks.filter { it.dueDate == null && !it.isCompleted }
    if (undated.isNotEmpty()) {
        item(key = "week_undated_label") {
            SectionLabel(stringResource(R.string.planner_no_due_date), count = undated.size, padding = PaddingValues(top = 18.dp, bottom = 8.dp))
        }
        taskItems(undated, state, actions)
    }
}

@Composable
private fun WeekStrip(state: PlannerUiState, actions: PlannerActions) {
    val quill = QuillTheme.colors
    val scheme = MaterialTheme.colorScheme
    val days = remember(state.weekStart) { PlannerStats.weekDays(state.weekStart) }
    val byDay = remember(state.tasks) { PlannerStats.tasksByDay(state.tasks) }
    val today = startOfDay(state.now)
    val isThisWeek = state.weekStart == PlannerStats.weekStart(state.now)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = quill.card,
        border = BorderStroke(1.dp, quill.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shortDate(days.first()) + " – " + shortDate(days.last()),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                if (!isThisWeek) {
                    TextButton(onClick = { actions.onShiftWeek(0) }) { Text(stringResource(R.string.planner_this_week)) }
                }
                CircleIconButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.planner_previous_week),
                    onClick = { actions.onShiftWeek(-1) },
                    size = 40.dp,
                    containerColor = Color.Transparent
                )
                CircleIconButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.planner_next_week),
                    onClick = { actions.onShiftWeek(1) },
                    size = 40.dp,
                    containerColor = Color.Transparent
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { day ->
                    WeekDayCell(
                        day = day,
                        selected = day == state.selectedDay,
                        isToday = day == today,
                        tasks = byDay[day].orEmpty(),
                        onClick = { actions.onSelectDay(day) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayCell(day: Long, selected: Boolean, isToday: Boolean, tasks: List<NoteEntity>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val open = tasks.filterNot { it.isCompleted }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            weekdayAbbrev(day).take(3),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.onBackground else Color.Transparent)
                .border(if (isToday && !selected) 1.5.dp else 0.dp, if (isToday && !selected) scheme.onBackground else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                dayOfMonth(day).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) scheme.background else scheme.onSurface
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = if (open.isNotEmpty()) open else tasks
            shown.take(3).forEach { t ->
                val c = priorityColor(Priority.of(t.priority)) ?: scheme.primary
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (t.isCompleted) c.copy(alpha = 0.3f) else c))
            }
        }
    }
}
