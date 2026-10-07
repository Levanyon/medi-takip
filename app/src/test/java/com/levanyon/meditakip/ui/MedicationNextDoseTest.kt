package com.levanyon.meditakip.ui

import com.levanyon.meditakip.data.*
import org.junit.Test
import org.junit.Assert.*

class MedicationNextDoseTest {
    private val data = MedicationTestData
    @Test fun ordersMultipleTimesAndIgnoresOtherMedication() {
        assertEquals(480, medicationNextDose(data.snapshot, "m", data.date)?.minute)
        assertNull(medicationNextDose(data.snapshot, "missing", data.date))
    }
    @Test fun takenAndSkippedDosesAdvanceToNextDay() {
        val logs = listOf(480, 1200).map { minute -> MedicationLogEntity("s:${data.date.toEpochDay()}:$minute", "s", data.date.toEpochDay(), minute, 1,
            if (minute == 480) LogStatus.TAKEN else LogStatus.SKIPPED) }
        assertEquals(data.date.plusDays(1), medicationNextDose(data.snapshot.copy(logs = logs), "m", data.date)?.date)
        assertEquals(1200, medicationNextDose(data.snapshot.copy(logs = logs.take(1)), "m", data.date)?.minute)
    }
    @Test fun endInactiveAndArchivesHaveNoNextDose() {
        listOf(data.schedule.copy(end = data.date.minusDays(1).toEpochDay()), data.schedule.copy(active = false),
            data.schedule.copy(effectiveTo = data.date.toEpochDay())).forEach {
            assertNull(medicationNextDose(data.snapshot.copy(schedules = listOf(it)), "m", data.date))
        }
        assertNull(medicationNextDose(data.snapshot.copy(people = listOf(data.person.copy(archived = true))), "m", data.date))
        assertNull(medicationNextDose(data.snapshot.copy(medications = listOf(data.snapshot.medications.single().copy(archived = true))), "m", data.date))
    }
    @Test fun farFutureStartAndWeeklyRecurrenceUseRealDates() {
        val start = data.date.plusMonths(2)
        val snapshot = data.snapshot.copy(schedules = listOf(data.schedule.copy(start = start.toEpochDay(), days = 1)))
        val next = medicationNextDose(snapshot, "m", data.date)!!
        assertTrue(next.date >= start); assertEquals(java.time.DayOfWeek.MONDAY, next.date.dayOfWeek)
    }
}
