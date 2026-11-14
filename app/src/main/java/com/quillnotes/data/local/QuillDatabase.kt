package com.quillnotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quillnotes.data.local.converter.Converters
import com.quillnotes.data.local.dao.NoteDao
import com.quillnotes.data.local.dao.AttachmentDao
import com.quillnotes.data.local.entity.AttachmentEntity
import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.TombstoneEntity

@Database(
    entities = [NoteEntity::class, TombstoneEntity::class, AttachmentEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QuillDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
}
