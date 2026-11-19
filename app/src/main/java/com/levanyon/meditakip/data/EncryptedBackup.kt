package com.levanyon.meditakip.data

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Deliberately carries neither a provider exception nor any input/medical content. */
class EncryptedBackupException : Exception("Encrypted backup could not be opened")

/** Portable envelope v1. The medical payload retains its own existing schema/version. */
object EncryptedBackup {
    const val FORMAT = "MEDITAKIP_ENCRYPTED_BACKUP"
    const val VERSION = 1
    const val ITERATIONS = 600_000
    const val MAX_FILE_BYTES = 14 * 1024 * 1024
    const val MAX_PASSWORD_CHARS = 1024
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val CIPHER = "AES-256-GCM"
    private val fields = setOf("format", "envelopeVersion", "kdf", "iterations", "salt", "cipher", "iv", "ciphertext")

    fun validNewPassword(password: CharArray): Boolean =
        password.size <= MAX_PASSWORD_CHARS && Character.codePointCount(password, 0, password.size) >= 12

    fun passwordsMatch(password: CharArray, confirmation: CharArray): Boolean =
        validNewPassword(password) && password.contentEquals(confirmation)

    class Envelope internal constructor(private val salt: ByteArray, private val iv: ByteArray, private val ciphertext: ByteArray) {
        fun open(password: CharArray): BackupContent = try {
            require(password.size <= MAX_PASSWORD_CHARS)
            val plain = crypt(Cipher.DECRYPT_MODE, ciphertext, password, salt, iv)
            try { BackupCodec.decode(plain.toString(Charsets.UTF_8)).copy(legacyUnencrypted = false) }
            finally { plain.fill(0) }
        } catch (_: Exception) { throw EncryptedBackupException() }
    }

    /** No files or streams are opened until the complete authenticated envelope exists. */
    fun seal(plaintext: ByteArray, password: CharArray): ByteArray {
        require(validNewPassword(password)) { "Invalid backup password length" }
        require(plaintext.size <= BackupCodec.MAX_BYTES) { "Backup exceeds import limit" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val ciphertext = crypt(Cipher.ENCRYPT_MODE, plaintext, password, salt, iv)
        return JSONObject().apply {
            put("format", FORMAT); put("envelopeVersion", VERSION); put("kdf", KDF)
            put("iterations", ITERATIONS); put("salt", encode(salt)); put("cipher", CIPHER)
            put("iv", encode(iv)); put("ciphertext", encode(ciphertext))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    /** All metadata is allowlisted before the expensive KDF. Duplicate keys/coercions are rejected. */
    fun parse(text: String): Envelope = try {
        require(text.length <= MAX_FILE_BYTES)
        BackupCodec.validateJson(text)
        val root = JSONObject(text)
        require(root.keys().asSequence().toSet() == fields)
        require(root.get("format") == FORMAT && root.get("kdf") == KDF && root.get("cipher") == CIPHER)
        require(root.get("envelopeVersion") is Int && root.getInt("envelopeVersion") == VERSION)
        require(root.get("iterations") is Int && root.getInt("iterations") == ITERATIONS)
        fun bytes(name: String, min: Int, max: Int): ByteArray {
            val value = root.get(name)
            require(value is String && value.length <= ((max + 2) / 3) * 4)
            val result = Base64.getDecoder().decode(value)
            require(result.size in min..max && encode(result) == value)
            return result
        }
        Envelope(bytes("salt", 16, 16), bytes("iv", 12, 12), bytes("ciphertext", 16, BackupCodec.MAX_BYTES + 16))
    } catch (_: Exception) { throw EncryptedBackupException() }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    // Canonical UTF-8 AAD, independent of JSON field ordering. Salt/IV are also authenticated.
    private fun aad(salt: ByteArray, iv: ByteArray) =
        "$FORMAT\n$VERSION\n$KDF\n$ITERATIONS\n$CIPHER\n${encode(salt)}\n${encode(iv)}".toByteArray(Charsets.UTF_8)

    private fun crypt(mode: Int, input: ByteArray, password: CharArray, salt: ByteArray, iv: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(salt, iv))
            return cipher.doFinal(input)
        } finally { key.fill(0) }
    }
}

sealed interface BackupImport {
    data class Legacy(val content: BackupContent) : BackupImport
    class Encrypted(val envelope: EncryptedBackup.Envelope) : BackupImport
}
