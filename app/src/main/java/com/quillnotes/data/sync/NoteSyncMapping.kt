package com.quillnotes.data.sync

import com.quillnotes.data.local.entity.NoteEntity
import com.quillnotes.data.local.entity.NoteType

/** File name a note is stored under in the cloud folder. */
fun noteFileName(uuid: String) = "$NOTE_FILE_PREFIX$uuid$NOTE_FILE_SUFFIX"

/** Inverse of [noteFileName]; null for any other file (key file, legacy v1 `note_<id>` files). */
fun uuidFromFileName(name: String): String? {
    if (!name.startsWith(NOTE_FILE_PREFIX) || !name.endsWith(NOTE_FILE_SUFFIX)) return null
    val uuid = name.removePrefix(NOTE_FILE_PREFIX).removeSuffix(NOTE_FILE_SUFFIX)
    return uuid.takeIf { UUID_REGEX.matches(it) }
}

const val BACKUP_KEY_FILE_NAME = "quill_backup_key.qnote"
private const val NOTE_FILE_PREFIX = "note_"
private const val NOTE_FILE_SUFFIX = ".qnote"
private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** [NoteEntity] must already be decrypted. */
fun NoteEntity.toPayload() = NotePayload(
    uuid = uuid, title = title, content = content, type = type.name,
    createdAt = createdAt, updatedAt = updatedAt, isPinned = isPinned, isCompleted = isCompleted,
    dueDate = dueDate, mood = mood, tags = tags, color = color, isTrashed = isDeleted
)

fun NotePayload.noteType(): NoteType = runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.NOTE)
