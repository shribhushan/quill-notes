package com.quillnotes.data.local.converter

import androidx.room.TypeConverter
import com.quillnotes.data.local.entity.NoteType

class Converters {
    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType = NoteType.valueOf(value)
}
