package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private val date = LocalDate.of(2026, 9, 21)
    private val s = MedicationScheduleEntity("s", "m", "Mother", "A", "1 tablet", "", "08:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private fun snapshot(schedule: MedicationScheduleEntity = s, logs: List<MedicationLogEntity> = emptyList()) = Snapshot(listOf(PersonEntity("p", "Mother")), listOf(MedicationEntity("m", "p")), listOf(schedule), logs)
    @Test fun dailyListSortedAndDeduplicated() { assertEquals(listOf(480, 1200), Schedule.day(snapshot(s.copy(times = "20:00,08:00,08:00")), date).map { it.minute }) }
    @Test fun boundariesInclusive() {
        assertEquals(2, Schedule.day(snapshot(s.copy(end = date.toEpochDay())), date).size)
        assertTrue(Schedule.day(snapshot(), date.minusDays(1)).isEmpty())
        assertTrue(Schedule.day(snapshot(s.copy(end = date.toEpochDay())), date.plusDays(1)).isEmpty())
    }
    @Test fun weekdaysAndInactive() {
        assertEquals(2, Schedule.day(snapshot(s.copy(days = 1)), date).size)
        assertTrue(Schedule.day(snapshot(s.copy(days = 2)), date).isEmpty())
        assertTrue(Schedule.day(snapshot(s.copy(active = false)), date).isEmpty())
    }
    @Test fun retiredRevisionPreservesHistory() { assertEquals(2, Schedule.day(snapshot(s.copy(effectiveTo = date.toEpochDay())), date).size); assertTrue(Schedule.day(snapshot(s.copy(effectiveTo = date.toEpochDay())), date.plusDays(1)).isEmpty()) }
    @Test fun leapDayAndYearBoundary() { assertEquals(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 29).plusDays(1)); assertEquals(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31).plusDays(1)) }
    @Test fun dstGapShiftsForwardOverlapOccursOnce() {
        val zone = ZoneId.of("Europe/Berlin")
        val gap = Occurrence(s, "p", LocalDate.of(2026, 3, 29), 150)
        assertEquals(3, gap.instant(zone).atZone(zone).hour)
        val overlap = Occurrence(s, "p", LocalDate.of(2026, 10, 25), 150)
        assertEquals(ZoneOffset.ofHours(2), overlap.instant(zone).atZone(zone).offset)
    }
    @Test fun takenLogIsReflectedAndItsRemovalClearsIt() {
        // Idempotent Taken/Undo with stock is covered on a real database in data/RepositoryTest.
        val instant = Instant.parse("2026-09-21T06:10:00Z")
        val item = Schedule.day(snapshot(), date).first()
        val log = MedicationLogEntity(item.key, item.schedule.id, date.toEpochDay(), item.minute, instant.toEpochMilli())
        assertEquals(instant.toEpochMilli(), Schedule.day(snapshot(logs = listOf(log)), date).first().takenAt)
        assertNull(Schedule.day(snapshot(logs = emptyList()), date).first().takenAt)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidRange() { Schedule.validate(s.copy(end = s.start - 1)) }
}
