package com.quillnotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quillnotes.data.local.converter.Converters
import com.quillnotes.data.local.dao.NoteDao
import com.quillnotes.data.local.entity.NoteEntity

@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QuillDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
