package com.quillnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quillnotes.notifications.PurgeWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class QuillNotesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        schedulePeriodicPurge()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            "task_reminders",
            "Task Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for your scheduled tasks"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun schedulePeriodicPurge() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "purge_deleted_notes",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PurgeWorker>(7, TimeUnit.DAYS).build()
        )
    }
}
