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
    fun completeOnboarding() = app.preferences.completeOnboarding()
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
    fun taken(item: Occurrence) = completion(item, true)
    fun undo(item: Occurrence) = completion(item, false)
    private fun completion(item: Occurrence, taken: Boolean) {
        if (item.key in pending.value) return
        pending.update { it + (item.key to if (taken) MedicationLogEntity(item.key, item.schedule.id, item.date.toEpochDay(), item.minute, System.currentTimeMillis()) else null) }
        viewModelScope.launch {
            try {
                if (taken) repository.completions.taken(item) else repository.completions.undo(item)
                stored.first { state -> state.logs.any { it.key == item.key } == taken }
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error.value = app.getString(R.string.error_save) }
            finally { pending.update { it - item.key } }
            app.reminders.refresh()
        }
    }
}
