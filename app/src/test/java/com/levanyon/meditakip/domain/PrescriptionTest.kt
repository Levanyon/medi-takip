package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class PrescriptionTest {
    private val date = LocalDate.of(2026,10,20)
    private val d = date.toEpochDay()
    private val now = date.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant()
    private val med = MedicationEntity("m","p", prescriptionDate = d-10, renewalDate = d, renewalReminderEnabled = true, renewalLeadDays = 3)
    private val schedule = MedicationScheduleEntity("s","m","Anne","İlaç","1 tablet","","08:00",127,d-20,null,true,d-20)
    private val snapshot = Snapshot(listOf(PersonEntity("p","Anne")),listOf(med),listOf(schedule))
    private fun events(m: MedicationEntity = med, s: MedicationScheduleEntity = schedule, at: Instant = now, zone: ZoneId = ZoneOffset.UTC) =
        RenewalPlan.events(snapshot.copy(medications=listOf(m),schedules=listOf(s)),AppSettings(),at,zone)
    @Test fun optionalDatesAndUnfixedIntervalsAreValid() {
        listOf(Prescription(),Prescription(written=d),Prescription(renewal=d),Prescription(d,d),Prescription(d,d+700,true,0)).forEach { assertTrue(it.valid) }
    }
    @Test fun invalidDatesAndNegativeLeadAreRejected() {
        listOf(Prescription(d,d-1),Prescription(written=Long.MAX_VALUE),Prescription(renewal=Long.MIN_VALUE),Prescription(leadDays=-1)).forEach { assertFalse(it.valid) }
    }
    @Test fun finiteAndIndefiniteTreatmentRetainDoseBoundaries() {
        Schedule.validate(schedule); Schedule.validate(schedule.copy(end=d))
        assertTrue(Schedule.day(snapshot.copy(schedules=listOf(schedule.copy(end=d))),date.plusDays(1)).isEmpty())
        assertFalse(Schedule.day(snapshot,date.plusDays(1)).isEmpty())
        assertTrue(Schedule.day(snapshot,date.minusDays(21)).isEmpty())
    }
    @Test fun invalidTreatmentDatesFail() {
        listOf(schedule.copy(end=schedule.start-1),schedule.copy(start=Long.MAX_VALUE),schedule.copy(end=Long.MAX_VALUE)).forEach {
            assertThrows(ScheduleValidationException::class.java) { Schedule.validate(it) }
        }
    }
    @Test fun renewalSchedulesAtNineLocalWithLeadDays() {
        val zone=ZoneId.of("Europe/Istanbul")
        assertEquals(date.minusDays(3).atTime(9,0).atZone(zone).toInstant(), events(zone=zone).single().at)
    }
    @Test fun missingDisabledArchivedInactiveEndedAndPastDoNotSchedule() {
        assertTrue(events(med.copy(renewalDate=null)).isEmpty())
        assertTrue(events(med.copy(renewalReminderEnabled=false)).isEmpty())
        assertTrue(events(med.copy(archived=true)).isEmpty())
        assertTrue(events(s=schedule.copy(active=false)).isEmpty())
        assertTrue(events(s=schedule.copy(effectiveTo=d-6)).isEmpty())
        assertTrue(events(s=schedule.copy(end=d-6)).isEmpty())
        assertTrue(events(at=date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).isEmpty())
        assertTrue(RenewalPlan.events(snapshot.copy(people=listOf(PersonEntity("p","Anne",archived=true))),AppSettings(),now,ZoneOffset.UTC).isEmpty())
        assertTrue(RenewalPlan.events(snapshot,AppSettings(notifications=false),now,ZoneOffset.UTC).isEmpty())
    }
    @Test fun pastTriggerHasNoRepeatedLoopOrBacklog() {
        val event=events().single(); val later=event.at.plusSeconds(3600)
        assertNull(RenewalPlan.next(events(),emptySet(),later))
        assertTrue(RenewalPlan.due(events(),emptySet(),0,later).isEmpty())
        assertEquals(listOf(event),RenewalPlan.due(events(),emptySet(),event.at.toEpochMilli(),later))
        assertTrue(RenewalPlan.due(events(),setOf(event.key),event.at.toEpochMilli(),later).isEmpty())
        assertTrue(RenewalPlan.due(events(),emptySet(),event.at.toEpochMilli(),later.plusSeconds(86400)).isEmpty())
    }
    @Test fun dateAndLeadChangesCreateNewIdentityWithoutDoseCollision() {
        val e=events().single()
        assertNotEquals(e.key,events(med.copy(renewalDate=d+1)).single().key)
        assertNotEquals(e.key,events(med.copy(renewalLeadDays=1)).single().key)
        assertTrue(e.key.startsWith("renewal:"))
        assertFalse(ReminderPlan.events(snapshot,AppSettings(),now,ZoneOffset.UTC).any { it.key==e.key })
    }
    @Test fun timezoneAndDstKeepNineOClock() {
        listOf("Europe/Istanbul","America/New_York","Pacific/Auckland").forEach {
            val zone=ZoneId.of(it); assertEquals(LocalTime.of(9,0),events(zone=zone).single().at.atZone(zone).toLocalTime())
        }
    }
}
