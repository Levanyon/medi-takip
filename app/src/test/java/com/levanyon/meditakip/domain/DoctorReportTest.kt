package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class DoctorReportTest {
    @Test fun selectsOnlyPersonsRecordsWithinInclusiveThirtyDays() {
        val today = LocalDate.of(2026, 9, 25)
        val d = today.toEpochDay()
        val s = MedicationScheduleEntity("s", "m", "Eski ad", "İlaç", "Kayıtlı doz", "", "08:00", 127, d - 90, null, true, d - 90)
        val logs = listOf(d - 30, d - 29, d, d + 1).map { MedicationLogEntity("s:$it:480", "s", it, 480, 100, LogStatus.SKIPPED) }
        val snapshot = Snapshot(listOf(PersonEntity("p", "Güncel ad"), PersonEntity("other", "Diğer")),
            listOf(MedicationEntity("m", "p"), MedicationEntity("m2", "other")), listOf(s, s.copy(id = "s2", medicationId = "m2")), logs)
        val report = DoctorReports.build(snapshot, "p", today)
        assertEquals("Güncel ad", report.person.name)
        assertEquals(listOf(d, d - 29), report.records.map { it.date.toEpochDay() })
        assertEquals(listOf("m"), report.medications.map { it.medicationId })
        assertTrue(report.records.all { it.skipped })
    }
    @Test fun emptyAndArchivedPeopleDoNotInventCurrentMedicationOrHistory() {
        val snapshot = Snapshot(people = listOf(PersonEntity("p", "Kişi", archived = true)))
        val report = DoctorReports.build(snapshot, "p", LocalDate.of(2026, 9, 25))
        assertTrue(report.medications.isEmpty()); assertTrue(report.records.isEmpty())
    }
}
