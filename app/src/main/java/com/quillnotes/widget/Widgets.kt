package com.quillnotes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Button
import com.quillnotes.LaunchRequest
import com.quillnotes.MainActivity
import com.quillnotes.R
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.domain.PlannerStats
import com.quillnotes.notifications.AlarmScheduler
import com.quillnotes.ui.util.startOfDay
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun noteRepository(): NoteRepository
    fun alarmScheduler(): AlarmScheduler
}

private val Paper = Color(0xFFF7F3EC)
private val Midnight = Color(0xFF1C1B19)
private val InkDay = Color(0xFF1C1B19)
private val InkNight = Color(0xFFF1ECE2)
private val Accent = Color(0xFF3344D6)

private val surface = ColorProvider(day = Paper, night = Midnight)
private val ink = ColorProvider(day = InkDay, night = InkNight)
private val mutedInk = ColorProvider(day = Color(0xFF6A6358), night = Color(0xFFB7B0A3))

private fun launch(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        this.action = action
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

// ── Quick capture ──────────────────────────────────────────────

/** Three buttons that open straight into a new note, task or voice note. */
class QuickCaptureWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickCaptureContent(context) }
    }
}

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()
}

@Composable
private fun QuickCaptureContent(context: Context) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(surface).cornerRadius(24.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = context.getString(R.string.widget_quick_title),
            style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Button(
                text = context.getString(R.string.widget_new_note),
                onClick = actionStartActivity(launch(context, LaunchRequest.ACTION_NEW_NOTE)),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            Button(
                text = context.getString(R.string.widget_new_task),
                onClick = actionStartActivity(launch(context, LaunchRequest.ACTION_NEW_TASK)),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            Button(
                text = context.getString(R.string.widget_voice),
                onClick = actionStartActivity(launch(context, LaunchRequest.ACTION_NEW_VOICE)),
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

// ── Today's tasks ──────────────────────────────────────────────

private val TaskIdKey = ActionParameters.Key<Long>("task_id")

/** Overdue and due-today tasks, tickable straight from the home screen. */
class TodayTasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val now = System.currentTimeMillis()
        val endOfToday = startOfDay(now) + 24L * 60 * 60 * 1000
        val tasks = entry.noteRepository().getTasks().first()
            .filter { !it.isCompleted && !it.isLocked && it.dueDate != null && it.dueDate!! < endOfToday }
            .sortedWith(PlannerStats.taskOrder)
        provideContent { TodayContent(context, tasks) }
    }
}

class TodayTasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}

@Composable
private fun TodayContent(context: Context, tasks: List<NoteEntity>) {
    Column(modifier = GlanceModifier.fillMaxSize().background(surface).cornerRadius(24.dp).padding(14.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.widget_today_title),
                style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launch(context, LaunchRequest.ACTION_OPEN_PLANNER)))
            )
            Button(
                text = "+",
                onClick = actionStartActivity(launch(context, LaunchRequest.ACTION_NEW_TASK))
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (tasks.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_today_empty),
                style = TextStyle(color = mutedInk, fontSize = 14.sp)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(tasks, itemId = { it.id }) { task ->
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<CompleteTaskCallback>(actionParametersOf(TaskIdKey to task.id)),
                        text = task.title.ifBlank { context.getString(R.string.untitled_task) },
                        style = TextStyle(color = ink, fontSize = 14.sp),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/** Ticking a task on the widget completes it (a recurring one starts its next round) and refreshes. */
class CompleteTaskCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val updated = entry.noteRepository().setTaskCompleted(taskId, true)
        val due = updated?.dueDate
        if (updated != null && !updated.isCompleted && due != null && due > System.currentTimeMillis()) {
            entry.alarmScheduler().scheduleReminder(updated.id, updated.title, due)
        } else {
            entry.alarmScheduler().cancelReminder(taskId)
        }
        TodayTasksWidget().updateAll(context)
    }
}

/** Refreshes the widgets after the data changed. Cheap when none are on the home screen. */
suspend fun refreshWidgets(context: Context) {
    TodayTasksWidget().updateAll(context)
}
