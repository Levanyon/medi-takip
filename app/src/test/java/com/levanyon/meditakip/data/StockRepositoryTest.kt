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

/** Stock stays exactly in step with dose records (Phase 3, DECISIONS D3.7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StockRepositoryTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private val day = LocalDate.of(2026, 9, 21)
    private val person = PersonEntity("p", "Anne")
    private val schedule = MedicationScheduleEntity("", "", "Anne", "Parol", "1 tablet", "", "08:00,20:00", 127, day.toEpochDay(), null, true, day.toEpochDay())
    private val tracked = StockSettings(true, 10.0, 1.0, 3.0)
    private suspend fun medication() = db.dao().medications().single()
    private suspend fun dose(minute: Int) = Schedule.day(repo.snapshot(), day).first { it.minute == minute }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MediDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Clock.fixed(day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()))
        repo.savePerson(person); repo.saveMedication(person, null, schedule, tracked)
    }
    @After fun close() { db.close() }

    @Test fun takenDecrementsOnceAndUndoRestores() = runBlocking {
        repo.completions.taken(dose(480))
        assertEquals(9.0, medication().stockQuantity, 1e-9)
        repo.completions.taken(dose(480)) // repeated tap or notification action
        assertEquals("A repeated İçildi never decrements twice", 9.0, medication().stockQuantity, 1e-9)
        repo.completions.undo(dose(480))
        assertEquals(10.0, medication().stockQuantity, 1e-9)
    }
    @Test fun skipAndTimeChangesDoNotTouchStock() = runBlocking {
        repo.completions.skip(dose(480))
        repo.completions.undo(dose(480))
        assertEquals(10.0, medication().stockQuantity, 1e-9)
        repo.completions.taken(dose(1200))
        repo.completions.setTakenTime(dose(1200), day.atTime(20, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(9.0, medication().stockQuantity, 1e-9)
    }
    @Test fun halfTabletsAndStockAdditions() = runBlocking {
        val id = medication().id
        repo.saveMedication(person, id, db.dao().schedules().single(), tracked.copy(quantity = 2.0, perDose = 0.5))
        repo.completions.taken(dose(480))
        assertEquals(1.5, medication().stockQuantity, 1e-9)
        repo.addStock(id, 30.0)
        assertEquals(31.5, medication().stockQuantity, 1e-9)
    }
    @Test fun editingTheScheduleKeepsTheStock() = runBlocking {
        repo.completions.taken(dose(480))
        val id = medication().id
        // An edit that does not send stock settings (e.g. from older code paths) keeps everything.
        repo.saveMedication(person, id, db.dao().schedules().last().copy(times = "09:00"))
        assertEquals(9.0, medication().stockQuantity, 1e-9)
        assertTrue(medication().stockEnabled)
        assertEquals(3.0, medication().stockThreshold, 1e-9)
    }
    @Test fun trackingOffMeansNoStockChanges() = runBlocking {
        val id = medication().id
        repo.saveMedication(person, id, db.dao().schedules().single(), tracked.copy(enabled = false))
        repo.completions.taken(dose(480))
        assertEquals(10.0, medication().stockQuantity, 1e-9)
        repo.addStock(id, 5.0)
        assertEquals(10.0, medication().stockQuantity, 1e-9)
    }
    @Test fun invalidSettingsAreRejectedWithoutChanges() = runBlocking {
        val id = medication().id
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.saveMedication(person, id, db.dao().schedules().single(), tracked.copy(perDose = 0.0)) } }
        assertEquals(1.0, medication().stockPerDose, 1e-9)
        assertEquals(1, db.dao().schedules().size)
    }
}
