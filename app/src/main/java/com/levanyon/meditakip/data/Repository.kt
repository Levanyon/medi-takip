package com.levanyon.meditakip.data

import androidx.room.withTransaction
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.flow.combine
import java.time.*
import java.util.UUID

/** Longest stored skip reason (a short note, not a diary). */
const val NOTE_LIMIT = 80

class Repository(private val db: MediDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao = db.dao()
    val snapshots = combine(dao.observePeople(), dao.observeMedications(), dao.observeSchedules(), dao.observeLogs(), ::Snapshot)
    val completions = Completions()

    /**
     * Dose records (docs/DECISIONS.md D3.3–D3.7). Every operation is one transaction that keeps stock in
     * step: only a newly inserted TAKEN record decrements it, and removing a TAKEN record restores it.
     * All operations are idempotent: repeating them never duplicates a record or a stock change.
     */
    inner class Completions {
        /** İçildi. [takenAt] defaults to now; late marking passes the real time. Returns false if already recorded. */
        suspend fun taken(item: Occurrence, takenAt: Long? = null): Boolean = db.withTransaction {
            val now = clock.millis()
            val inserted = dao.insertLog(MedicationLogEntity(item.key, item.schedule.id, item.date.toEpochDay(), item.minute, takenAt ?: now, LogStatus.TAKEN, now)) != -1L
            if (inserted) changeStock(item, -1)
            inserted
        }
        /** Atla, with an optional short reason. Returns false if the dose already has a record. */
        suspend fun skip(item: Occurrence, note: String? = null): Boolean = db.withTransaction {
            val now = clock.millis()
            dao.insertLog(MedicationLogEntity(item.key, item.schedule.id, item.date.toEpochDay(), item.minute, now, LogStatus.SKIPPED, now, note?.trim()?.take(NOTE_LIMIT)?.ifBlank { null })) != -1L
        }
        /** Geri Al / Kaydı kaldır: removes this one dose record; a removed TAKEN record gives its stock back. */
        suspend fun undo(item: Occurrence): Boolean = db.withTransaction {
            val existing = dao.log(item.key) ?: return@withTransaction false
            dao.undo(item.key)
            if (existing.status == LogStatus.TAKEN) changeStock(item, +1)
            true
        }
        /** Saati değiştir: corrects the actual time of a TAKEN record; recordedAt keeps the original entry time. */
        suspend fun setTakenTime(item: Occurrence, takenAt: Long): Boolean = db.withTransaction {
            val existing = dao.log(item.key)?.takeIf { it.status == LogStatus.TAKEN } ?: return@withTransaction false
            dao.updateLog(existing.copy(takenAt = takenAt))
            true
        }
        private suspend fun changeStock(item: Occurrence, direction: Int) {
            val medication = dao.medications().find { it.id == item.schedule.medicationId } ?: return
            if (medication.stockEnabled) dao.adjustStock(medication.id, direction * medication.stockPerDose)
        }
    }
    suspend fun snapshot() = db.withTransaction { Snapshot(dao.people(), dao.medications(), dao.schedules(), dao.logs()) }
    suspend fun savePerson(person: PersonEntity) { require(person.name.isNotBlank()); dao.savePerson(person.copy(name = person.name.trim())) }
    /**
     * Creates a medication or edits one. An edit never changes an existing revision: it closes the open
     * revision at yesterday (`effectiveTo = today - 1`) and inserts a new one effective from today.
     * Editing on the day a revision was created therefore leaves a "never-effective" revision
     * (`effectiveTo = effectiveFrom - 1`, see [isNeverEffective]); its recorded doses stay visible.
     */
    suspend fun saveMedication(person: PersonEntity, medicationId: String?, schedule: MedicationScheduleEntity) = db.withTransaction {
        Schedule.validate(schedule)
        val id = medicationId ?: UUID.randomUUID().toString()
        val today = LocalDate.now(clock).toEpochDay()
        dao.schedules().filter { it.medicationId == id && it.effectiveTo == null }.forEach { dao.saveSchedule(it.copy(effectiveTo = today - 1)) }
        // Keep the existing medication row (stock, archive state): an edit only adds a schedule revision (D3.7).
        val existing = dao.medications().find { it.id == id }
        dao.saveMedication(existing?.copy(personId = person.id) ?: MedicationEntity(id, person.id))
        dao.saveSchedule(schedule.copy(id = UUID.randomUUID().toString(), medicationId = id, personName = person.name, effectiveFrom = today, effectiveTo = null, sequence = dao.maxSequence() + 1))
    }
    suspend fun archiveMedication(id: String) = db.withTransaction {
        val today = LocalDate.now(clock).toEpochDay()
        dao.medications().find { it.id == id }?.let { dao.saveMedication(it.copy(archived = true)) }
        dao.schedules().filter { it.medicationId == id && it.effectiveTo == null }.forEach { dao.saveSchedule(it.copy(effectiveTo = today - 1)) }
    }
    suspend fun archivePerson(person: PersonEntity) = db.withTransaction {
        dao.medications().filter { it.personId == person.id }.forEach { archiveMedication(it.id) }
        dao.savePerson(person.copy(archived = true))
    }
    /** Restores only the person. Their archived medications stay archived and are restored one by one. */
    suspend fun restorePerson(personId: String) = db.withTransaction {
        dao.people().find { it.id == personId }?.let { dao.savePerson(it.copy(archived = false)) }
    }
    /**
     * Restores an archived medication. Past revisions are never touched: a copy of the newest revision
     * becomes effective from today, so the days spent in the archive stay empty in history.
     * The owner must be active (restore the person first).
     */
    suspend fun restoreMedication(id: String) = db.withTransaction {
        val medication = dao.medications().find { it.id == id } ?: return@withTransaction
        val person = dao.people().single { it.id == medication.personId }
        require(!person.archived) { "Restore the person before their medication" }
        val schedules = dao.schedules().filter { it.medicationId == id }
        val latest = MedicationViews.latestRevision(Snapshot(schedules = schedules), id) ?: return@withTransaction
        dao.saveMedication(medication.copy(archived = false))
        if (latest.effectiveTo != null) {
            val today = LocalDate.now(clock).toEpochDay()
            dao.saveSchedule(latest.copy(id = UUID.randomUUID().toString(), personName = person.name, effectiveFrom = today, effectiveTo = null, sequence = dao.maxSequence() + 1))
        }
    }
}
