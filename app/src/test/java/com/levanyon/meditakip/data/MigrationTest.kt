package com.levanyon.meditakip.data

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
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
import java.io.File
import java.time.*

/**
 * Migration 1 → 2 on a database built exactly like the one on the phone: the tables come from the
 * committed `app/schemas/.../1.json`, the data is realistic v1 data written with plain SQL. Opening it
 * through Room runs MIGRATION_1_2 and makes Room validate every table against the v2 entities (it
 * throws on any mismatch). Then every row is compared value by value. (docs/DECISIONS.md D3.1–D3.2)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "migration-test.db"
    private val d = LocalDate.of(2026, 9, 1).toEpochDay()
    private fun millis(day: Long, hour: Int, minute: Int) = LocalDate.ofEpochDay(day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private data class V1Person(val id: String, val name: String, val note: String, val archived: Int)
    private data class V1Medication(val id: String, val personId: String, val archived: Int)
    private data class V1Schedule(val id: String, val medicationId: String, val personName: String, val name: String, val dose: String, val note: String, val times: String,
                                  val days: Int, val start: Long, val end: Long?, val active: Int, val effectiveFrom: Long, val effectiveTo: Long?)
    private data class V1Log(val key: String, val scheduleId: String, val date: Long, val minute: Int, val takenAt: Long)

    private val people = listOf(V1Person("p", "Anne", "Tansiyon takibi", 0), V1Person("k", "Kardeş", "", 0), V1Person("d", "Dede", "", 1))
    private val medications = listOf(V1Medication("m1", "p", 0), V1Medication("m2", "k", 0), V1Medication("m3", "d", 1))
    /** Insertion order matters: it becomes the permanent revision order (sequence ← rowid). */
    private val schedules = listOf(
        V1Schedule("s1a", "m1", "Anne", "Parol", "1 tablet", "", "08:00, 20:00", 127, d, null, 1, d, d + 9),
        V1Schedule("s2", "m2", "Kardeş", "Vitamin", "1 damla", "Kahvaltıda", "09:00", 0b0011111, d, d + 60, 1, d, null),
        V1Schedule("s1b", "m1", "Anne", "Parol 500", "2 tablet", "", "08:00", 127, d, null, 1, d + 10, d + 9), // never effective (same-day edit)
        V1Schedule("s1c", "m1", "Anne", "Parol 500", "1 tablet", "", "08:00, 13:00", 127, d, null, 1, d + 10, null),
        V1Schedule("s3", "m3", "Dede", "Tansiyon", "1 tablet", "", "07:30", 127, d, null, 1, d, d + 4), // archived medication
    )
    private val logs = listOf(
        V1Log("s1a:${d}:480", "s1a", d, 480, millis(d, 8, 4)),
        V1Log("s1a:${d + 1}:1200", "s1a", d + 1, 1200, millis(d + 1, 21, 15)),
        V1Log("s1b:${d + 10}:480", "s1b", d + 10, 480, millis(d + 10, 8, 1)),
        V1Log("s1c:${d + 10}:780", "s1c", d + 10, 780, millis(d + 10, 13, 5)),
        V1Log("s2:${d + 2}:540", "s2", d + 2, 540, millis(d + 2, 9, 0)),
        V1Log("s3:${d + 3}:450", "s3", d + 3, 450, millis(d + 3, 7, 40)),
    )

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun cleanUp() { context.deleteDatabase(name) }

    /** Creates the v1 database from the committed schema JSON and fills it with plain SQL. */
    private fun createV1Database() {
        val schema = JSONObject(File("schemas/com.levanyon.meditakip.data.MediDatabase/1.json").readText()).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))
        val db = context.openOrCreateDatabase(name, 0, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            entity.optJSONArray("indices")?.let { indices -> for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        people.forEach { db.insertOrThrow("people", null, ContentValues().apply { put("id", it.id); put("name", it.name); put("note", it.note); put("color", 0); put("archived", it.archived) }) }
        medications.forEach { db.insertOrThrow("medications", null, ContentValues().apply { put("id", it.id); put("personId", it.personId); put("archived", it.archived) }) }
        schedules.forEach { s ->
            db.insertOrThrow("schedules", null, ContentValues().apply {
                put("id", s.id); put("medicationId", s.medicationId); put("personName", s.personName); put("name", s.name); put("dose", s.dose); put("note", s.note)
                put("times", s.times); put("days", s.days); put("start", s.start); put("end", s.end); put("active", s.active); put("effectiveFrom", s.effectiveFrom); put("effectiveTo", s.effectiveTo)
            })
        }
        logs.forEach { l -> db.insertOrThrow("logs", null, ContentValues().apply { put("key", l.key); put("scheduleId", l.scheduleId); put("date", l.date); put("minute", l.minute); put("takenAt", l.takenAt) }) }
        db.version = 1
        db.close()
    }
    private fun openMigrated() = Room.databaseBuilder(context, MediDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()

    @Test fun everyV1RowSurvivesWithUnchangedValues() = runBlocking {
        createV1Database()
        val room = openMigrated() // throws if the migrated schema differs from the v2 entities
        try {
            val dao = room.dao()
            assertEquals(people.map { PersonEntity(it.id, it.name, it.note, 0, it.archived == 1) }.toSet(), dao.people().toSet())
            assertEquals(medications.map { MedicationEntity(it.id, it.personId, it.archived == 1) }.toSet(), dao.medications().toSet())
            val migratedSchedules = dao.schedules()
            assertEquals(schedules.mapIndexed { i, s -> MedicationScheduleEntity(s.id, s.medicationId, s.personName, s.name, s.dose, s.note, s.times, s.days, s.start, s.end, s.active == 1, s.effectiveFrom, s.effectiveTo, sequence = i + 1L) },
                migratedSchedules)
            assertEquals(logs.map { MedicationLogEntity(it.key, it.scheduleId, it.date, it.minute, it.takenAt, LogStatus.TAKEN, recordedAt = it.takenAt, note = null) }.toSet(), dao.logs().toSet())
            assertEquals(2, room.openHelper.readableDatabase.version)
        } finally { room.close() }
    }

    @Test fun historyAndLatestRevisionAreTheSameBeforeAndAfter() = runBlocking {
        // The same data as in-memory v1 values, before any migration.
        val before = Snapshot(
            people.map { PersonEntity(it.id, it.name, it.note, 0, it.archived == 1) },
            medications.map { MedicationEntity(it.id, it.personId, it.archived == 1) },
            schedules.map { s -> MedicationScheduleEntity(s.id, s.medicationId, s.personName, s.name, s.dose, s.note, s.times, s.days, s.start, s.end, s.active == 1, s.effectiveFrom, s.effectiveTo) },
            logs.map { MedicationLogEntity(it.key, it.scheduleId, it.date, it.minute, it.takenAt) })
        createV1Database()
        val room = openMigrated()
        try {
            val after = Repository(room).snapshot()
            (0L..20L).map { LocalDate.ofEpochDay(d + it) }.forEach { day ->
                fun describe(s: Snapshot) = Schedule.day(s, day).map { "${it.personName} ${it.time} ${it.schedule.name} ${it.schedule.dose} ${it.takenAt}" }
                assertEquals("History of $day must not change", describe(before), describe(after))
            }
            listOf("m1", "m2", "m3").forEach { id -> assertEquals(MedicationViews.latestRevision(before, id)?.id, MedicationViews.latestRevision(after, id)?.id) }
            assertEquals("s1c", MedicationViews.latestRevision(after, "m1")?.id)
            assertTrue(after.medications.none { it.stockEnabled })
        } finally { room.close() }
    }

    @Test fun newRevisionsAfterMigrationContinueTheSequence() = runBlocking {
        createV1Database()
        val room = openMigrated()
        try {
            val repo = Repository(room, Clock.fixed(LocalDate.ofEpochDay(d + 20).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC))
            val anne = room.dao().people().single { it.id == "p" }
            val current = room.dao().schedules().single { it.id == "s1c" }
            repo.saveMedication(anne, "m1", current.copy(name = "Parol 1000"))
            val newest = MedicationViews.latestRevision(repo.snapshot(), "m1")!!
            assertEquals("Parol 1000", newest.name)
            assertEquals(schedules.size + 1L, newest.sequence)
        } finally { room.close() }
    }
}
