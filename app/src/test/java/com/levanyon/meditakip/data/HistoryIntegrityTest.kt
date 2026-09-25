package com.levanyon.meditakip.data

import android.app.Application
import androidx.room.Room
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

/**
 * Regression guard for the core promise: past history never changes because of a later edit,
 * deactivation, archive or restore. Runs against a real (in-memory) Room database.
 * Scenarios originate from the Phase 0 audit probe (docs/AUDIT.md §4.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HistoryIntegrityTest {
    private lateinit var db: MediDatabase
    /** Monday 21 Sep 2026: Parol 08:00 + 20:00 created; 08:00 marked taken. */
    private val d = LocalDate.of(2026, 9, 21)
    private val person = PersonEntity("p", "Anne")
    private val base = MedicationScheduleEntity("", "", "Anne", "Parol", "1 tablet", "", "08:00,20:00", 127, d.toEpochDay(), null, true, d.toEpochDay())
    private val unchangedPast = listOf("08:00 Parol 1 tablet ✓", "20:00 Parol 1 tablet ○")

    private fun repo(day: LocalDate) = Repository(db, Clock.fixed(day.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
    private suspend fun snapshot() = repo(d).snapshot()
    private fun rows(snap: Snapshot, day: LocalDate) =
        Schedule.day(snap, day).map { "${it.time} ${it.schedule.name} ${it.schedule.dose} " + if (it.takenAt != null) "✓" else "○" }
    private suspend fun seed(): String {
        repo(d).savePerson(person); repo(d).saveMedication(person, null, base)
        repo(d).completions.taken(Schedule.day(snapshot(), d).first { it.minute == 480 })
        return snapshot().medications.single().id
    }

    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MediDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }

    @Test fun editingTimeAndDoseKeepsPastRows() = runBlocking {
        val id = seed()
        repo(d.plusDays(1)).saveMedication(person, id, base.copy(times = "09:00", dose = "2 tablet"))
        val s = snapshot()
        assertEquals(unchangedPast, rows(s, d))
        assertEquals(listOf("09:00 Parol 2 tablet ○"), rows(s, d.plusDays(1)))
        assertEquals(1, s.logs.size)
        // B1: the history matrix cell for the past day carries that day's revision (1 tablet), not the latest.
        val matrix = HistoryMatrices.build(s, "p", d, d.plusDays(1))
        val past = matrix.rows.single { it.date == d }.cells.values.single { it.minute == 480 }
        assertEquals("1 tablet", past.schedule.dose)
    }
    @Test fun removingAWeekdayKeepsPastRows() = runBlocking {
        val id = seed()
        repo(d.plusDays(7)).saveMedication(person, id, base.copy(days = 127 and 1.inv())) // drop Monday
        val s = snapshot()
        assertEquals(unchangedPast, rows(s, d))
        assertEquals(emptyList<String>(), rows(s, d.plusDays(7)))
    }
    @Test fun deactivatingKeepsPastRows() = runBlocking {
        val id = seed()
        repo(d.plusDays(1)).saveMedication(person, id, base.copy(active = false))
        val s = snapshot()
        assertEquals(unchangedPast, rows(s, d))
        assertEquals(emptyList<String>(), rows(s, d.plusDays(1)))
    }
    @Test fun archivingMedicationKeepsPastRowsAndLogs() = runBlocking {
        val id = seed()
        repo(d.plusDays(1)).deleteMedication(id)
        val s = snapshot()
        assertTrue(s.medications.single().archived)
        assertEquals(unchangedPast, rows(s, d))
        assertEquals(emptyList<String>(), rows(s, d.plusDays(1)))
        assertEquals(1, s.logs.size)
    }
    @Test fun archivingPersonKeepsPastRowsAndLogs() = runBlocking {
        seed()
        repo(d.plusDays(1)).deletePerson(person)
        val s = snapshot()
        assertTrue(s.people.single().archived)
        assertTrue(s.medications.single().archived)
        assertEquals(unchangedPast, rows(s, d))
        assertEquals(1, s.logs.size)
    }
    @Test fun renamingPersonUpdatesEveryDoseWithoutTouchingRevisions() = runBlocking {
        seed()
        repo(d).savePerson(person.copy(name = "Annem"))
        val s = snapshot()
        // B2: names come from `people`; the denormalized revision column keeps its old value and is not read.
        assertEquals(listOf("Annem", "Annem"), Schedule.day(s, d).map { it.personName })
        assertEquals("Anne", s.schedules.single().personName)
        assertEquals(unchangedPast, rows(s, d))
    }
    @Test fun sameDayEditAppliesFromTodayAndKeepsTakenDose() = runBlocking {
        val id = seed()
        repo(d).saveMedication(person, id, base.copy(times = "09:00"))
        val s = snapshot()
        // Rule: an edit applies from today. The taken 08:00 stays (from its log); the untaken 20:00 is replaced.
        assertEquals(listOf("08:00 Parol 1 tablet ✓", "09:00 Parol 1 tablet ○"), rows(s, d))
        assertEquals(1, s.logs.size)
    }
}
