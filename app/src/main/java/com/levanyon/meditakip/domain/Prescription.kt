package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.*
import java.time.*

/** Current prescription metadata, independent of dose revisions and stock. Dates are epoch days. */
data class Prescription(val written: Long? = null, val renewal: Long? = null, val enabled: Boolean = false, val leadDays: Int = 0) {
    val valid: Boolean get() = (written == null || written in DATE_RANGE) && (renewal == null || renewal in DATE_RANGE) &&
        (written == null || renewal == null || renewal >= written) && leadDays >= 0
    fun applyTo(m: MedicationEntity) = m.copy(prescriptionDate = written, renewalDate = renewal,
        renewalReminderEnabled = enabled, renewalLeadDays = leadDays)
    companion object {
        // Same supported calendar range as the existing backup contract.
        val DATE_RANGE = LocalDate.of(2000, 1, 1).toEpochDay()..LocalDate.of(2200, 1, 1).toEpochDay()
        fun from(m: MedicationEntity) = Prescription(m.prescriptionDate, m.renewalDate, m.renewalReminderEnabled, m.renewalLeadDays)
    }
}

data class RenewalEvent(val medicationId: String, val name: String, val renewal: Long, val leadDays: Int, val at: Instant) {
    val key get() = "renewal:$medicationId:$renewal:$leadDays"
}

/** Date-oriented planning only; executed by the existing single-alarm Reminders engine. */
object RenewalPlan {
    fun events(snapshot: Snapshot, settings: AppSettings, now: Instant, zone: ZoneId): List<RenewalEvent> {
        if (!settings.notifications) return emptyList()
        val today = now.atZone(zone).toLocalDate().toEpochDay()
        return snapshot.medications.mapNotNull { m ->
            val p = Prescription.from(m)
            val renewal = p.renewal ?: return@mapNotNull null
            val person = snapshot.people.find { it.id == m.personId } ?: return@mapNotNull null
            val s = MedicationViews.latestRevision(snapshot, m.id) ?: return@mapNotNull null
            if (!p.valid || !p.enabled || m.archived || person.archived || !s.active || s.effectiveTo != null ||
                renewal < today || (s.end != null && s.end < today)) return@mapNotNull null
            val at = LocalDate.ofEpochDay(renewal).minusDays(p.leadDays.toLong()).atTime(9, 0).atZone(zone).toInstant()
            RenewalEvent(m.id, s.name, renewal, p.leadDays, at)
        }.sortedWith(compareBy({ it.at }, { it.medicationId }))
    }
    fun due(events: List<RenewalEvent>, dispatched: Set<String>, lowerMillis: Long, now: Instant) =
        if (lowerMillis <= 0) emptyList() else events.filter {
            it.key !in dispatched && it.at.toEpochMilli() >= lowerMillis && it.at <= now && it.at >= now.minus(ReminderPlan.DELIVERY_WINDOW)
        }
    fun next(events: List<RenewalEvent>, dispatched: Set<String>, now: Instant) = events.firstOrNull { it.at > now && it.key !in dispatched }
}
