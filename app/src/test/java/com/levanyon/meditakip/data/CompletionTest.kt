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

/** Taken / skipped / late marking / undo on a real database (Phase 3, DECISIONS D3.3–D3.6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompletionTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private val day = LocalDate.of(2026, 9, 21)
    private val now = day.atTime(9, 30).atZone(ZoneId.systemDefault()).toInstant()
    private val person = PersonEntity("p", "Anne")
    private val schedule = MedicationScheduleEntity("", "", "Anne", "Parol", "1 tablet", "", "08:00,20:00", 127, day.toEpochDay(), null, true, day.toEpochDay())
    private suspend fun doses() = Schedule.day(repo.snapshot(), day)
    private suspend fun morning() = doses().first { it.minute == 480 }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MediDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Clock.fixed(now, ZoneId.systemDefault()))
        repo.savePerson(person); repo.saveMedication(person, null, schedule)
    }
    @After fun close() { db.close() }

    @Test fun skipIsRecordedWithANoteAndCountsAsHandled() = runBlocking {
        assertTrue(repo.completions.skip(morning(), "  Dışarıdaydım  "))
        val skipped = morning()
        assertTrue(skipped.skipped && skipped.handled)
        assertNull("A skipped dose never shows a taken time", skipped.takenAt)
        assertEquals("Dışarıdaydım", skipped.note)
        assertEquals(LogStatus.SKIPPED, db.dao().logs().single().status)
        assertEquals(DailyProgress(2, 1), DailyProgress.from(doses()))
        val events = ReminderPlan.events(repo.snapshot(), AppSettings(), day.atTime(7, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
        assertTrue("No reminder for a skipped dose", events.none { it.occurrence.key == skipped.key })
    }
    @Test fun aDoseHasAtMostOneRecord() = runBlocking {
        assertTrue(repo.completions.skip(morning()))
        assertFalse("Already skipped: İçildi must not add a second record", repo.completions.taken(morning()))
        assertTrue(repo.completions.undo(morning()))
        assertTrue(repo.completions.taken(morning()))
        assertFalse(repo.completions.taken(morning()))
        assertEquals(1, db.dao().logs().size)
    }
    @Test fun longNotesAreShortenedAndBlankNotesDropped() = runBlocking {
        repo.completions.skip(morning(), "x".repeat(200))
        assertEquals(NOTE_LIMIT, morning().note!!.length)
        repo.completions.undo(morning())
        repo.completions.skip(morning(), "   ")
        assertNull(morning().note)
    }
    @Test fun lateMarkingKeepsWhenItWasRecorded() = runBlocking {
        val actual = day.atTime(8, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repo.completions.taken(morning(), takenAt = actual)
        val log = db.dao().logs().single()
        assertEquals(actual, log.takenAt)
        assertEquals("recordedAt is the moment of entry", now.toEpochMilli(), log.recordedAt)
        assertEquals(actual, morning().takenAt)
    }
    @Test fun changingTheTimeOnlyTouchesTakenRecords() = runBlocking {
        repo.completions.taken(morning())
        val corrected = day.atTime(8, 2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(repo.completions.setTakenTime(morning(), corrected))
        val log = db.dao().logs().single()
        assertEquals(corrected, log.takenAt)
        assertEquals(now.toEpochMilli(), log.recordedAt)
        repo.completions.undo(morning())
        repo.completions.skip(morning())
        assertFalse("A skipped dose has no taken time to change", repo.completions.setTakenTime(morning(), corrected))
        assertFalse(repo.completions.setTakenTime(doses().first { it.minute == 1200 }, corrected))
    }
    @Test fun undoRemovesOnlyThatOneRecord() = runBlocking {
        repo.completions.taken(morning())
        val evening = doses().first { it.minute == 1200 }
        repo.completions.skip(evening)
        repo.completions.undo(morning())
        assertEquals(listOf(evening.key), db.dao().logs().map { it.key })
        assertFalse("Undo of a dose without record is a no-op", repo.completions.undo(morning()))
    }
}
