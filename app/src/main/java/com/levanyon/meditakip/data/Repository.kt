package com.levanyon.meditakip.data

import androidx.room.withTransaction
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.flow.combine
import java.time.*
import java.util.UUID

interface CompletionStore {
    suspend fun insert(log: MedicationLogEntity)
    suspend fun delete(key: String)
}
class CompletionRepository(private val store: CompletionStore, private val clock: Clock = Clock.systemUTC()) {
    suspend fun taken(item: Occurrence) = store.insert(MedicationLogEntity(item.key, item.schedule.id, item.date.toEpochDay(), item.minute, clock.millis()))
    suspend fun undo(item: Occurrence) = store.delete(item.key)
}

class Repository(private val db: MediDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao = db.dao()
    val snapshots = combine(dao.observePeople(), dao.observeMedications(), dao.observeSchedules(), dao.observeLogs(), ::Snapshot)
    val completions = CompletionRepository(object : CompletionStore {
        override suspend fun insert(log: MedicationLogEntity) = dao.insertLog(log)
        override suspend fun delete(key: String) = dao.undo(key)
    })
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
        dao.saveMedication(MedicationEntity(id, person.id))
        dao.saveSchedule(schedule.copy(id = UUID.randomUUID().toString(), medicationId = id, personName = person.name, effectiveFrom = today, effectiveTo = null))
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
}
