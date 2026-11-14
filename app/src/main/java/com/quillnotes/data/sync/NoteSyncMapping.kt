package com.quillnotes.data.sync

import com.quillnotes.data.local.entity.AttachmentEntity
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
fun NoteEntity.toPayload(attachments: List<AttachmentRef> = emptyList()) = NotePayload(
    uuid = uuid, title = title, content = content, type = type.name,
    createdAt = createdAt, updatedAt = updatedAt, isPinned = isPinned, isCompleted = isCompleted,
    dueDate = dueDate, mood = mood, tags = tags, color = color, isTrashed = isDeleted,
    priority = priority, repeatRule = repeatRule, notebook = notebook, isArchived = isArchived,
    isTemplate = isTemplate, isLocked = isLocked, attachments = attachments
)

fun AttachmentEntity.toRef() = AttachmentRef(uuid, mimeType, width, height, sizeBytes, createdAt)

/** A local note together with its attachment list, as the sync engine reads it. */
data class SyncNote(val note: NoteEntity, val attachments: List<AttachmentRef> = emptyList()) {
    fun toPayload(): NotePayload = note.toPayload(attachments)
}

/** File name an attachment's encrypted bytes are stored under in the cloud folder. */
fun attachmentFileName(uuid: String) = "att_$uuid.qatt"

/** Inverse of [attachmentFileName]. */
fun attachmentUuidFromFileName(name: String): String? {
    if (!name.startsWith("att_") || !name.endsWith(".qatt")) return null
    val uuid = name.removePrefix("att_").removeSuffix(".qatt")
    return uuid.takeIf { UUID_REGEX.matches(it) }
}

fun NotePayload.noteType(): NoteType = runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.NOTE)
