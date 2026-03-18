package com.quillnotes.di

import com.quillnotes.data.sync.CloudSyncManager
import com.quillnotes.data.sync.GoogleDriveSyncService
import com.quillnotes.data.sync.OneDriveSyncService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideCloudSyncManager(
        googleDrive: GoogleDriveSyncService,
        oneDrive: OneDriveSyncService,
        encryption: com.quillnotes.data.encryption.NoteEncryptionManager
    ): CloudSyncManager = CloudSyncManager(googleDrive, oneDrive, encryption)
}
