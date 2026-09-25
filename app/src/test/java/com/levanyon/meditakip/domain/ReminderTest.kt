package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderTest {
    private val date = LocalDate.of(2026, 9, 21)
    private val s = MedicationScheduleEntity("s", "m", "Mother", "A", "text", "", "23:50", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val snapshot = Snapshot(listOf(PersonEntity("p", "Mother")), listOf(MedicationEntity("m", "p")), listOf(s))
    @Test fun retryCrossesMidnight() {
        val events = ReminderPlan.events(snapshot, AppSettings(retry = true), date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        assertEquals(Instant.parse("2026-09-22T00:20:00Z"), events.first { it.retry }.at)
    }
    @Test fun takenSuppressesFirstAndRetry() {
        val o = Schedule.day(snapshot, date).first()
        val log = MedicationLogEntity(o.key, s.id, date.toEpochDay(), o.minute, 1)
        assertTrue(ReminderPlan.events(snapshot.copy(logs = listOf(log)), AppSettings(retry = true), date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC).none { it.occurrence.date == date })
    }
    @Test fun disabledReturnsNoEvents() { assertTrue(ReminderPlan.events(snapshot, AppSettings(notifications = false), Instant.now(), ZoneOffset.UTC).isEmpty()) }
    @Test fun futureStartBeyondOneWeekStillScheduled() {
        val future = snapshot.copy(schedules = listOf(s.copy(start = date.plusMonths(2).toEpochDay())))
        assertEquals(date.plusMonths(2), ReminderPlan.events(future, AppSettings(), date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC).first().occurrence.date)
    }
}
