package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HistoryMatrixTest {
    private val today = LocalDate.of(2026, 9, 23)
    private val start = today.minusDays(2).toEpochDay()
    private val parol = MedicationScheduleEntity("parol", "m1", "Anne", "Parol", "1 tablet", "", "08:00,20:00", 127, start, null, true, start)
    private val tension = MedicationScheduleEntity("tension", "m2", "Anne", "Tansiyon", "1 tablet", "", "13:00", 127, start, null, true, start)
    private val sibling = MedicationScheduleEntity("vit", "m3", "Kardeş", "Vitamin", "1 kapsül", "", "09:00", 127, start, null, true, start)
    private fun log(s: MedicationScheduleEntity, date: LocalDate, minute: Int, hour: Int, min: Int) =
        MedicationLogEntity("${s.id}:${date.toEpochDay()}:$minute", s.id, date.toEpochDay(), minute, date.atTime(hour, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    private val snapshot = Snapshot(
        listOf(PersonEntity("anne", "Anne"), PersonEntity("kardes", "Kardeş")),
        listOf(MedicationEntity("m1", "anne"), MedicationEntity("m2", "anne"), MedicationEntity("m3", "kardes")),
        listOf(parol, tension, sibling),
        listOf(log(parol, today, 480, 8, 4), log(tension, today, 780, 13, 2), log(parol, today.minusDays(1), 1200, 20, 3), log(sibling, today, 540, 9, 0))
    )

    @Test fun columnsAreMedicationAndTimeSortedByTimeForOnePersonOnly() {
        val matrix = HistoryMatrices.build(snapshot, "anne", today.minusDays(2), today)
        assertEquals(listOf("Parol 08:00", "Tansiyon 13:00", "Parol 20:00"), matrix.columns.map { "${it.name} ${it.time}" })
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), matrix.rows.map { it.date })
    }
    @Test fun cellsUseRealLogsAndLeaveUnmarkedDosesOpen() {
        val matrix = HistoryMatrices.build(snapshot, "anne", today.minusDays(2), today)
        val (morning, noon, evening) = matrix.columns
        val latest = matrix.rows.first()
        assertNotNull(latest.cells.getValue(morning.key).takenAt)
        assertNotNull(latest.cells.getValue(noon.key).takenAt)
        assertNull(latest.cells.getValue(evening.key).takenAt)
        val yesterday = matrix.rows[1]
        assertNull(yesterday.cells.getValue(morning.key).takenAt)
        assertNotNull(yesterday.cells.getValue(evening.key).takenAt)
    }
    @Test fun buildingTheMatrixDoesNotCreateRecords() {
        HistoryMatrices.build(snapshot, "anne", today.minusDays(30), today)
        assertEquals(4, snapshot.logs.size)
    }
    @Test fun daysBeforeAnyScheduleAreOmittedAndMedicationFilterWorks() {
        val matrix = HistoryMatrices.build(snapshot, "anne", today.minusDays(10), today, medicationId = "m2")
        assertEquals(3, matrix.rows.size)
        assertEquals(listOf("Tansiyon"), matrix.columns.map { it.name })
        assertEquals(LocalDate.ofEpochDay(start), HistoryMatrices.earliest(snapshot, "anne"))
    }
    @Test fun revisedScheduleKeepsOneColumnPerMedicationAndTimeWithLatestName() {
        val renamed = parol.copy(id = "parol2", name = "Parol 500", effectiveFrom = today.toEpochDay())
        val revised = snapshot.copy(schedules = listOf(parol.copy(effectiveTo = today.minusDays(1).toEpochDay()), renamed, tension, sibling),
            logs = snapshot.logs.filter { it.scheduleId != "parol" || it.date != today.toEpochDay() })
        val matrix = HistoryMatrices.build(revised, "anne", today.minusDays(2), today)
        assertEquals(listOf("Parol 500 08:00", "Tansiyon 13:00", "Parol 500 20:00"), matrix.columns.map { "${it.name} ${it.time}" })
        assertNotNull(matrix.rows[1].cells.getValue(matrix.columns[2].key).takenAt)
    }
}
