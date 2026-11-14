package com.quillnotes.data.sync

import android.content.Context
import android.util.Base64
import com.quillnotes.data.encryption.NoteEncryptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the cloud-backup data key on this phone, encrypted under the device
 * Keystore key. The key itself reaches other phones only via the passphrase-wrapped
 * copy in the cloud (see [BackupKeyCrypto]).
 */
@Singleton
class BackupKeyStore @Inject constructor(
    @ApplicationContext context: Context,
    private val encryption: NoteEncryptionManager
) {
    private val prefs = context.getSharedPreferences("quill_backup_key", Context.MODE_PRIVATE)

    fun get(): ByteArray? {
        val stored = prefs.getString(KEY, null) ?: return null
        val cipher = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull() ?: return null
        return encryption.decryptForDevice(cipher, AAD)
    }

    fun has(): Boolean = get() != null

    fun put(dataKey: ByteArray) {
        val cipher = encryption.encryptForDevice(dataKey, AAD)
        prefs.edit().putString(KEY, Base64.encodeToString(cipher, Base64.NO_WRAP)).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "data_key"
        const val AAD = "quill.backup.datakey"
    }
}
