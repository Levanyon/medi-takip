package com.levanyon.meditakip.data

import android.app.Application
import androidx.room.Room
import com.levanyon.meditakip.domain.Snapshot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.*
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EncryptedBackupTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private lateinit var prefs: Preferences
    private lateinit var backups: Backups
    private val context get() = RuntimeEnvironment.getApplication()
    private val clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 10, 11).toEpochDay()
    private val password get() = "uzun parola İstanbul 🔐".toCharArray()
    private val data get() = Snapshot(
        people = listOf(PersonEntity("p", "PRIVATE_PERSON", "PRIVATE_NOTE")),
        medications = listOf(MedicationEntity("m", "p", stockEnabled = true, stockQuantity = 12.0,
            stockPerDose = 0.5, stockThreshold = 2.0, prescriptionDate = day, renewalDate = day + 30,
            renewalReminderEnabled = true, gtin = "08699580090635")),
        schedules = listOf(MedicationScheduleEntity("s:legacy", "m", "PRIVATE_PERSON", "PRIVATE_MEDICINE", "1 tablet", "PRIVATE_DOSE_NOTE",
            "12:00", 127, day, null, true, day)),
        logs = listOf(MedicationLogEntity("s:legacy:$day:720", "s:legacy", day, 720, clock.millis(), note = "PRIVATE_HISTORY")),
        packages = listOf(MedicationPackageEntity("pkg", "m", "08699580090635", "SERIAL", "LOT", "2028-12-31", clock.millis(), 12.0)))

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, MediDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, clock); prefs = Preferences(context)
        backups = Backups(repo, prefs, context.filesDir, "test", clock, safetyCipher = testSafetyCipher())
        repo.replaceAll(data)
    }
    @After fun close() { db.close() }
    private fun exported(): ByteArray = ByteArrayOutputStream().also { runBlocking { backups.export(it, password) } }.toByteArray()
    private fun legacy() = BackupCodec.encode(data, AppSettings(), clock.millis(), "test")
    private fun changed(change: (JSONObject) -> Unit) = JSONObject(exported().toString(Charsets.UTF_8)).also(change).toString().toByteArray()
    private fun assertRejected(bytes: ByteArray, pass: CharArray = password) = runBlocking {
        val before = repo.snapshot(); val settings = prefs.state.value
        val files = context.filesDir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(context.filesDir).path to it.readBytes().toList() }
        val error = assertThrows(EncryptedBackupException::class.java) {
            runBlocking { backups.restore(backups.inspect(bytes.inputStream(), pass)) }
        }
        assertEquals("Encrypted backup could not be opened", error.message); assertNull(error.cause)
        assertEquals(before, repo.snapshot()); assertEquals(settings, prefs.state.value)
        assertEquals(files, context.filesDir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(context.filesDir).path to it.readBytes().toList() })
    }

    @Test fun newExportHasOnlyCryptoMetadataAndNoMedicalIdentityOrPassword() {
        val text = exported().toString(Charsets.UTF_8)
        listOf("PRIVATE_PERSON", "PRIVATE_MEDICINE", "PRIVATE_NOTE", "PRIVATE_HISTORY", "08699580090635", String(password)).forEach { assertFalse(text.contains(it)) }
        val root = JSONObject(text)
        assertEquals(setOf("format", "envelopeVersion", "kdf", "iterations", "salt", "cipher", "iv", "ciphertext"), root.keys().asSequence().toSet())
        assertEquals("MEDITAKIP_ENCRYPTED_BACKUP", root.getString("format")); assertEquals(1, root.getInt("envelopeVersion"))
        assertEquals("PBKDF2-HMAC-SHA256", root.getString("kdf")); assertEquals(600000, root.getInt("iterations"))
        assertEquals("AES-256-GCM", root.getString("cipher"))
        assertEquals(16, Base64.getDecoder().decode(root.getString("salt")).size)
        assertEquals(12, Base64.getDecoder().decode(root.getString("iv")).size)
    }
    @Test fun freshSaltIvAndCiphertextForEveryExport() {
        val a = JSONObject(exported().toString(Charsets.UTF_8)); val b = JSONObject(exported().toString(Charsets.UTF_8))
        listOf("salt", "iv", "ciphertext").forEach { assertNotEquals(a.getString(it), b.getString(it)) }
    }
    @Test fun portableUnicodePasswordRoundTripPreservesEveryMedicalTable() = runBlocking {
        val bytes = exported()
        repo.replaceAll(Snapshot())
        val otherInstance = Backups(repo, prefs, context.filesDir, "another-install", clock, safetyCipher = testSafetyCipher())
        val candidate = otherInstance.inspect(bytes.inputStream(), password)
        assertFalse(candidate.legacyUnencrypted)
        otherInstance.restore(candidate)
        assertEquals(data, repo.snapshot())
    }
    @Test fun independentPlatformDecryptionVerifiesParametersAndAad() {
        val root = JSONObject(exported().toString(Charsets.UTF_8))
        val salt = Base64.getDecoder().decode(root.getString("salt")); val iv = Base64.getDecoder().decode(root.getString("iv"))
        val spec = PBEKeySpec(password, salt, 600000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)); key.fill(0)
        cipher.updateAAD("MEDITAKIP_ENCRYPTED_BACKUP\n1\nPBKDF2-HMAC-SHA256\n600000\nAES-256-GCM\n${root.getString("salt")}\n${root.getString("iv")}".toByteArray())
        val plaintext = cipher.doFinal(Base64.getDecoder().decode(root.getString("ciphertext")))
        assertEquals(data, BackupCodec.decode(plaintext.toString(Charsets.UTF_8)).snapshot); plaintext.fill(0)
    }
    @Test fun wrongPasswordLeavesDatabasePreferencesAndFilesUntouched() { assertRejected(exported(), "a wrong password".toCharArray()) }
    @Test fun shortAndEmptyExportPasswordsWriteNothing() = runBlocking {
        for (pass in listOf("", "short", "12345678901")) {
            val output = ByteArrayOutputStream()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { backups.export(output, pass.toCharArray()) } }
            assertEquals(0, output.size())
        }
    }
    @Test fun confirmationMustMatchAndUnicodeSpacesAreAllowed() {
        assertTrue(EncryptedBackup.passwordsMatch(password, password))
        assertFalse(EncryptedBackup.passwordsMatch(password, "different value".toCharArray()))
        assertFalse(EncryptedBackup.validNewPassword("🔐".repeat(6).toCharArray()))
        assertTrue(EncryptedBackup.validNewPassword("🔐".repeat(12).toCharArray()))
    }
    private fun flipped(field: String, last: Boolean = false) = changed {
        val bytes = Base64.getDecoder().decode(it.getString(field)); val index = if (last) bytes.lastIndex else 0
        bytes[index] = (bytes[index].toInt() xor 1).toByte()
        it.put(field, Base64.getEncoder().encodeToString(bytes))
    }
    @Test fun modifiedCiphertextFailsWithoutMutation() { assertRejected(flipped("ciphertext")) }
    @Test fun modifiedAuthenticationTagFailsWithoutMutation() { assertRejected(flipped("ciphertext", true)) }
    @Test fun modifiedIvFailsWithoutMutation() { assertRejected(flipped("iv")) }
    @Test fun modifiedSaltFailsWithoutMutation() { assertRejected(flipped("salt")) }
    @Test fun malformedBase64AndTruncatedCiphertextAreRejected() {
        for (value in listOf("!!", "AA==", "")) assertRejected(changed { it.put("ciphertext", value) })
    }
    @Test fun unsupportedVersionsAlgorithmsAndIterationsAreRejectedBeforeKdf() {
        val original = JSONObject(exported().toString(Charsets.UTF_8)).toString()
        for ((field, value) in listOf("envelopeVersion" to 2, "envelopeVersion" to "1", "kdf" to "anything",
            "cipher" to "AES/ECB/NoPadding", "iterations" to 1, "iterations" to 2147483647, "iterations" to "600000")) {
            assertRejected(JSONObject(original).put(field, value).toString().toByteArray())
        }
    }
    @Test fun missingExtraDuplicateAndOversizedMetadataAreRejected() {
        val original = exported().toString(Charsets.UTF_8)
        assertRejected(JSONObject(original).apply { remove("iv") }.toString().toByteArray())
        assertRejected(JSONObject(original).put("unexpected", "value").toString().toByteArray())
        assertRejected(original.replace("\"iterations\":600000", "\"iterations\":600000,\"iterations\":600000").toByteArray())
        for (field in listOf("salt", "iv")) assertRejected(JSONObject(original).put(field, "A".repeat(10000)).toString().toByteArray())
    }
    @Test fun encryptedMalformedInnerPayloadDoesNotRestore() {
        assertRejected(EncryptedBackup.seal("not a backup".toByteArray(), password))
    }
    @Test fun invalidSaltAndIvLengthsAreRejectedWithoutMutation() {
        val original = exported().toString(Charsets.UTF_8)
        for ((field, lengths) in listOf("salt" to listOf(0, 15, 17), "iv" to listOf(0, 11, 13))) {
            for (length in lengths) {
                val value = Base64.getEncoder().encodeToString(ByteArray(length))
                assertRejected(JSONObject(original).put(field, value).toString().toByteArray())
            }
        }
    }
    @Test fun encryptedUnsupportedInnerVersionsDoNotRestore() {
        for (field in listOf("formatVersion", "schemaVersion")) {
            val invalid = JSONObject(legacy()).put(field, 99).toString().toByteArray()
            assertRejected(EncryptedBackup.seal(invalid, password))
        }
    }
    @Test fun encryptedInvalidDomainDataDoesNotRestore() {
        val invalid = JSONObject(legacy()).apply { getJSONArray("medications").getJSONObject(0).put("personId", "missing") }
        assertRejected(EncryptedBackup.seal(invalid.toString().toByteArray(), password))
    }
    @Test fun legacyImportIsIdentifiableAndNextExportIsEncrypted() = runBlocking {
        val source = legacy().toByteArray()
        val file = backups.open(source.inputStream()) as BackupImport.Legacy
        assertTrue(file.content.legacyUnencrypted)
        repo.replaceAll(Snapshot()); backups.restore(file.content)
        assertEquals(data, repo.snapshot())
        assertTrue(backups.open(exported().inputStream()) is BackupImport.Encrypted)
        assertArrayEquals(legacy().toByteArray(), source)
    }
    @Test fun arbitraryJsonIsNotABackup() { assertRejected("{}".toByteArray()) }
    @Test fun oversizedStreamStopsAtLimitBeforeParsingOrDerivingKey() {
        var read = 0
        val stream = object : java.io.InputStream() {
            override fun read(): Int { read++; return ' '.code }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                java.util.Arrays.fill(buffer, offset, offset + length, ' '.code.toByte()); read += length; return length
            }
        }
        assertThrows(EncryptedBackupException::class.java) { backups.open(stream) }
        assertEquals(EncryptedBackup.MAX_FILE_BYTES + 1, read)
    }
    @Test fun truncatedWrapperFailsBeforeRestore() { assertRejected(exported().dropLast(8).toByteArray()) }
    @Test fun exportCreatesNoPrivatePlaintextTempFiles() {
        val before = context.filesDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(context.filesDir).path }.toSet()
        exported()
        assertEquals(before, context.filesDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(context.filesDir).path }.toSet())
        assertFalse(backups.safetyCopy.exists())
    }
}
