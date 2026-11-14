package com.quillnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import com.quillnotes.widget.refreshWidgets
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import com.quillnotes.data.sync.SyncWorker
import com.quillnotes.notifications.PurgeWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class QuillNotesApp : Application() {

    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var noteRepository: NoteRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        createNotificationChannels()
        schedulePeriodicPurge()
        // Keep the home-screen widgets in step with the task list.
        appScope.launch {
            noteRepository.getTasks().debounce(800).collect { refreshWidgets(this@QuillNotesApp) }
        }
        // Keep the background sync job in step with the Auto-sync preference.
        appScope.launch {
            preferences.autoSync.distinctUntilChanged().collect { SyncWorker.setEnabled(this@QuillNotesApp, it) }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "task_reminders",
                getString(R.string.channel_task_reminders),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.channel_task_reminders_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                "journal_reminder",
                getString(R.string.channel_journal_reminder),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.channel_journal_reminder_desc) }
        )
    }

    private fun schedulePeriodicPurge() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "purge_deleted_notes",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PurgeWorker>(7, TimeUnit.DAYS).build()
        )
    }
}
