package com.quillnotes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quillnotes.data.repository.NoteRepository
import com.quillnotes.data.repository.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** After a reboot: re-arm every pending task reminder and the daily journal prompt. */
class RescheduleAlarmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RescheduleEntryPoint {
        fun noteRepository(): NoteRepository
        fun alarmScheduler(): AlarmScheduler
        fun preferences(): PreferencesRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RescheduleEntryPoint::class.java
        )
        val repository = entryPoint.noteRepository()
        val scheduler = entryPoint.alarmScheduler()
        val prefs = entryPoint.preferences()

        val now = System.currentTimeMillis()
        repository.getTasksWithDueDateAfter(now).forEach { task ->
            scheduler.scheduleReminder(task.id, task.title, task.dueDate!!)
        }
        if (prefs.journalReminderEnabled.first()) {
            scheduler.scheduleJournalReminder(prefs.journalReminderMinutes.first())
        }
        return Result.success()
    }
}
