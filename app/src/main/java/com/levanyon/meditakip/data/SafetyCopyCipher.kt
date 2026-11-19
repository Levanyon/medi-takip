package com.levanyon.meditakip.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Injectable key ownership; production keys never leave AndroidKeyStore. */
fun interface SafetyCopyKey { fun get(): SecretKey }

object AndroidSafetyCopyKey : SafetyCopyKey {
    private const val ALIAS = "meditakip.restore-safety.v1"
    @Synchronized override fun get(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}

/** Device-only envelope: authenticated version header, 12-byte random IV, ciphertext and 128-bit tag. */
class SafetyCopyCipher(private val key: SafetyCopyKey = AndroidSafetyCopyKey) {
    private val header = "MEDITAKIP_SAFETY_1\n".toByteArray(Charsets.US_ASCII)
    val maxBytes get() = header.size + 12 + BackupCodec.MAX_BYTES + 16

    fun seal(plain: ByteArray): ByteArray {
        require(plain.size <= BackupCodec.MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key.get()) // Provider generates a fresh IV, including AndroidKeyStore.
        require(cipher.iv.size == 12)
        cipher.updateAAD(header)
        return header + cipher.iv + cipher.doFinal(plain)
    }

    fun open(bytes: ByteArray): ByteArray = try {
        require(bytes.size in (header.size + 12 + 16)..maxBytes)
        require(bytes.copyOfRange(0, header.size).contentEquals(header))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key.get(), GCMParameterSpec(128, bytes.copyOfRange(header.size, header.size + 12)))
        cipher.updateAAD(header)
        cipher.doFinal(bytes, header.size + 12, bytes.size - header.size - 12)
    } catch (_: Exception) { throw EncryptedBackupException() }
}
