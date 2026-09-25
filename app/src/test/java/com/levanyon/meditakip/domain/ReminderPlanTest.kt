package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** Next-trigger, delivery-window, snooze and grouping rules (Phase 2). */
class ReminderPlanTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 9, 21)
    private val parol = MedicationScheduleEntity("s1", "m1", "Anne", "Parol", "1 tablet", "", "08:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val vitamin = MedicationScheduleEntity("s2", "m2", "Kardeş", "Vitamin", "1 kapsül", "", "08:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val snapshot = Snapshot(listOf(PersonEntity("p", "Anne"), PersonEntity("k", "Kardeş")), listOf(MedicationEntity("m1", "p"), MedicationEntity("m2", "k")), listOf(parol, vitamin))
    private fun at(h: Int, m: Int = 0, day: LocalDate = date) = day.atTime(h, m).toInstant(zone)
    private fun events(now: Instant, settings: AppSettings = AppSettings(), snoozes: Map<String, Long> = emptyMap()) = ReminderPlan.events(snapshot, settings, now, zone, snoozes)

    @Test fun followUpIsOnByDefaultAfterThirtyMinutes() {
        val e = events(at(7))
        assertTrue(AppSettings().retry)
        assertEquals(at(8, 30), e.first { it.kind == ReminderKind.RETRY && it.occurrence.schedule.id == "s1" }.at)
    }
    @Test fun nextSkipsDispatchedAndPastEvents() {
        val e = events(at(7))
        val first = ReminderPlan.next(e, emptySet(), at(7))!!
        assertEquals(at(8), first.at)
        val dispatched = e.filter { it.at == at(8) }.map { it.key }.toSet()
        assertEquals(at(8, 30), ReminderPlan.next(e, dispatched, at(7))!!.at)
        assertEquals(at(20), ReminderPlan.next(e, emptySet(), at(8, 45))!!.at)
    }
    @Test fun sameTimeDosesAreOrderedByPersonAndShareAGroup() {
        val due = events(at(7)).filter { it.at == at(8) && it.kind == ReminderKind.FIRST }
        assertEquals(listOf("Anne", "Kardeş"), due.map { it.occurrence.personName })
        assertEquals(1, due.map(ReminderPlan::groupKey).distinct().size)
    }
    @Test fun dueDeliversOnlyUndispatchedEventsBetweenAlarmAndNow() {
        val e = events(at(7))
        val due = ReminderPlan.due(e, emptySet(), at(8).toEpochMilli(), at(8, 1))
        assertEquals(2, due.size)
        assertTrue(ReminderPlan.due(e, due.map { it.key }.toSet(), at(8).toEpochMilli(), at(8, 1)).isEmpty())
        assertTrue("No alarm pending -> nothing delivered", ReminderPlan.due(e, emptySet(), 0, at(8, 1)).isEmpty())
    }
    @Test fun dueNeverDeliversABacklogOlderThanOneDay() {
        val e = events(at(7, day = date.plusDays(2)))
        val lower = at(8).toEpochMilli() // alarm set two days ago, phone was off
        val due = ReminderPlan.due(e, emptySet(), lower, at(9, day = date.plusDays(2)))
        assertTrue(due.all { it.at >= at(9, day = date.plusDays(1)) })
    }
    @Test fun snoozeAddsAUniqueEventForAnOutstandingDose() {
        val dose = Schedule.day(snapshot, date).first { it.schedule.id == "s1" && it.minute == 480 }
        val snoozedTo = at(8, 12).toEpochMilli()
        val e = events(at(8, 2), snoozes = mapOf(dose.key to snoozedTo))
        val snooze = e.single { it.kind == ReminderKind.SNOOZE }
        assertEquals(dose.key, snooze.occurrence.key)
        assertEquals(snoozedTo, snooze.at.toEpochMilli())
        assertEquals(snooze, ReminderPlan.next(e, e.filter { !it.at.isAfter(at(8, 2)) }.map { it.key }.toSet(), at(8, 2)))
        assertNotEquals(snooze.key, e.first { it.kind == ReminderKind.FIRST && it.occurrence.key == dose.key }.key)
    }
    @Test fun markedDoseDropsItsSnoozeAndFollowUp() {
        val dose = Schedule.day(snapshot, date).first { it.schedule.id == "s1" && it.minute == 480 }
        val taken = snapshot.copy(logs = listOf(MedicationLogEntity(dose.key, "s1", date.toEpochDay(), 480, at(8, 5).toEpochMilli())))
        val e = ReminderPlan.events(taken, AppSettings(), at(8, 5), zone, mapOf(dose.key to at(8, 15).toEpochMilli()))
        assertTrue(e.none { it.occurrence.key == dose.key })
        val outstanding = e.map { it.occurrence.key }.toSet()
        assertTrue(ReminderPlan.liveSnoozes(mapOf(dose.key to at(8, 15).toEpochMilli()), outstanding, at(8, 5)).isEmpty())
    }
    @Test fun oldSnoozesExpire() {
        val dose = Schedule.day(snapshot, date).first()
        val outstanding = setOf(dose.key)
        assertEquals(1, ReminderPlan.liveSnoozes(mapOf(dose.key to at(8).toEpochMilli()), outstanding, at(9)).size)
        assertTrue(ReminderPlan.liveSnoozes(mapOf(dose.key to at(8).toEpochMilli()), outstanding, at(9, day = date.plusDays(2))).isEmpty())
    }
}
