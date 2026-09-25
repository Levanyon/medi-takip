package com.levanyon.meditakip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MediApp
    private val repository = app.repository
    val settings = app.preferences.state
    val onboardingCompleted = app.preferences.onboardingCompleted
    fun completeOnboarding(startSetup: Boolean = false) = app.preferences.completeOnboarding(startSetup)
    val setupPending = app.preferences.setupPending
    fun finishSetup() = app.preferences.finishSetup()
    val selectedPerson = app.preferences.lastPerson
    fun selectPerson(id: String?) = app.preferences.rememberPerson(id)
    fun settings(value: AppSettings) = perform { app.preferences.save(value) }
    private val stored = repository.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Snapshot())
    private val pending = MutableStateFlow<Map<String, MedicationLogEntity?>>(emptyMap())
    val snapshot = combine(stored, pending) { state, changes -> state.copy(logs = state.logs.filter { it.key !in changes } + changes.values.filterNotNull()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Snapshot())
    val error = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)
    fun perform(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { block(); onSuccess(); app.reminders.refresh() }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = if (e is ScheduleValidationException) when (e.reason) {
                    ScheduleValidation.NAME -> R.string.error_medication_name
                    ScheduleValidation.DOSE -> R.string.error_dose
                    ScheduleValidation.DAYS -> R.string.error_days
                    ScheduleValidation.DATES -> R.string.error_dates
                    ScheduleValidation.TIMES -> R.string.error_times
                } else R.string.error_save
                error.value = app.getString(message)
            } finally { saving.value = false }
        }
    }
    fun person(person: PersonEntity, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.savePerson(person) }
    fun archivePerson(person: PersonEntity, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.archivePerson(person) }
    fun medication(person: PersonEntity, id: String?, schedule: MedicationScheduleEntity, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.saveMedication(person, id, schedule) }
    fun archiveMedication(id: String, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.archiveMedication(id) }
    fun restorePerson(id: String, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.restorePerson(id) }
    fun restoreMedication(id: String, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.restoreMedication(id) }
    /** İçildi; [takenAt] = null means now. */
    fun taken(item: Occurrence, takenAt: Long? = null) {
        val at = takenAt ?: System.currentTimeMillis()
        completion(item, log(item, LogStatus.TAKEN, at), matchTime = takenAt != null) { repository.completions.taken(item, takenAt) }
    }
    /** Atla with an optional short reason. */
    fun skip(item: Occurrence, note: String? = null) =
        completion(item, log(item, LogStatus.SKIPPED, System.currentTimeMillis(), note?.trim()?.take(NOTE_LIMIT)?.ifBlank { null })) { repository.completions.skip(item, note) }
    /** Geri Al / Kaydı kaldır. */
    fun undo(item: Occurrence) = completion(item, null) { repository.completions.undo(item) }
    /** Saati değiştir (late marking) for a taken dose. */
    fun setTakenTime(item: Occurrence, takenAt: Long) {
        val current = snapshot.value.logs.find { it.key == item.key } ?: return
        completion(item, current.copy(takenAt = takenAt), matchTime = true) { repository.completions.setTakenTime(item, takenAt) }
    }
    private fun log(item: Occurrence, status: LogStatus, at: Long, note: String? = null) =
        MedicationLogEntity(item.key, item.schedule.id, item.date.toEpochDay(), item.minute, at, status, System.currentTimeMillis(), note)

    /**
     * Shows [expected] immediately (optimistic), writes through the repository, then waits until the
     * database reports the same state for this dose before dropping the optimistic value.
     */
    private fun completion(item: Occurrence, expected: MedicationLogEntity?, matchTime: Boolean = false, write: suspend () -> Unit) {
        if (item.key in pending.value) return
        pending.update { it + (item.key to expected) }
        viewModelScope.launch {
            try {
                write()
                stored.first { state ->
                    val saved = state.logs.find { it.key == item.key }
                    saved?.status == expected?.status && (!matchTime || saved?.takenAt == expected?.takenAt)
                }
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error.value = app.getString(R.string.error_save) }
            finally { pending.update { it - item.key } }
            app.reminders.refresh()
        }
    }
}
