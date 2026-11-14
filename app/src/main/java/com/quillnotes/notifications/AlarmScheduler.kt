package com.quillnotes.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.quillnotes.data.sync.ReminderSink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderSink {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** True if exact alarms are allowed (they are denied by default for most apps on Android 14+). */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleReminder(taskId: Long, taskTitle: String, dueDateMillis: Long) {
        if (dueDateMillis <= System.currentTimeMillis()) return
        arm(dueDateMillis, taskPendingIntent(taskId, taskTitle))
    }

    fun cancelReminder(taskId: Long) {
        alarmManager.cancel(taskPendingIntent(taskId, ""))
    }

    override fun schedule(taskId: Long, title: String, dueDateMillis: Long) = scheduleReminder(taskId, title, dueDateMillis)

    override fun cancel(taskId: Long) = cancelReminder(taskId)

    /** Re-arms a reminder for a task the user snoozed from its notification. */
    fun snooze(taskId: Long, taskTitle: String, minutes: Int) {
        scheduleReminder(taskId, taskTitle, System.currentTimeMillis() + minutes * 60_000L)
    }

    // ── Daily journal reminder ─────────────────────────────────

    /** Arms the next occurrence of the daily journal prompt at [minutesOfDay] (the receiver re-arms it). */
    fun scheduleJournalReminder(minutesOfDay: Int) {
        arm(nextOccurrence(minutesOfDay, System.currentTimeMillis()), journalPendingIntent())
    }

    fun cancelJournalReminder() {
        alarmManager.cancel(journalPendingIntent())
    }

    // ── Internals ──────────────────────────────────────────────

    /**
     * Exact alarms need a permission users can deny. Fall back to an inexact alarm so the
     * reminder still fires, a little late, instead of being dropped.
     */
    private fun arm(atMillis: Long, intent: PendingIntent) {
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun taskPendingIntent(taskId: Long, taskTitle: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
        }
        return PendingIntent.getBroadcast(
            context, taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun journalPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, JOURNAL_REQUEST_CODE, Intent(context, JournalReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val JOURNAL_REQUEST_CODE = 9_000_001

        /** The next time after [now] that the wall clock reads [minutesOfDay]. */
        fun nextOccurrence(minutesOfDay: Int, now: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
                set(Calendar.MINUTE, minutesOfDay % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
    }
}
