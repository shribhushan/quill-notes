package com.quillnotes.data.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupKeyCryptoTest {
    // Few iterations: the KDF cost is not what is under test.
    private val fast = 1_000

    @Test fun `passphrase unwraps the same data key on another phone`() {
        val key = BackupKeyCrypto.newDataKey()
        val info = BackupKeyCrypto.wrap(key, "correct horse".toCharArray(), fast)
        // Simulate the key-info file travelling through the cloud.
        val downloaded = BackupKeyCrypto.decodeKeyInfo(BackupKeyCrypto.encodeKeyInfo(info))
        assertNotNull(downloaded)
        assertArrayEquals(key, BackupKeyCrypto.unwrap(downloaded!!, "correct horse".toCharArray()))
    }

    @Test fun `wrong passphrase does not unwrap`() {
        val info = BackupKeyCrypto.wrap(BackupKeyCrypto.newDataKey(), "right".toCharArray(), fast)
        assertNull(BackupKeyCrypto.unwrap(info, "wrong".toCharArray()))
    }

    @Test fun `corrupt or hostile key info is rejected without throwing`() {
        val info = BackupKeyCrypto.wrap(BackupKeyCrypto.newDataKey(), "p".toCharArray(), fast)
        assertNull(BackupKeyCrypto.unwrap(info.copy(wrappedKey = "!!!"), "p".toCharArray()))
        assertNull(BackupKeyCrypto.unwrap(info.copy(iterations = Int.MAX_VALUE), "p".toCharArray()))
        assertNull(BackupKeyCrypto.unwrap(info.copy(version = 9), "p".toCharArray()))
        assertNull(BackupKeyCrypto.decodeKeyInfo("garbage".toByteArray()))
    }

    @Test fun `each wrap uses a fresh salt`() {
        val key = BackupKeyCrypto.newDataKey()
        val a = BackupKeyCrypto.wrap(key, "p".toCharArray(), fast)
        val b = BackupKeyCrypto.wrap(key, "p".toCharArray(), fast)
        assertNotEquals(a.salt, b.salt)
    }

    @Test fun `note file round trips and is bound to key and file name`() {
        val key = BackupKeyCrypto.newDataKey()
        val blob = BackupKeyCrypto.encryptNote(key, "note_1.qnote", "secret text")
        assertEquals("secret text", BackupKeyCrypto.decryptNote(key, "note_1.qnote", blob))
        assertNull(BackupKeyCrypto.decryptNote(key, "note_2.qnote", blob))
        assertNull(BackupKeyCrypto.decryptNote(BackupKeyCrypto.newDataKey(), "note_1.qnote", blob))
    }

    @Test fun `tampered or foreign files fail closed`() {
        val key = BackupKeyCrypto.newDataKey()
        val blob = BackupKeyCrypto.encryptNote(key, "n.qnote", "x")
        blob[blob.size - 1] = (blob.last() + 1).toByte()
        assertNull(BackupKeyCrypto.decryptNote(key, "n.qnote", blob))
        assertNull(BackupKeyCrypto.decryptNote(key, "n.qnote", byteArrayOf(1, 2, 3)))
        assertNull(BackupKeyCrypto.decryptNote(key, "n.qnote", ByteArray(0)))
    }
}
