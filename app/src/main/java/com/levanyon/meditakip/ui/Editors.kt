package com.levanyon.meditakip.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
@Composable fun MedicationEditor(person: PersonEntity, schedule: MedicationScheduleEntity?, dismiss: () -> Unit, save: (MedicationScheduleEntity) -> Unit) {
    val context = rememberPickerContext()
    var name by rememberSaveable { mutableStateOf(schedule?.name ?: "") }
    var dose by rememberSaveable { mutableStateOf(schedule?.dose ?: "") }
    var note by rememberSaveable { mutableStateOf(schedule?.note ?: "") }
    var times by rememberSaveable { mutableStateOf(schedule?.times ?: "08:00") }
    var days by rememberSaveable { mutableIntStateOf(schedule?.days ?: 127) }
    var start by rememberSaveable { mutableLongStateOf(schedule?.start ?: LocalDate.now().toEpochDay()) }
    var end by rememberSaveable { mutableStateOf(schedule?.end) }
    var active by rememberSaveable { mutableStateOf(schedule?.active ?: true) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    // Advanced fields start open only when they already hold non-default values.
    var advanced by rememberSaveable { mutableStateOf(schedule != null && (schedule.end != null || schedule.note.isNotBlank() || !schedule.active || schedule.start > LocalDate.now().toEpochDay())) }
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
    FormScreen(stringResource(if (schedule == null) R.string.add_medication else R.string.edit_medication), dismiss) {
        StatusChip(stringResource(R.string.for_person, person.name), icon = Icons.Default.Person)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.medication_name)) }, singleLine = true, shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(dose, { dose = it }, label = { Text(stringResource(R.string.dose)) }, supportingText = { Text(stringResource(R.string.dose_help)) }, shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel(stringResource(R.string.times), stringResource(R.string.times_help))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            if (times.isNotBlank()) Schedule.minutes(times).forEach { minute ->
                val time = LocalTime.of(minute / 60, minute % 60).toString()
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickTime(minute) }, modifier = Modifier.heightIn(min = MediSize.primaryAction)) {
                            Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(MediSpace.small)); Text(time, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { times = Schedule.minutes(times).filter { it != minute }.joinToString(", ") { LocalTime.of(it / 60, it % 60).toString() } }, modifier = Modifier.size(MediSize.primaryAction)) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove_time, time))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { pickTime() }, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.add_time), style = MaterialTheme.typography.labelLarge)
        }
        FieldLabel(stringResource(R.string.weekdays))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            FilterChip(modifier = Modifier.heightIn(min = MediSize.touch), selected = days == 127, onClick = { days = 127 }, label = { Text(stringResource(R.string.every_day)) })
            stringArrayResource(R.array.weekday_short).forEachIndexed { i, label ->
                FilterChip(selected = days != 127 && days and (1 shl i) != 0, onClick = { days = if (days == 127) 1 shl i else days xor (1 shl i) }, label = { Text(label) }, modifier = Modifier.heightIn(min = MediSize.touch))
            }
        }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
            Text(stringResource(if (advanced) R.string.fewer_options else R.string.more_options), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        AnimatedVisibility(advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
                DateField(stringResource(R.string.start_date), TurkishFormat.date(LocalDate.ofEpochDay(start))) { pickDate(maxOf(start, today), today) { start = it } }
                DateField(stringResource(R.string.end_date), end?.let { TurkishFormat.date(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.no_end_date)) { pickDate(maxOf(end ?: start, start, today), maxOf(start, today)) { end = it } }
                if (end != null) QuietAction(stringResource(R.string.clear_end)) { end = null }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.notes_optional)) }, shape = MaterialTheme.shapes.medium, minLines = 2, modifier = Modifier.fillMaxWidth())
                SettingToggle(stringResource(R.string.active_toggle), active) { active = it }
                Text(stringResource(R.string.active_toggle_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (schedule != null) Text(stringResource(R.string.schedule_edit_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium) }
        // Kaydet lives at the end of the scrolled form; FormScreen keeps it clear of system bars and the keyboard.
        PrimaryAction(stringResource(R.string.save), Icons.Default.Check) {
            focus.clearFocus()
            error = when {
                name.isBlank() -> R.string.error_medication_name
                dose.isBlank() -> R.string.error_dose
                days == 0 -> R.string.error_days
                end != null && end!! < start -> R.string.error_dates
                times.isBlank() -> R.string.error_times
                else -> null
            }
            if (error == null) save(MedicationScheduleEntity("", "", person.name, name.trim(), dose.trim(), note, times, days, start, end, active, LocalDate.now().toEpochDay()))
        }
    }
}

@Composable private fun FieldLabel(title: String, help: String? = null) {
    Column(Modifier.padding(top = MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        help?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun DateField(label: String, value: String, pick: () -> Unit) {
    Surface(onClick = pick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
        Row(Modifier.padding(horizontal = MediSpace.page, vertical = MediSpace.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
            Box(Modifier.weight(1f)) { LabelValue(label, value) }
            Icon(Icons.Default.Edit, null)
        }
    }
}

/** Atla: names the dose, takes an optional short note (e.g. why), and confirms. Reversible with Geri Al. */
@Composable fun SkipDialog(item: Occurrence, dismiss: () -> Unit, confirm: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    MediDialog(stringResource(R.string.skip_title), dismiss) {
        Text("${item.personName} · ${item.schedule.name} · ${item.time}", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(note, { if (it.length <= NOTE_LIMIT) note = it }, label = { Text(stringResource(R.string.skip_reason)) }, singleLine = true,
            shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
        PrimaryAction(stringResource(R.string.skip), Icons.Default.DoNotDisturbOn) { confirm(note.ifBlank { null }) }
        CancelAction(dismiss)
    }
}

/** Archiving is reversible (Arşivden Çıkar), so the confirmation uses the normal primary action, not a red one. */
@Composable fun ArchiveConfirmation(message: String, dismiss: () -> Unit, confirm: () -> Unit) {
    MediDialog(stringResource(R.string.archive_title), dismiss) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        PrimaryAction(stringResource(R.string.archive), Icons.Default.Archive, action = confirm)
        CancelAction(dismiss)
    }
}
@Composable fun SettingToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction).toggleable(value = checked, role = Role.Switch, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = MediSpace.regular), style = MaterialTheme.typography.bodyLarge); Switch(checked, onCheckedChange = null)
    }
}
