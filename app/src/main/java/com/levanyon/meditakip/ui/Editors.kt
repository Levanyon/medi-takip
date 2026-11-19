package com.levanyon.meditakip.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.*
import java.util.UUID

@Composable fun PersonEditor(person: PersonEntity?, dismiss: () -> Unit, archive: (() -> Unit)? = null, save: (PersonEntity) -> Unit) {
    var name by rememberSaveable { mutableStateOf(person?.name ?: "") }
    var note by rememberSaveable { mutableStateOf(person?.note ?: "") }
    val focus = LocalFocusManager.current
    FormScreen(stringResource(if (person == null) R.string.add_person else R.string.edit_person), dismiss) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.notes_optional)) }, shape = MaterialTheme.shapes.medium, minLines = 2, modifier = Modifier.fillMaxWidth())
        PrimaryAction(stringResource(R.string.save), Icons.Default.Check, name.isNotBlank()) {
            focus.clearFocus()
            save((person ?: PersonEntity(UUID.randomUUID().toString(), "")).copy(name = name.trim(), note = note))
        }
        if (person != null && archive != null) {
            Spacer(Modifier.height(MediSpace.large))
            QuietAction(stringResource(R.string.archive_person), Icons.Default.Archive, action = archive)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun MedicationEditor(person: PersonEntity, schedule: MedicationScheduleEntity?, dismiss: () -> Unit, initialStock: StockSettings? = null, initialPrescription: Prescription = Prescription(), scannedName: String? = null, scannedGtin: String? = null, archive: (() -> Unit)? = null, save: (MedicationScheduleEntity, StockSettings, Prescription) -> Unit) {
    val context = rememberPickerContext()
    var name by rememberSaveable { mutableStateOf(schedule?.name ?: scannedName ?: "") }
    var scanReviewed by rememberSaveable { mutableStateOf(false) }
    var dose by rememberSaveable { mutableStateOf(schedule?.dose ?: "") }
    var note by rememberSaveable { mutableStateOf(schedule?.note ?: "") }
    var times by rememberSaveable { mutableStateOf(schedule?.times ?: "08:00") }
    var days by rememberSaveable { mutableIntStateOf(schedule?.days ?: 127) }
    var start by rememberSaveable { mutableLongStateOf(schedule?.start ?: LocalDate.now().toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(schedule?.end) }
    var active by rememberSaveable { mutableStateOf(schedule?.active ?: true) }
    var written by rememberSaveable { mutableStateOf(initialPrescription.written) }
    var renewal by rememberSaveable { mutableStateOf(initialPrescription.renewal) }
    var renewalEnabled by rememberSaveable { mutableStateOf(initialPrescription.enabled) }
    var leadDays by rememberSaveable { mutableStateOf(initialPrescription.leadDays.toString()) }
    val stockStart = initialStock ?: StockSettings(false, 0.0, 1.0, 0.0)
    var stockEnabled by rememberSaveable { mutableStateOf(stockStart.enabled) }
    var stockQuantity by rememberSaveable { mutableStateOf(if (stockStart.enabled) StockPlan.format(stockStart.quantity) else "") }
    var stockPerDose by rememberSaveable { mutableStateOf(StockPlan.format(stockStart.perDose)) }
    var stockThreshold by rememberSaveable { mutableStateOf(if (stockStart.threshold > 0) StockPlan.format(stockStart.threshold) else "") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    // B5: edits apply from today (effectiveFrom = today), so dates before today would silently have no
    // effect. The pickers therefore never offer past days; an existing past start stays as it is.
    val today = LocalDate.now().toEpochDay()
    fun pickDate(value: Long, earliest: Long, selected: (Long) -> Unit) {
        val date = LocalDate.ofEpochDay(value)
        DatePickerDialog(context, { _, year, month, day -> selected(LocalDate.of(year, month + 1, day).toEpochDay()) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.minDate = LocalDate.ofEpochDay(earliest).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }
    fun pickTime(old: Int? = null) {
        TimePickerDialog(context, { _, hour, minute ->
            val values = if (times.isBlank()) mutableListOf() else Schedule.minutes(times).toMutableList()
            old?.let { values.remove(it) }; values.add(hour * 60 + minute)
            times = values.distinct().sorted().joinToString(", ") { LocalTime.of(it / 60, it % 60).toString() }
        }, old?.div(60) ?: 8, old?.rem(60) ?: 0, true).show()
    }
    val focus = LocalFocusManager.current
    val title = stringResource(if (schedule == null) R.string.add_medication else R.string.edit_medication)
    FormScreen(title, dismiss, header = { MedicationHeader(title, dismiss) }) {
        if (scannedGtin != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(MediSpace.regular), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                    Icon(painterResource(R.drawable.ic_scan_prefill), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(if (scannedName != null) R.string.scan_prefill else R.string.scan_read))
                        if (scannedName != null) Text(stringResource(R.string.scan_prefill_body), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.scan_gtin, scannedGtin), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        StatusChip(stringResource(R.string.for_person, person.name), icon = Icons.Default.Person)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.medication_name)) }, isError = error == R.string.error_medication_name, shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.dose)) }, supportingText = { Text(stringResource(R.string.dose_help)) }, isError = error == R.string.error_dose, shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
        MedicationScheduleSection(times, days, { days = it }, { pickTime(it) }) { minute ->
            times = Schedule.minutes(times).filter { it != minute }.joinToString(", ") { LocalTime.of(it / 60, it % 60).toString() }
        }
        MedicationInfoGroup(stringResource(R.string.medication_period)) {
            DateField(stringResource(R.string.start_date), TurkishFormat.date(LocalDate.ofEpochDay(start))) { pickDate(maxOf(start, today), today) { start = it } }
            DateField(stringResource(R.string.end_date), end?.let { TurkishFormat.date(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.no_end_date)) { pickDate(maxOf(end ?: start, start, today), maxOf(start, today)) { end = it } }
            if (end != null) QuietAction(stringResource(R.string.clear_end)) { end = null }
        }
        MedicationInfoGroup(stringResource(R.string.prescription_section)) {
            DateField(stringResource(R.string.prescription_date), written?.let { TurkishFormat.date(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.optional_date)) {
                pickDate(written ?: today, Prescription.DATE_RANGE.first) { written = it }
            }
            if (written != null) QuietAction(stringResource(R.string.clear_prescription_date)) { written = null }
            DateField(stringResource(R.string.renewal_date), renewal?.let { TurkishFormat.date(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.optional_date)) {
                pickDate(renewal ?: today, Prescription.DATE_RANGE.first) { renewal = it }
            }
            if (renewal != null) QuietAction(stringResource(R.string.clear_renewal_date)) {
                renewal = null; renewalEnabled = false
                leadDays = (leadDays.toIntOrNull()?.takeIf { it >= 0 } ?: 0).toString()
            }
            if (renewal != null) {
                SettingToggle(stringResource(R.string.renewal_reminder), renewalEnabled) {
                    renewalEnabled = it
                    if (!it) leadDays = (leadDays.toIntOrNull()?.takeIf { days -> days >= 0 } ?: 0).toString()
                }
                if (renewalEnabled) {
                    OutlinedTextField(leadDays, { leadDays = it }, label = { Text(stringResource(R.string.renewal_lead_days)) },
                        singleLine = true, shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.renewal_time_hint), style = MaterialTheme.typography.bodyMedium, color = careColors.secondaryInk)
                }
            }
        }
        MedicationInfoGroup(stringResource(R.string.medication_stock)) {
            // Optional stock tracking (D3.7): decimals allowed ("0,5"); an empty threshold means no warning.
            SettingToggle(stringResource(R.string.stock_tracking), stockEnabled) { stockEnabled = it }
            Text(stringResource(R.string.stock_tracking_help), style = MaterialTheme.typography.bodyMedium, color = careColors.secondaryInk)
            if (stockEnabled) {
                NumberField(stockQuantity, { stockQuantity = it }, stringResource(R.string.stock_quantity))
                NumberField(stockPerDose, { stockPerDose = it }, stringResource(R.string.stock_per_dose))
                NumberField(stockThreshold, { stockThreshold = it }, stringResource(R.string.stock_threshold))
            }
        }
        if (scannedGtin != null) ScanSafety()
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.notes_optional)) }, shape = MaterialTheme.shapes.small, minLines = 2, modifier = Modifier.fillMaxWidth())
        SettingToggle(stringResource(R.string.active_toggle), active) { active = it }
        Text(stringResource(R.string.active_toggle_help), style = MaterialTheme.typography.bodyMedium, color = careColors.secondaryInk)
        if (schedule != null) Text(stringResource(R.string.schedule_edit_note), style = MaterialTheme.typography.bodyMedium, color = careColors.secondaryInk)
        if (scannedGtin != null) SettingToggle(stringResource(R.string.scan_review_confirm), scanReviewed) { scanReviewed = it }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        // Kaydet lives at the end of the scrolled form; FormScreen keeps it clear of system bars and the keyboard.
        PrimaryAction(stringResource(R.string.save), Icons.Default.Check, enabled = scannedGtin == null || scanReviewed) {
            focus.clearFocus()
            error = when {
                name.isBlank() -> R.string.error_medication_name
                dose.isBlank() -> R.string.error_dose
                stockEnabled && stockSettings(stockQuantity, stockPerDose, stockThreshold)?.valid != true -> R.string.error_stock
                days == 0 -> R.string.error_days
                end != null && end!! < start -> R.string.error_dates
                times.isBlank() -> R.string.error_times
                !Prescription(written, renewal, renewalEnabled, leadDays.toIntOrNull() ?: -1).valid -> R.string.error_prescription
                else -> null
            }
            if (error == null) save(MedicationScheduleEntity("", "", person.name, name.trim(), dose.trim(), note, times, days, start, end, active, LocalDate.now().toEpochDay()), if (stockEnabled) stockSettings(stockQuantity, stockPerDose, stockThreshold)!! else stockStart.copy(enabled = false), Prescription(written, renewal, renewalEnabled, leadDays.toInt()))
        }
        archive?.let { DangerAction(stringResource(R.string.archive_medication), it) }
    }
}

/**
 * Reads the stock fields of the form (tracking on). Null when a number is missing or not a number; an empty
 * threshold means "no warning" (0). When tracking is off the stored numbers are kept, not reset.
 */
private fun stockSettings(quantity: String, perDose: String, threshold: String): StockSettings? {
    val q = StockPlan.parse(quantity) ?: return null
    val p = StockPlan.parse(perDose) ?: return null
    val t = if (threshold.isBlank()) 0.0 else StockPlan.parse(threshold) ?: return null
    return StockSettings(true, q, p, t)
}

@Composable fun NumberField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, { text -> if (text.length <= 8 && text.all { it.isDigit() || it == ',' || it == '.' }) change(text) }, label = { Text(label) }, singleLine = true,
        shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
}

@Composable private fun DateField(label: String, value: String, pick: () -> Unit) {
    Surface(onClick = pick, shape = MaterialTheme.shapes.small, border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
        Row(Modifier.padding(horizontal = MediSpace.page, vertical = MediSpace.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Box(Modifier.weight(1f)) { LabelValue(label, value) }
            Icon(painterResource(R.drawable.ic_medication_calendar), null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Atla: names the dose, takes an optional short note (e.g. why), and confirms. Reversible with Geri Al. */
/**
 * [dateNotice]: for a past day, the dialog also names the date, so it doubles as the confirmation that
 * history is being changed (no second dialog; DECISIONS D3.6).
 */
@Composable fun SkipDialog(item: Occurrence, dismiss: () -> Unit, dateNotice: String? = null, confirm: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    MediDialog(stringResource(R.string.skip_title), dismiss) {
        Text("${item.personName} · ${item.schedule.name} · ${item.time}", style = MaterialTheme.typography.titleMedium)
        dateNotice?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        OutlinedTextField(note, { if (it.length <= NOTE_LIMIT) note = it }, label = { Text(stringResource(R.string.skip_reason)) }, singleLine = true,
            shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
        PrimaryAction(stringResource(R.string.skip), Icons.Default.DoNotDisturbOn) { focus.clearFocus(); confirm(note.ifBlank { null }) }
        CancelAction(dismiss)
    }
}

/** Archive keeps the existing explicit confirmation and uses the V3 destructive role. */
@Composable fun ArchiveConfirmation(message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    MediDialog(stringResource(R.string.archive_title), dismiss) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        DangerAction(stringResource(R.string.archive), confirm)
        CancelAction(dismiss)
    }
}
@Composable fun SettingToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction).toggleable(value = checked, role = Role.Switch, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = MediSpace.regular), style = MaterialTheme.typography.bodyLarge); Switch(checked, onCheckedChange = null)
    }
}
