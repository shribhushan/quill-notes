package com.quillnotes.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.quillnotes.LaunchRequest
import com.quillnotes.MainActivity
import com.quillnotes.R

/** Fires when a task is due: a notification that opens the task, with Done and Snooze actions. */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val CHANNEL_ID = "task_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)?.takeIf { it.isNotBlank() }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = LaunchRequest.ACTION_OPEN_TASK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val tapPending = PendingIntent.getActivity(
            context, taskId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_task_due))
            .setContentText(taskTitle ?: context.getString(R.string.reminder_untitled_task))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapPending)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.reminder_done), actionIntent(context, taskId, taskTitle, ReminderActionReceiver.ACTION_DONE))
            .addAction(0, context.getString(R.string.reminder_snooze), actionIntent(context, taskId, taskTitle, ReminderActionReceiver.ACTION_SNOOZE))
            .build()

        notify(context, taskId.toInt(), notification)
    }

    private fun actionIntent(context: Context, taskId: Long, title: String?, action: String): PendingIntent {
        val i = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title.orEmpty())
        }
        // Distinct request code per task and action so the two buttons never share an intent.
        return PendingIntent.getBroadcast(
            context, (taskId * 4 + if (action == ReminderActionReceiver.ACTION_DONE) 1 else 2).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Posts a notification only if the user has allowed notifications (Android 13+). */
internal fun notify(context: Context, id: Int, notification: android.app.Notification) {
    val allowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (allowed) context.getSystemService(NotificationManager::class.java).notify(id, notification)
}
