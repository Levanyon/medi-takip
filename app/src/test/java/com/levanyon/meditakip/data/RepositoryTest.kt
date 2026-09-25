package com.levanyon.meditakip.data

import android.app.Application
import androidx.room.Room
import com.levanyon.meditakip.domain.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RepositoryTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private val date = LocalDate.of(2026, 9, 21)
    private val person = PersonEntity("p", "Mother")
    private val schedule = MedicationScheduleEntity("", "", "Mother", "A", "1 tablet", "", "08:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private fun repository(day: LocalDate) = Repository(db, Clock.fixed(day.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MediDatabase::class.java).allowMainThreadQueries().build(); repo = repository(date) }
    @After fun close() { db.close() }
    @Test fun saveTakenUndoRoundTrip() = runBlocking {
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
        val item = Schedule.day(repo.snapshot(), date).first()
        repo.completions.taken(item); repo.completions.taken(item)
        assertEquals(1, repo.snapshot().logs.size)
        assertNotNull(Schedule.day(repo.snapshot(), date).first().takenAt)
        repo.completions.undo(item)
        assertTrue(repo.snapshot().logs.isEmpty())
    }
    @Test fun editRetainsPastAndReplacesFuture() = runBlocking {
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
        val id = repo.snapshot().medications.single().id
        repository(date.plusDays(1)).saveMedication(person, id, schedule.copy(name = "B", times = "12:00"))
        val state = repo.snapshot()
        assertEquals(listOf("A", "A"), Schedule.day(state, date).map { it.schedule.name })
        assertEquals(listOf("B"), Schedule.day(state, date.plusDays(1)).map { it.schedule.name })
    }
    @Test fun archivePersonStopsFutureButPreservesHistory() = runBlocking {
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
        repository(date.plusDays(1)).deletePerson(person)
        val state = repo.snapshot()
        assertTrue(state.people.single().archived)
        assertEquals(2, Schedule.day(state, date).size)
        assertTrue(Schedule.day(state, date.plusDays(1)).isEmpty())
    }
    @Test fun invalidEditRollsBackWholeTransaction() = runBlocking {
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
        val before = repo.snapshot()
        try { repo.saveMedication(person, before.medications.single().id, schedule.copy(days = 0)); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.snapshot())
    }
    @Test fun completedEntrySurvivesSameDayEdit() = runBlocking {
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
        val item = Schedule.day(repo.snapshot(), date).first()
        repo.completions.taken(item)
        repo.saveMedication(person, item.schedule.medicationId, schedule.copy(times = "21:00"))
        val list = Schedule.day(repo.snapshot(), date)
        assertEquals(2, list.size)
        assertNotNull(list.first { it.minute == 480 }.takenAt)
    }
    @Test fun sameTimeEditDoesNotDuplicateCompletedDose() = runBlocking {
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
        val item = Schedule.day(repo.snapshot(), date).first()
        repo.completions.taken(item)
        repo.saveMedication(person, item.schedule.medicationId, schedule.copy(name = "Updated"))
        val list = Schedule.day(repo.snapshot(), date)
        assertEquals(2, list.size)
        assertEquals(1, list.count { it.takenAt != null })
        repo.completions.undo(item)
        assertEquals(2, Schedule.day(repo.snapshot(), date).size)
        assertTrue(Schedule.day(repo.snapshot(), date).all { it.takenAt == null })
    }
}
