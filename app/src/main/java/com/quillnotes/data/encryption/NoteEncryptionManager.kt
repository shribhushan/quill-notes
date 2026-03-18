package com.quillnotes.data.encryption

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles AES-256-GCM encryption/decryption of note content using Google Tink.
 *
 * Key is stored in Android Keystore (hardware-backed on supported devices),
 * making it resistant to extraction even on rooted devices.
 */
@Singleton
class NoteEncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSET_NAME = "quill_notes_keyset"
        private const val PREFERENCE_FILE = "quill_notes_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://quill_notes_master_key"
    }

    private val aead: Aead

    init {
        AeadConfig.register()

        val keysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    /**
     * Encrypts plaintext. Returns Base64-encoded ciphertext.
     * Associated data binds the ciphertext to a context (e.g., note ID)
     * to prevent ciphertext from being swapped between notes.
     */
    fun encrypt(plaintext: String, associatedData: String = ""): String {
        if (plaintext.isEmpty()) return ""
        val ciphertext = aead.encrypt(
            plaintext.toByteArray(Charsets.UTF_8),
            associatedData.toByteArray(Charsets.UTF_8)
        )
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts Base64-encoded ciphertext back to plaintext.
     */
    fun decrypt(encryptedText: String, associatedData: String = ""): String {
        if (encryptedText.isEmpty()) return ""
        return try {
            val ciphertext = Base64.decode(encryptedText, Base64.NO_WRAP)
            val plaintext = aead.decrypt(
                ciphertext,
                associatedData.toByteArray(Charsets.UTF_8)
            )
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (e.g., data was stored before encryption was added),
            // return the original text to prevent data loss
            encryptedText
        }
    }

    /**
     * Encrypts content for cloud export. Uses a separate associated data context
     * so cloud-stored data can be decrypted independently.
     */
    fun encryptForExport(plaintext: String): ByteArray {
        return aead.encrypt(
            plaintext.toByteArray(Charsets.UTF_8),
            "quill_cloud_export".toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Decrypts content imported from cloud.
     */
    fun decryptFromImport(ciphertext: ByteArray): String {
        val plaintext = aead.decrypt(
            ciphertext,
            "quill_cloud_export".toByteArray(Charsets.UTF_8)
        )
        return String(plaintext, Charsets.UTF_8)
    }
}
