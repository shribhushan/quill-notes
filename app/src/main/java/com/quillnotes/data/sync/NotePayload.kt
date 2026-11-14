package com.quillnotes.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An image (or other file) attached to a note; its bytes are a separate cloud file, `att_<uuid>.qatt`. */
@Serializable
data class AttachmentRef(
    val uuid: String,
    val mime: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0,
    val createdAt: Long = 0
)

/**
 * One note as stored in the cloud (before encryption). Versioned so later releases
 * can add fields without older apps misreading the file.
 */
@Serializable
data class NotePayload(
    @SerialName("v") val formatVersion: Int = FORMAT_VERSION,
    val uuid: String,
    val title: String = "",
    val content: String = "",
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val mood: String? = null,
    val tags: String = "",
    val color: String? = null,
    /** In the trash (restorable). */
    val isTrashed: Boolean = false,
    /** Tombstone: permanently deleted; all other content fields are blank. */
    val isPurged: Boolean = false,
    val priority: Int = 0,
    val repeatRule: String? = null,
    val notebook: String? = null,
    val isArchived: Boolean = false,
    val isTemplate: Boolean = false,
    val isLocked: Boolean = false,
    val attachments: List<AttachmentRef> = emptyList()
) {
    /** True when the user-visible content is identical, ignoring timestamps. */
    fun sameContentAs(other: NotePayload): Boolean =
        copy(createdAt = 0, updatedAt = 0) == other.copy(createdAt = 0, updatedAt = 0)

    companion object {
        const val FORMAT_VERSION = 3

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun tombstone(uuid: String, type: String, deletedAt: Long) = NotePayload(
            uuid = uuid, type = type, createdAt = deletedAt, updatedAt = deletedAt, isPurged = true
        )

        fun encode(payload: NotePayload): String = json.encodeToString(serializer(), payload)

        /** Returns null if [text] is not a payload this app understands. */
        fun decode(text: String): NotePayload? =
            runCatching { json.decodeFromString(serializer(), text) }
                .getOrNull()
                ?.takeIf { it.formatVersion <= FORMAT_VERSION && it.uuid.isNotBlank() }
    }
}
