package com.quillnotes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.quillnotes.data.sync.ReminderSink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderSink {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleReminder(taskId: Long, taskTitle: String, dueDateMillis: Long) {
        if (dueDateMillis <= System.currentTimeMillis()) return

        val pendingIntent = buildPendingIntent(taskId, taskTitle)
        // Exact alarms need a permission users can deny (and which is off by default on
        // newer Android). Fall back to an inexact alarm so the reminder still fires, a
        // little late, instead of being dropped.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDateMillis, pendingIntent)
        }
    }

    override fun schedule(taskId: Long, title: String, dueDateMillis: Long) = scheduleReminder(taskId, title, dueDateMillis)

    override fun cancel(taskId: Long) = cancelReminder(taskId)

    fun cancelReminder(taskId: Long) {
        val pendingIntent = buildPendingIntent(taskId, "")
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(taskId: Long, taskTitle: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
