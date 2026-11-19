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
@Config(sdk=[28], application=Application::class)
class PrescriptionPersistenceTest {
    private lateinit var db: MediDatabase
    private lateinit var repo: Repository
    private val context get() = RuntimeEnvironment.getApplication()
    private val d=LocalDate.of(2026,10,8).toEpochDay()
    private val p=PersonEntity("p","Anne")
    private val s=MedicationScheduleEntity("","","Anne","İlaç","1 tablet","","08:00",127,d,null,true,d)
    @Before fun setup() = runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,MediDatabase::class.java).allowMainThreadQueries().build()
        repo=Repository(db,Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"),ZoneOffset.UTC)); repo.savePerson(p)
    }
    @After fun close() { db.close() }
    @Test fun addEditClearAndOmittedPrescriptionPreserveHistoryStockAndDates() = runBlocking {
        val rx=Prescription(d-2,d+30,true,7)
        repo.saveMedication(p,null,s,StockSettings(true,20.0,0.5,2.0),rx)
        var state=repo.snapshot(); val id=state.medications.single().id
        repo.completions.taken(Schedule.day(state,LocalDate.ofEpochDay(d)).single())
        val log=repo.snapshot().logs.single()
        repo.saveMedication(p,id,s.copy(end=d+40),prescription=rx.copy(renewal=d+60,leadDays=2))
        state=repo.snapshot()
        assertEquals(rx.copy(renewal=d+60,leadDays=2),Prescription.from(state.medications.single()))
        assertEquals(19.5,state.medications.single().stockQuantity,0.0)
        assertEquals(log,state.logs.single())
        assertEquals(d,state.schedules.last().start); assertEquals(d+40,state.schedules.last().end)
        repo.saveMedication(p,id,s)
        assertEquals(d+60,repo.snapshot().medications.single().renewalDate)
        repo.saveMedication(p,id,s,prescription=Prescription())
        assertEquals(Prescription(),Prescription.from(repo.snapshot().medications.single()))
        assertNull(repo.snapshot().schedules.last().end)
    }
    @Test fun invalidSaveIsAtomic() = runBlocking {
        repo.saveMedication(p,null,s); val before=repo.snapshot()
        try { repo.saveMedication(p,before.medications.single().id,s,prescription=Prescription(d,d-1)); fail() } catch (_:IllegalArgumentException) {}
        assertEquals(before,repo.snapshot())
    }
    @Test fun schemaThreeBackupRestoresAllPrescriptionFields() = runBlocking {
        repo.saveMedication(p,null,s,prescription=Prescription(d,d+20,true,5))
        val before=repo.snapshot()
        val text=BackupCodec.encode(before,AppSettings(),System.currentTimeMillis(),"test")
        assertEquals(4,JSONObject(text).getInt("schemaVersion"))
        val backups=Backups(repo,Preferences(context),context.filesDir,"test", safetyCipher = testSafetyCipher())
        repo.replaceAll(Snapshot())
        backups.restore(BackupCodec.decode(text))
        assertEquals(before,repo.snapshot())
    }
    @Test fun schemaTwoBackupRestoresWithNullDatesAndDisabledReminder() = runBlocking {
        repo.saveMedication(p,null,s)
        val before=repo.snapshot()
        val root=JSONObject(BackupCodec.encode(before,AppSettings(),System.currentTimeMillis(),"old"))
        root.put("schemaVersion",2)
        val m=root.getJSONArray("medications").getJSONObject(0)
        listOf("prescriptionDate","renewalDate","renewalReminderEnabled","renewalLeadDays").forEach { m.remove(it) }
        repo.replaceAll(Snapshot())
        Backups(repo,Preferences(context),context.filesDir,"test", safetyCipher = testSafetyCipher()).restore(BackupCodec.decode(root.toString()))
        assertEquals(before,repo.snapshot())
    }
    @Test fun newBackupRejectsMissingCoercedOverflowAndInvalidFields() = runBlocking {
        repo.saveMedication(p,null,s,prescription=Prescription(d,d+20,true,5))
        val original=BackupCodec.encode(repo.snapshot(),AppSettings(),System.currentTimeMillis(),"test")
        val changes:List<(JSONObject)->Unit> = listOf(
            { it.remove("renewalDate") }, { it.put("renewalDate","20000") }, { it.put("renewalDate",Long.MAX_VALUE) },
            { it.put("renewalDate",d-1) }, { it.put("prescriptionDate",Long.MIN_VALUE) },
            { it.put("renewalLeadDays",-1) }, { it.put("renewalLeadDays",2147483648L) },
            { it.put("renewalLeadDays",1.5) }, { it.put("renewalReminderEnabled","true") })
        changes.forEach { change ->
            val root=JSONObject(original); change(root.getJSONArray("medications").getJSONObject(0))
            assertThrows(BackupException::class.java) { BackupCodec.decode(root.toString()) }
        }
    }
}
