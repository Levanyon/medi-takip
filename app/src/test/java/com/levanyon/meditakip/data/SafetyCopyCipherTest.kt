package com.levanyon.meditakip.data

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

/** Test keys are ephemeral and injected; production always uses AndroidKeyStore. */
fun testSafetyCipher(): SafetyCopyCipher {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    return SafetyCopyCipher { key }
}

class SafetyCopyCipherTest {
    @Test fun roundTripIsRandomizedAndContainsNoPlaintext() {
        val cipher = testSafetyCipher()
        val plain = "private person and medication".toByteArray()
        val a = cipher.seal(plain); val b = cipher.seal(plain)
        assertFalse(a.contentEquals(b))
        assertFalse(a.toString(Charsets.UTF_8).contains("private person"))
        assertArrayEquals(plain, cipher.open(a)); assertArrayEquals(plain, cipher.open(b))
    }
    @Test fun corruptionTruncationAndLostKeyFailClosed() {
        val cipher = testSafetyCipher()
        val bytes = cipher.seal("private information".toByteArray())
        for (index in listOf(0, 18, bytes.lastIndex)) {
            val changed = bytes.clone().apply { this[index] = (this[index].toInt() xor 1).toByte() }
            assertThrows(EncryptedBackupException::class.java) { cipher.open(changed) }
        }
        assertThrows(EncryptedBackupException::class.java) { cipher.open(bytes.copyOf(20)) }
        assertThrows(EncryptedBackupException::class.java) { testSafetyCipher().open(bytes) }
        assertThrows(EncryptedBackupException::class.java) { cipher.open(ByteArray(cipher.maxBytes + 1)) }
    }
}
