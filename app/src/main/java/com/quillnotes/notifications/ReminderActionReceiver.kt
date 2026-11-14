package com.quillnotes.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quillnotes.data.repository.NoteRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Done and Snooze buttons on a task reminder without opening the app. */
class ReminderActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun noteRepository(): NoteRepository
        fun alarmScheduler(): AlarmScheduler
    }

    companion object {
        const val ACTION_DONE = "com.quillnotes.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.quillnotes.action.REMINDER_SNOOZE"
        private const val SNOOZE_MINUTES = 60
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderReceiver.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_TITLE).orEmpty()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ActionEntryPoint::class.java)

        context.getSystemService(NotificationManager::class.java).cancel(taskId.toInt())

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE -> entry.alarmScheduler().snooze(taskId, title, SNOOZE_MINUTES)
                    ACTION_DONE -> {
                        // A recurring task starts its next round, so its next reminder is armed too.
                        val updated = entry.noteRepository().setTaskCompleted(taskId, true)
                        val due = updated?.dueDate
                        if (updated != null && !updated.isCompleted && due != null) {
                            entry.alarmScheduler().scheduleReminder(taskId, updated.title, due)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
