package com.quillnotes.data.encryption

/**
 * The at-rest encryption the repository needs. [NoteEncryptionManager] is the Android Keystore
 * implementation; tests substitute a plain one.
 */
interface NoteCipher {
    /** Encrypts text; returns Base64. Empty input stays empty. */
    fun encrypt(plaintext: String, associatedData: String = ""): String

    /** Decrypts [encryptedText]; returns it unchanged if it does not authenticate. */
    fun decrypt(encryptedText: String, associatedData: String = ""): String

    /** Raw-bytes variant used for attachment files. */
    fun encryptBytes(plain: ByteArray, associatedData: String): ByteArray

    /** Returns null if the bytes do not authenticate. */
    fun decryptBytes(cipher: ByteArray, associatedData: String): ByteArray?
}
