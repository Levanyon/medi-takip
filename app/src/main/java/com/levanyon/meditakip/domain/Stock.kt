package com.levanyon.meditakip.domain

import com.levanyon.meditakip.data.MedicationEntity
import com.levanyon.meditakip.data.MedicationScheduleEntity
import java.util.Locale
import kotlin.math.floor

/** A medication whose stock reached its threshold (docs/DECISIONS.md D3.7/D3.8). */
data class LowStock(val medicationId: String, val personId: String, val personName: String, val name: String, val quantity: Double, val daysLeft: Int?)

/** Optional per-medication stock settings as edited in the medication form. */
data class StockSettings(val enabled: Boolean, val quantity: Double, val perDose: Double, val threshold: Double) {
    val valid get() = !enabled || (quantity >= 0 && perDose > 0 && threshold >= 0)
}

/** Pure stock math. The database changes stock only through Repository.Completions and addStock. */
object StockPlan {
    /** Average number of doses per day of a revision over a week (e.g. 2 times on 5 weekdays = 1.43). */
    fun dosesPerDay(schedule: MedicationScheduleEntity): Double =
        if (!schedule.active) 0.0 else Schedule.minutes(schedule.times).size * Integer.bitCount(schedule.days and 127) / 7.0

    /** Whole days the remaining stock lasts at the current schedule; null when stock is off or nothing is planned. */
    fun daysLeft(medication: MedicationEntity, schedule: MedicationScheduleEntity?): Int? {
        if (!medication.stockEnabled || schedule == null || medication.stockPerDose <= 0) return null
        val perDay = medication.stockPerDose * dosesPerDay(schedule)
        if (perDay <= 0) return null
        return floor(medication.stockQuantity.coerceAtLeast(0.0) / perDay).toInt()
    }

    /** Low = tracking on, a threshold set (> 0), and the quantity at or below it. */
    fun isLow(medication: MedicationEntity) = medication.stockEnabled && medication.stockThreshold > 0 && medication.stockQuantity <= medication.stockThreshold

    /** Low medications of active people (optionally one person), for the Bugün banner and the notification. */
    fun low(snapshot: Snapshot, personId: String? = null): List<LowStock> {
        val people = snapshot.people.filter { !it.archived && (personId == null || it.id == personId) }.associateBy { it.id }
        return snapshot.medications.filter { !it.archived && it.personId in people && isLow(it) }.mapNotNull { medication ->
            val schedule = MedicationViews.latestRevision(snapshot, medication.id) ?: return@mapNotNull null
            LowStock(medication.id, medication.personId, people.getValue(medication.personId).name, schedule.name, medication.stockQuantity, daysLeft(medication, schedule))
        }.sortedBy { it.daysLeft ?: Int.MAX_VALUE }
    }

    /** "12", "11,5" — Turkish decimal comma, at most two decimals; negative stock is shown as 0. */
    fun format(amount: Double): String {
        val value = amount.coerceAtLeast(0.0)
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.forLanguageTag("tr-TR"), "%.2f", value).trimEnd('0').trimEnd(',')
    }

    /** Accepts "12", "11,5" or "11.5"; null when the text is not a number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
}
