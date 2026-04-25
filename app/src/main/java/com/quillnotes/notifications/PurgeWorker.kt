package com.quillnotes.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quillnotes.data.repository.NoteRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn

class PurgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PurgeWorkerEntryPoint {
        fun noteRepository(): NoteRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PurgeWorkerEntryPoint::class.java
        )
        entryPoint.noteRepository().purgeOldDeletedNotes()
        return Result.success()
    }
}
