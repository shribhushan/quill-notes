package com.quillnotes.di

import android.content.Context
import androidx.room.Room
import com.quillnotes.data.encryption.NoteCipher
import com.quillnotes.data.encryption.NoteEncryptionManager
import com.quillnotes.data.local.MIGRATION_1_2
import com.quillnotes.data.local.MIGRATION_2_3
import com.quillnotes.data.local.MIGRATION_3_4
import com.quillnotes.data.local.QuillDatabase
import com.quillnotes.data.local.dao.AttachmentDao
import com.quillnotes.data.local.dao.NoteDao
import com.quillnotes.data.repository.AttachmentFiles
import com.quillnotes.data.repository.AttachmentStore
import com.quillnotes.data.sync.ReminderSink
import com.quillnotes.notifications.AlarmScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuillDatabase {
        return Room.databaseBuilder(
            context,
            QuillDatabase::class.java,
            "quill_notes.db"
        )
            // No destructive fallback: a missing migration must fail loudly rather than
            // silently wipe the user's notes.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    @Singleton
    fun provideNoteDao(database: QuillDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    @Singleton
    fun provideEncryptionManager(@ApplicationContext context: Context): NoteEncryptionManager {
        return NoteEncryptionManager(context)
    }

    @Provides
    @Singleton
    fun provideNoteCipher(manager: NoteEncryptionManager): NoteCipher = manager

    @Provides
    @Singleton
    fun provideAttachmentDao(database: QuillDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    @Singleton
    fun provideAttachmentFiles(store: AttachmentStore): AttachmentFiles = store

    @Provides
    @Singleton
    fun provideReminderSink(scheduler: AlarmScheduler): ReminderSink = scheduler
}
