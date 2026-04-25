package com.quillnotes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quillnotes.data.repository.NoteRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class RescheduleAlarmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RescheduleEntryPoint {
        fun noteRepository(): NoteRepository
        fun alarmScheduler(): AlarmScheduler
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RescheduleEntryPoint::class.java
        )
        val repository = entryPoint.noteRepository()
        val scheduler = entryPoint.alarmScheduler()

        val now = System.currentTimeMillis()
        repository.getTasksWithDueDateAfter(now).forEach { task ->
            scheduler.scheduleReminder(task.id, task.title, task.dueDate!!)
        }

        return Result.success()
    }
}
