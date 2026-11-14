package com.quillnotes.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.quillnotes.LaunchRequest
import com.quillnotes.MainActivity
import com.quillnotes.R
import com.quillnotes.data.repository.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The optional daily "time to journal" prompt. Re-arms itself for the next day each time it fires. */
class JournalReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface JournalEntryPoint {
        fun preferences(): PreferencesRepository
        fun alarmScheduler(): AlarmScheduler
    }

    companion object {
        const val CHANNEL_ID = "journal_reminder"
        private const val NOTIFICATION_ID = 9_000_001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, JournalEntryPoint::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val prefs = entry.preferences()
                if (!prefs.journalReminderEnabled.first()) return@launch
                val open = Intent(context, MainActivity::class.java).apply {
                    action = LaunchRequest.ACTION_NEW_JOURNAL
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val tap = PendingIntent.getActivity(
                    context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.journal_reminder_title))
                    .setContentText(context.getString(R.string.journal_reminder_text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build()
                notify(context, NOTIFICATION_ID, notification)
                entry.alarmScheduler().scheduleJournalReminder(prefs.journalReminderMinutes.first())
            } finally {
                pending.finish()
            }
        }
    }
}
