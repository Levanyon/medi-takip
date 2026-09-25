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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri

/** The dose-record operations screens may call; implemented by [AppViewModel], faked in tests. */
interface DoseCorrections {
    fun taken(item: Occurrence, takenAt: Long? = null)
    fun skip(item: Occurrence, note: String? = null)
    fun undo(item: Occurrence)
    fun setTakenTime(item: Occurrence, takenAt: Long)
}

class AppViewModel(application: Application) : AndroidViewModel(application), DoseCorrections {
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
    val lastBackup = app.preferences.lastBackup
    val backupStartedAt = app.preferences.backupStartedAt
    val backupPreview = MutableStateFlow<BackupContent?>(null)
    val fileBusy = MutableStateFlow(false)
    val fileMessage = MutableStateFlow<String?>(null)
    val reportToShare = MutableStateFlow<Uri?>(null)
    val safetyCopyAvailable = MutableStateFlow(app.backups.safetyCopy.exists())

    private fun fileOperation(failure: Int, operation: suspend () -> Unit) {
        if (fileBusy.value || saving.value || pending.value.isNotEmpty()) {
            fileMessage.value = app.getString(R.string.backup_busy); return
        }
        fileBusy.value = true
        viewModelScope.launch {
            try { operation() }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = if (e is BackupException) when (e.problem) {
                    BackupProblem.NOT_A_BACKUP -> R.string.backup_not_a_backup
                    BackupProblem.TOO_NEW -> R.string.backup_too_new
                    BackupProblem.TOO_LARGE -> R.string.backup_too_large
                    BackupProblem.DAMAGED -> R.string.backup_damaged
                } else failure
                fileMessage.value = app.getString(message)
            } finally { fileBusy.value = false; safetyCopyAvailable.value = app.backups.safetyCopy.exists() }
        }
    }
    fun exportBackup(uri: Uri) = fileOperation(R.string.backup_save_failed) {
        withContext(Dispatchers.IO) { requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use { app.backups.export(it) } }
        fileMessage.value = app.getString(R.string.backup_saved)
    }
    fun inspectBackup(uri: Uri) {
        backupPreview.value = null
        fileOperation(R.string.backup_damaged) {
            backupPreview.value = withContext(Dispatchers.IO) { app.backups.inspect(requireNotNull(app.contentResolver.openInputStream(uri))) }
        }
    }
    fun inspectSafetyCopy() = fileOperation(R.string.backup_damaged) {
        backupPreview.value = withContext(Dispatchers.IO) { app.backups.inspect(android.util.AtomicFile(app.backups.safetyCopy).openRead()) }
    }
    fun confirmRestore() {
        val content = backupPreview.value ?: return
        fileOperation(R.string.backup_restore_failed) {
            withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                app.reminders.restoreData { app.backups.restore(content) }
            }
            backupPreview.value = null
            fileMessage.value = app.getString(R.string.backup_restored)
        }
    }
    fun createReport(personId: String) = fileOperation(R.string.report_failed) {
        reportToShare.value = withContext(Dispatchers.IO) {
            val report = DoctorReports.build(repository.snapshot(), personId, java.time.LocalDate.now())
            val directory = java.io.File(app.cacheDir, "reports").apply { check(exists() || mkdirs()) }
            // Only generated cache files older than a day; never user documents or backups.
            directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
            val file = java.io.File(directory, "MediTakip-${java.util.UUID.randomUUID()}.pdf")
            try { file.outputStream().use { ReportPdf.write(report, it) } }
            catch (e: Exception) { file.delete(); throw e }
            androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.reports", file)
        }
    }
    fun perform(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (saving.value || fileBusy.value) return
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
    fun medication(person: PersonEntity, id: String?, schedule: MedicationScheduleEntity, stock: StockSettings? = null, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.saveMedication(person, id, schedule, stock) }
    fun addStock(medicationId: String, amount: Double, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.addStock(medicationId, amount) }
    fun archiveMedication(id: String, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.archiveMedication(id) }
    fun restorePerson(id: String, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.restorePerson(id) }
    fun restoreMedication(id: String, onSuccess: () -> Unit = {}) = perform(onSuccess) { repository.restoreMedication(id) }
    /** İçildi; [takenAt] = null means now. */
    override fun taken(item: Occurrence, takenAt: Long?) {
        val at = takenAt ?: System.currentTimeMillis()
        completion(item, log(item, LogStatus.TAKEN, at), matchTime = takenAt != null) { repository.completions.taken(item, takenAt) }
    }
    /** Atla with an optional short reason. */
    override fun skip(item: Occurrence, note: String?) =
        completion(item, log(item, LogStatus.SKIPPED, System.currentTimeMillis(), note?.trim()?.take(NOTE_LIMIT)?.ifBlank { null })) { repository.completions.skip(item, note) }
    /** Geri Al / Kaydı kaldır. */
    override fun undo(item: Occurrence) = completion(item, null) { repository.completions.undo(item) }
    /** Saati değiştir (late marking) for a taken dose. */
    override fun setTakenTime(item: Occurrence, takenAt: Long) {
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
        if (fileBusy.value || item.key in pending.value) return
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
