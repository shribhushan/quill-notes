package com.quillnotes.data.repository

/** Where attachment bytes live on disk (always encrypted at rest). */
interface AttachmentFiles {
    /** Encrypts [plain] and stores it under [uuid]. */
    fun write(uuid: String, plain: ByteArray)

    /** The decrypted bytes, or null if the file is missing or cannot be decrypted. */
    fun read(uuid: String): ByteArray?

    fun exists(uuid: String): Boolean

    fun delete(uuid: String)
}
