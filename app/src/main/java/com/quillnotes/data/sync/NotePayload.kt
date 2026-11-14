package com.quillnotes.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    val isPurged: Boolean = false
) {
    /** True when the user-visible content is identical, ignoring timestamps. */
    fun sameContentAs(other: NotePayload): Boolean =
        copy(createdAt = 0, updatedAt = 0) == other.copy(createdAt = 0, updatedAt = 0)

    companion object {
        const val FORMAT_VERSION = 2

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
