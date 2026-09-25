package com.levanyon.meditakip.data

import android.app.Application
import androidx.room.Room
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.*

/** Backup round trip and validation (Phase 4, DECISIONS D4.1–D4.3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private lateinit var preferences: Preferences
    private lateinit var backups: Backups
    private val d = LocalDate.of(2026, 9, 1).toEpochDay()
    private val context get() = RuntimeEnvironment.getApplication()
    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)

    /** Realistic data: archived person, stock, a never-effective revision, a skipped dose with a note. */
    private val realistic = Snapshot(
        listOf(PersonEntity("p", "Anne", "Tansiyon takibi"), PersonEntity("k", "Kardeş"), PersonEntity("d", "Dede", archived = true)),
        listOf(MedicationEntity("m1", "p", stockEnabled = true, stockQuantity = 11.5, stockPerDose = 0.5, stockThreshold = 3.0), MedicationEntity("m2", "k"), MedicationEntity("m3", "d", archived = true)),
        listOf(
            MedicationScheduleEntity("s1a", "m1", "Anne", "Parol", "1 tablet", "", "08:00, 20:00", 127, d, null, true, d, d + 9, 1),
            MedicationScheduleEntity("s1b", "m1", "Anne", "Parol 500", "1 tablet", "Tok karnına", "08:00", 127, d, null, true, d + 10, d + 9, 2),
            MedicationScheduleEntity("s1c", "m1", "Anne", "Parol 500", "1/2 tablet", "", "08:00, 13:00", 0b0011111, d, d + 90, true, d + 10, null, 3),
            MedicationScheduleEntity("s2", "m2", "Kardeş", "D Vitamini", "1 damla", "", "09:00", 127, d, null, false, d, null, 4),
            MedicationScheduleEntity("s3", "m3", "Dede", "Tansiyon", "1 tablet", "", "07:30", 127, d, null, true, d, d + 4, 5),
        ),
        listOf(
            MedicationLogEntity("s1a:$d:480", "s1a", d, 480, 1_788_000_000_000, LogStatus.TAKEN, 1_788_000_100_000),
            MedicationLogEntity("s1a:${d + 1}:1200", "s1a", d + 1, 1200, 1_788_090_000_000, LogStatus.SKIPPED, 1_788_090_000_000, "Dışarıdaydık"),
            MedicationLogEntity("s3:${d + 2}:450", "s3", d + 2, 450, 1_788_150_000_000),
        ),
    )
    private val settings = AppSettings(notifications = true, retry = false, delayMinutes = 60, theme = "Dark")

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, MediDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, clock)
        preferences = Preferences(context)
        backups = Backups(repo, preferences, context.filesDir, "test", clock)
        repo.replaceAll(realistic)
        preferences.save(settings)
    }
    @After fun close() { db.close() }
    private fun exportText() = ByteArrayOutputStream().also { runBlocking { backups.export(it) } }.toString(Charsets.UTF_8.name())
    private fun corrupt(change: (JSONObject) -> Unit): String = JSONObject(exportText()).also(change).toString()
    private fun problem(text: String): BackupProblem = try { BackupCodec.decode(text); fail("should be rejected"); throw AssertionError() } catch (e: BackupException) { e.problem }

    @Test fun roundTripRestoresIdenticalData() = runBlocking {
        val text = exportText()
        assertEquals(clock.millis(), preferences.lastBackupAt)
        // Wipe, then restore into a different state.
        repo.replaceAll(Snapshot(listOf(PersonEntity("x", "Başka")), emptyList(), emptyList(), emptyList()))
        preferences.save(AppSettings())
        val content = backups.inspect(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        backups.restore(content)
        val after = repo.snapshot()
        assertEquals(realistic.people.toSet(), after.people.toSet())
        assertEquals(realistic.medications.toSet(), after.medications.toSet())
        assertEquals(realistic.schedules, after.schedules) // same order: sequence is preserved
        assertEquals(realistic.logs.toSet(), after.logs.toSet())
        assertEquals(settings, preferences.state.value)
        listOf("m1", "m2", "m3").forEach { assertEquals(MedicationViews.latestRevision(realistic, it), MedicationViews.latestRevision(after, it)) }
        (0L..15L).map { LocalDate.ofEpochDay(d + it) }.forEach { day ->
            assertEquals(Schedule.day(realistic, day), Schedule.day(after, day))
        }
    }
    @Test fun previewSummarisesTheBackup() {
        val summary = BackupCodec.decode(exportText()).summary
        assertEquals(BackupSummary(clock.millis(), 3, 3, 3, LocalDate.ofEpochDay(d), LocalDate.ofEpochDay(d + 2)), summary)
    }
    @Test fun restoreKeepsASafetyCopyAndResetsRuntimeState() = runBlocking {
        val text = exportText()
        preferences.snoozes = mapOf("k" to 1L); preferences.lowStockNotified = setOf("m1"); preferences.rememberPerson("x")
        backups.safetyCopy.delete()
        backups.restore(BackupCodec.decode(text))
        assertTrue(backups.safetyCopy.exists())
        assertEquals(3, BackupCodec.decode(backups.safetyCopy.readText()).snapshot.people.size)
        assertTrue(preferences.snoozes.isEmpty()); assertTrue(preferences.lowStockNotified.isEmpty()); assertNull(preferences.lastPerson.value)
    }
    @Test fun rejectsFilesThatAreNotBackups() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problem("merhaba"))
        assertEquals(BackupProblem.NOT_A_BACKUP, problem("""{"format":"other"}"""))
        assertEquals(BackupProblem.TOO_NEW, problem(corrupt { it.put("formatVersion", 99) }))
        assertEquals(BackupProblem.TOO_LARGE, problem(" ".repeat(BackupCodec.MAX_BYTES + 1)))
    }
    @Test fun rejectsBrokenReferencesAndValues() {
        fun first(json: JSONObject, table: String) = json.getJSONArray(table).getJSONObject(0)
        val broken = listOf(
            corrupt { first(it, "medications").put("personId", "nobody") },
            corrupt { first(it, "schedules").put("medicationId", "none") },
            corrupt { first(it, "logs").put("scheduleId", "none") },
            corrupt { first(it, "logs").put("key", "wrong-key") },
            corrupt { first(it, "logs").put("minute", 1440) },
            corrupt { first(it, "logs").put("status", "EATEN") },
            corrupt { it.getJSONArray("people").put(JSONObject().put("id", "p").put("name", "Kopya").put("archived", false)) },
            corrupt { first(it, "people").put("name", " ") },
            corrupt { first(it, "schedules").put("times", "25:99") },
            corrupt { first(it, "schedules").put("days", 0) },
            corrupt { first(it, "schedules").put("end", d - 5) },
            corrupt { it.getJSONArray("schedules").getJSONObject(1).put("sequence", 1) }, // same order as s1a
            corrupt { first(it, "medications").put("stockPerDose", 0) },
            corrupt { it.remove("logs") },
        )
        broken.forEachIndexed { i, text -> assertEquals("case $i", BackupProblem.DAMAGED, problem(text)) }
    }
    @Test fun aFailedReplaceLeavesTheCurrentDataUntouched() = runBlocking {
        val before = repo.snapshot()
        // Bypasses validation on purpose: a record pointing at a missing revision violates a foreign key.
        val invalid = realistic.copy(logs = realistic.logs + MedicationLogEntity("ghost:$d:480", "ghost", d, 480, 0))
        assertThrows(Exception::class.java) { runBlocking { repo.replaceAll(invalid) } }
        assertEquals(before, repo.snapshot())
    }
}
