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
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PackageTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private val person = PersonEntity("p", "Anne")
    private val day = LocalDate.of(2026, 10, 9)
    private val scan = ScannedProduct("08699580090635", "SER1", "LOT", PackageExpiry(2028, 2, null))
    private val schedule = MedicationScheduleEntity("", "", "Anne", "İlaç", "1 tablet", "", "08:00", 127, day.toEpochDay(), null, true, day.toEpochDay())
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MediDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Clock.fixed(day.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)); repo.savePerson(person)
    }
    @After fun close() { db.close() }
    private suspend fun medication(stock: Boolean = true) = repo.saveMedication(person, null, schedule, StockSettings(stock, 10.0, 0.5, 2.0))
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun duplicatePhysicalPackageCannotIncrementTwiceEvenAcrossMedications() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 20.0, "receipt1")
        val before = repo.snapshot()
        rejected { repo.addPackage(id, scan, 20.0, "receipt2") }; assertEquals(before, repo.snapshot())
        val other = medication(); rejected { repo.addPackage(other, scan, 20.0, "receipt3") }
        assertEquals(1, repo.snapshot().packages.size)
    }
    @Test fun sameGtinNewSerialAddsConfirmedAmountOnly() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 20.0, "a"); repo.addPackage(id, scan.copy(serial = "SER2"), 3.0, "b")
        assertEquals(33.0, repo.snapshot().medications.single().stockQuantity, 0.0)
        assertEquals(2, repo.snapshot().packages.size); assertEquals(1, repo.snapshot().schedules.size)
    }
    @Test fun databaseUniquenessIsEnforcedWithoutRepositoryChecks() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 1.0, "a")
        val row = repo.snapshot().packages.single()
        try { db.dao().insertPackage(row.copy(id = "b")); fail("Unique index must reject duplicate") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, db.dao().packages().size)
    }
    @Test fun receiptIdAlsoPreventsEanRepeatSubmissionButNotIndependentBoxes() = runBlocking {
        val id = medication(); val ean = ScannedProduct(scan.gtin)
        repo.addPackage(id, ean, 4.0, "a"); rejected { repo.addPackage(id, ean, 4.0, "a") }
        repo.addPackage(id, ean, 6.0, "b")
        assertEquals(20.0, repo.snapshot().medications.single().stockQuantity, 0.0)
        assertTrue(repo.snapshot().packages.all { it.serial == null })
    }
    @Test fun packageAndMedicationCreationRollBackTogetherOnDuplicate() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 1.0, "a")
        val other = person.copy(id = "other"); repo.savePerson(other)
        val before = repo.snapshot()
        rejected { repo.saveScannedMedication(other, schedule, StockSettings(true, 30.0, 1.0, 2.0), Prescription(), scan, "b", false) }
        assertEquals(before, repo.snapshot())
    }
    @Test fun scanCreationDoesNotDoubleCountInitialStockOrInventPrescription() = runBlocking {
        repo.saveScannedMedication(person, schedule, StockSettings(true, 30.0, 0.5, 2.0), Prescription(), scan, "a", false)
        val state = repo.snapshot()
        assertEquals(30.0, state.medications.single().stockQuantity, 0.0)
        assertEquals(Prescription(), Prescription.from(state.medications.single()))
        assertEquals(30.0, state.packages.single().quantityAdded, 0.0)
        rejected { repo.saveScannedMedication(person, schedule, StockSettings(true, 1.0, 1.0, 0.0), Prescription(), scan.copy(serial = "NEW"), "b", false) }
    }
    @Test fun doseTakenUndoAndSkipKeepExistingStockMath() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 5.0, "a")
        val occurrence = Schedule.day(repo.snapshot(), day).single()
        repo.completions.taken(occurrence); repo.completions.taken(occurrence)
        assertEquals(14.5, repo.snapshot().medications.single().stockQuantity, 0.0)
        repo.completions.undo(occurrence); repo.completions.skip(occurrence)
        assertEquals(15.0, repo.snapshot().medications.single().stockQuantity, 0.0)
        assertEquals(1, repo.snapshot().packages.size)
    }
    @Test fun expiredMustBeAcknowledgedAndMissingExpiryIsNotInvented() = runBlocking {
        val id = medication(); val expired = scan.copy(expiry = PackageExpiry(2020, 1, 1))
        rejected { repo.addPackage(id, expired, 2.0, "a") }
        repo.addPackage(id, expired, 2.0, "a", true)
        repo.addPackage(id, scan.copy(serial = "NEW", expiry = null), 0.0, "b")
        assertNull(repo.snapshot().packages.last().expiry)
    }
    @Test fun disabledStockOnlyAcceptsMetadataAndNonfiniteQuantityFails() = runBlocking {
        val id = medication(false)
        rejected { repo.addPackage(id, scan, 1.0, "a") }
        repo.addPackage(id, scan, 0.0, "a")
        rejected { repo.addPackage(id, scan.copy(serial = "NEW"), Double.NaN, "b") }
        assertEquals(10.0, repo.snapshot().medications.single().stockQuantity, 0.0)
    }
    @Test fun packageRoundTripAndOldBackupDefaults() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 10.0, "a")
        val before = repo.snapshot()
        val text = BackupCodec.encode(before, AppSettings(), System.currentTimeMillis(), "test")
        val decoded = BackupCodec.decode(text)
        repo.replaceAll(Snapshot()); Backups(repo, Preferences(RuntimeEnvironment.getApplication()), RuntimeEnvironment.getApplication().filesDir, "test", safetyCipher = testSafetyCipher()).restore(decoded)
        assertEquals(before, repo.snapshot())
        for (version in listOf(2, 3)) {
            val root = JSONObject(text).put("schemaVersion", version); root.remove("packages")
            root.getJSONArray("medications").getJSONObject(0).remove("gtin")
            val old = BackupCodec.decode(root.toString()).snapshot
            assertTrue(old.packages.isEmpty()); assertNull(old.medications.single().gtin)
        }
    }
    @Test fun backupRejectsDuplicatePackageAndInvalidLinksDatesOrNumbers() = runBlocking {
        val id = medication(); repo.addPackage(id, scan, 0.0, "a")
        val text = BackupCodec.encode(repo.snapshot(), AppSettings(), System.currentTimeMillis(), "test")
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("expiry", "2027-02-29") }, { it.put("quantityAdded", -1) },
            { it.put("medicationId", "missing") }, { it.put("gtin", "00000000000000") }, { it.put("serial", "") })
        changes.forEach { change -> val root = JSONObject(text); change(root.getJSONArray("packages").getJSONObject(0)); assertThrows(BackupException::class.java) { BackupCodec.decode(root.toString()) } }
        val root = JSONObject(text); val packages = root.getJSONArray("packages")
        packages.put(JSONObject(packages.getJSONObject(0).toString()).put("id", "b"))
        assertThrows(BackupException::class.java) { BackupCodec.decode(root.toString()) }
        Unit
    }
}
