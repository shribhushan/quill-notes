package com.quillnotes.data.export

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.quillnotes.data.sync.BackupKeyCrypto
import com.quillnotes.data.sync.NotePayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BadPassphraseException : Exception("That passphrase doesn't open this backup.")
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * `.qbackup`: a passphrase-encrypted archive of every note (and image) that Quill can read back
 * on any phone, with no cloud account involved.
 *
 * Layout: magic line, a length-prefixed JSON header (key-derivation parameters), then a Tink
 * streaming-AEAD (AES-GCM, 64 KB segments) stream. Inside is a ZIP with `manifest.json`,
 * `notes/<uuid>.json` and `attachments/<uuid>.bin`. Streaming keeps memory flat however many
 * images there are.
 */
object BackupFile {
    private val MAGIC = "QUILLBACKUP1\n".toByteArray()
    private const val VERIFIER = "quill-backup-ok"
    private const val AAD = "quill.backup.v1"
    private const val SEGMENT_BYTES = 1 shl 16
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Header(val version: Int = 1, val kdf: String = "PBKDF2WithHmacSHA256", val iterations: Int, val salt: String)

    @Serializable
    data class Manifest(val format: Int = 1, val createdAt: Long, val noteCount: Int, val attachmentCount: Int, val app: String = "quill-notes")

    private fun streaming(key: ByteArray) = AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT_BYTES, 0)

    /** Encrypts to [out] (closing it). [attachmentBytes] returns null for images not on this phone. */
    fun write(
        out: OutputStream,
        passphrase: CharArray,
        notes: List<NotePayload>,
        attachmentBytes: (String) -> ByteArray?,
        now: Long = System.currentTimeMillis(),
        iterations: Int = BackupKeyCrypto.DEFAULT_ITERATIONS
    ): Manifest {
        val salt = ByteArray(16).also(random::nextBytes)
        val key = BackupKeyCrypto.deriveKey(passphrase, salt, iterations)
        val header = json.encodeToString(Header.serializer(), Header(iterations = iterations, salt = Base64.getEncoder().encodeToString(salt)))
            .toByteArray()

        val dataOut = DataOutputStream(out)
        dataOut.write(MAGIC)
        dataOut.writeInt(header.size)
        dataOut.write(header)
        dataOut.flush()

        val attachments = LinkedHashMap<String, ByteArray>()
        for (p in notes) for (a in p.attachments) {
            if (a.uuid in attachments) continue
            attachmentBytes(a.uuid)?.let { attachments[a.uuid] = it }
        }
        val manifest = Manifest(createdAt = now, noteCount = notes.size, attachmentCount = attachments.size)

        streaming(key).newEncryptingStream(out, AAD.toByteArray()).use { enc ->
            enc.write(VERIFIER.toByteArray())
            ZipOutputStream(enc).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                for (p in notes) {
                    zip.putNextEntry(ZipEntry("notes/${p.uuid}.json"))
                    zip.write(NotePayload.encode(p).toByteArray())
                    zip.closeEntry()
                }
                for ((uuid, bytes) in attachments) {
                    zip.putNextEntry(ZipEntry("attachments/$uuid.bin"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        return manifest
    }

    /**
     * Decrypts [input], calling [onNote] for each note and then [onAttachment] for each image
     * (notes come first so a reader can create rows before their files arrive).
     * @throws BadPassphraseException wrong passphrase
     * @throws InvalidBackupException not a Quill backup, damaged, or from a newer version
     */
    fun read(
        input: InputStream,
        passphrase: CharArray,
        onNote: (NotePayload) -> Unit,
        onAttachment: (uuid: String, bytes: ByteArray) -> Unit
    ): Manifest {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try { data.readFully(magic) } catch (e: IOException) { throw InvalidBackupException("This file is not a Quill backup.", e) }
        if (!magic.contentEquals(MAGIC)) throw InvalidBackupException("This file is not a Quill backup.")
        val headerLength = try { data.readInt() } catch (e: IOException) { throw InvalidBackupException("This backup is damaged.", e) }
        if (headerLength !in 1..4096) throw InvalidBackupException("This backup is damaged.")
        val headerBytes = ByteArray(headerLength)
        try { data.readFully(headerBytes) } catch (e: IOException) { throw InvalidBackupException("This backup is damaged.", e) }
        val header = runCatching { json.decodeFromString(Header.serializer(), String(headerBytes)) }.getOrNull()
            ?: throw InvalidBackupException("This backup is damaged.")
        if (header.version != 1) throw InvalidBackupException("This backup was made by a newer version of Quill.")
        if (header.iterations !in 1..10_000_000) throw InvalidBackupException("This backup is damaged.")

        val key = try {
            BackupKeyCrypto.deriveKey(passphrase, Base64.getDecoder().decode(header.salt), header.iterations)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This backup is damaged.", e)
        }

        val dec = try {
            streaming(key).newDecryptingStream(input, AAD.toByteArray())
        } catch (e: GeneralSecurityException) {
            throw BadPassphraseException()
        }
        try {
            val check = ByteArray(VERIFIER.length)
            DataInputStream(dec).readFully(check)
            if (String(check) != VERIFIER) throw BadPassphraseException()
        } catch (e: IOException) {
            throw BadPassphraseException() // the first segment would not authenticate
        }

        var manifest: Manifest? = null
        try {
            val zip = ZipInputStream(dec)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val bytes = readLimited(zip, name)
                when {
                    name == "manifest.json" -> manifest = json.decodeFromString(Manifest.serializer(), String(bytes))
                    name.startsWith("notes/") && name.endsWith(".json") ->
                        NotePayload.decode(String(bytes))?.let(onNote) // unreadable/newer notes are skipped
                    name.startsWith("attachments/") && name.endsWith(".bin") ->
                        onAttachment(name.removePrefix("attachments/").removeSuffix(".bin"), bytes)
                }
            }
        } catch (e: IOException) {
            throw InvalidBackupException("This backup is damaged or incomplete.", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw InvalidBackupException("This backup is damaged.", e)
        }
        return manifest ?: throw InvalidBackupException("This backup is incomplete.")
    }

    private fun readLimited(zip: ZipInputStream, name: String): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(chunk)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IOException("$name is too large")
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }
}

enum class ImportAction { ADD, REPLACE, SKIP }

/** What importing a backed-up note should do given what is on the phone. Never loses a newer local edit. */
object ImportPolicy {
    fun decide(localUpdatedAt: Long?, backup: NotePayload): ImportAction = when {
        backup.isPurged -> ImportAction.SKIP
        localUpdatedAt == null -> ImportAction.ADD
        backup.updatedAt > localUpdatedAt -> ImportAction.REPLACE
        else -> ImportAction.SKIP
    }
}
