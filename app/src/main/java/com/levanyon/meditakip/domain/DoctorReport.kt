package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import java.time.LocalDate

data class DoctorReport(val person: PersonEntity, val from: LocalDate, val through: LocalDate,
    val medications: List<MedicationScheduleEntity>, val records: List<Occurrence>)

object DoctorReports {
    /** Recorded history only; never invent unmarked doses or interpret the records. */
    fun build(snapshot: Snapshot, personId: String, today: LocalDate): DoctorReport {
        val person = snapshot.people.single { it.id == personId }
        val ids = snapshot.medications.filter { it.personId == personId }.map { it.id }.toSet()
        val active = MedicationViews.current(snapshot).filter { it.medicationId in ids && it.active }
        val from = today.minusDays(29)
        val records = (0L..29L).flatMap { offset ->
            Schedule.day(snapshot, today.minusDays(offset)).filter { it.personId == personId && it.handled }
        }
        return DoctorReport(person, from, today, active, records)
    }
}
