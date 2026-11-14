package com.quillnotes.data.sync

import com.google.crypto.tink.subtle.AesGcmJce
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The backup data key and its passphrase wrapping.
 *
 * Cloud files are encrypted with a random 256-bit *data key*. That key is stored in the
 * cloud only wrapped (AES-GCM) by a key derived from the user's recovery passphrase
 * (PBKDF2-HMAC-SHA256), so the provider cannot read notes and a new phone can recover
 * the data key from the passphrase alone. Changing the passphrase re-wraps the key
 * without re-encrypting any notes.
 *
 * No Android dependencies, so it is unit-tested on the JVM.
 */
object BackupKeyCrypto {

    const val DEFAULT_ITERATIONS = 600_000
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val WRAP_AAD = "quill.keywrap.v1"
    private val NOTE_MAGIC = byteArrayOf('Q'.code.toByte(), 'N'.code.toByte(), 2, 0)
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Stored in the cloud as `quill_backup_key.qnote`. Contains nothing usable without the passphrase. */
    @Serializable
    data class KeyInfo(
        val version: Int = 1,
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int,
        val salt: String,
        val wrappedKey: String
    )

    fun newDataKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun wrap(dataKey: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): KeyInfo {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val kek = deriveKey(passphrase, salt, iterations)
        val wrapped = AesGcmJce(kek).encrypt(dataKey, WRAP_AAD.toByteArray())
        return KeyInfo(
            iterations = iterations,
            salt = Base64.getEncoder().encodeToString(salt),
            wrappedKey = Base64.getEncoder().encodeToString(wrapped)
        )
    }

    /** Returns the data key, or null if the passphrase is wrong or [info] is unusable. */
    fun unwrap(info: KeyInfo, passphrase: CharArray): ByteArray? = try {
        if (info.version != 1 || info.iterations !in 1..10_000_000) null
        else {
            val kek = deriveKey(passphrase, Base64.getDecoder().decode(info.salt), info.iterations)
            AesGcmJce(kek).decrypt(Base64.getDecoder().decode(info.wrappedKey), WRAP_AAD.toByteArray())
        }
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    fun encodeKeyInfo(info: KeyInfo): ByteArray = json.encodeToString(KeyInfo.serializer(), info).toByteArray()

    fun decodeKeyInfo(bytes: ByteArray): KeyInfo? =
        runCatching { json.decodeFromString(KeyInfo.serializer(), String(bytes)) }.getOrNull()

    /** Encrypts one note file. [fileName] is bound in as associated data so files cannot be swapped. */
    fun encryptNote(dataKey: ByteArray, fileName: String, plaintext: String): ByteArray =
        NOTE_MAGIC + AesGcmJce(dataKey).encrypt(plaintext.toByteArray(), aad(fileName))

    /** Returns null if the bytes are not a v2 note file or do not authenticate under [dataKey]. */
    fun decryptNote(dataKey: ByteArray, fileName: String, data: ByteArray): String? {
        if (data.size <= NOTE_MAGIC.size || !data.copyOfRange(0, NOTE_MAGIC.size).contentEquals(NOTE_MAGIC)) return null
        return try {
            String(AesGcmJce(dataKey).decrypt(data.copyOfRange(NOTE_MAGIC.size, data.size), aad(fileName)))
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    private fun aad(fileName: String) = "quill.note.v2:$fileName".toByteArray()

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
