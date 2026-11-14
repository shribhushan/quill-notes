package com.quillnotes.data.repository

import android.content.Context
import com.quillnotes.data.encryption.NoteCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Attachment files in the app's private storage, encrypted with the device key. */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: NoteCipher
) : AttachmentFiles {

    private val dir = File(context.filesDir, "attachments").apply { mkdirs() }

    private fun file(uuid: String) = File(dir, "${safe(uuid)}.bin")

    // Names come from sync; never let one escape the directory.
    private fun safe(uuid: String) = uuid.filter { it.isLetterOrDigit() || it == '-' }

    override fun write(uuid: String, plain: ByteArray) {
        val target = file(uuid)
        val tmp = File(dir, "${safe(uuid)}.tmp")
        tmp.writeBytes(cipher.encryptBytes(plain, aad(uuid)))
        if (!tmp.renameTo(target)) { // fall back to copy if rename across filesystems fails
            target.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    override fun read(uuid: String): ByteArray? {
        val f = file(uuid)
        if (!f.isFile) return null
        return cipher.decryptBytes(f.readBytes(), aad(uuid))
    }

    override fun exists(uuid: String): Boolean = file(uuid).isFile

    override fun delete(uuid: String) {
        file(uuid).delete()
    }

    private fun aad(uuid: String) = "quill.attachment:$uuid"
}
