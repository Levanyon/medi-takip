package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Most important first: name, person, dose, status, then the schedule (times and days) in one tonal
 * section. Dates and notes follow in a quieter group; recent use reuses the history matrix.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationDetail(snapshot: Snapshot, medicationId: String, today: LocalDate, back: () -> Unit, edit: (PersonEntity, MedicationScheduleEntity) -> Unit, archive: () -> Unit, corrections: DoseCorrections? = null, addStock: (Double) -> Unit = {}, now: LocalDateTime = today.atTime(LocalTime.now()), restore: () -> Unit = {}) {
    val medication = snapshot.medications.find { it.id == medicationId } ?: return
    val person = snapshot.people.find { it.id == medication.personId } ?: return
    val schedule = MedicationViews.latestRevision(snapshot, medicationId) ?: return
    val matrix = remember(snapshot, medicationId, today) { HistoryMatrices.build(snapshot, person.id, today.minusDays(6), today, medicationId) }
    var opened by remember { mutableStateOf<Occurrence?>(null) }
    var addingStock by remember { mutableStateOf(false) }
    val editable = !medication.archived && !person.archived
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxWidth - MediSpace.page * 2
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            item { MedicationHeader(stringResource(R.string.medication_detail_title), back, if (editable) ({ edit(person, schedule) }) else null) }
            item {
                MedicationHero(person, medication, schedule, medicationNextDose(snapshot, medicationId, today), today, now)
            }
            item { SectionHeader(stringResource(R.string.medication_usage)) }
            item {
                MedicationInfoGroup {
                    MedicationInfoRow(stringResource(R.string.times), "${timesText(schedule.times)} · ${schedule.dose}", R.drawable.ic_medication_clock, daysText(schedule.days))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MedicationInfoRow(stringResource(R.string.medication_period), TurkishFormat.date(LocalDate.ofEpochDay(schedule.start)), R.drawable.ic_medication_calendar,
                        schedule.end?.let { TurkishFormat.date(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.treatment_indefinite))
                    if (medication.stockEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StockLine(medication, schedule, editable) { addingStock = true }
                    }
                }
            }
            if (medication.prescriptionDate != null || medication.renewalDate != null) item {
                MedicationInfoGroup(stringResource(R.string.prescription_section)) {
                    medication.prescriptionDate?.let { MedicationInfoRow(stringResource(R.string.prescription_date), TurkishFormat.date(LocalDate.ofEpochDay(it)), R.drawable.ic_medication_calendar) }
                    medication.renewalDate?.let { MedicationInfoRow(stringResource(R.string.renewal_date), TurkishFormat.date(LocalDate.ofEpochDay(it)), R.drawable.ic_medication_calendar) }
                }
            }
            if (schedule.note.isNotBlank()) item {
                MedicationInfoGroup { MedicationInfoRow(stringResource(R.string.notes), schedule.note, R.drawable.ic_medication_note) }
            }
            if (medication.gtin != null) item {
                MedicationInfoGroup(stringResource(R.string.scan_packages)) {
                    Text(stringResource(R.string.scan_gtin, medication.gtin))
                    snapshot.packages.filter { it.medicationId == medicationId }.forEach { p ->
                        HorizontalDivider()
                        p.serial?.let { Text(stringResource(R.string.scan_serial, it)) }
                        p.batch?.let { Text(stringResource(R.string.scan_batch, it)) }
                        val expiry = p.expiry?.let(PackageExpiry::parseStored)
                        ExpiryLabel(expiry)
                        if (expiry?.expired(today) == true) Text(stringResource(R.string.scan_expired), color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.scan_package_record, StockPlan.format(p.quantityAdded), TurkishFormat.date(java.time.Instant.ofEpochMilli(p.addedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())))
                    }
                }
            }
            if (editable) item { PrimaryAction(stringResource(R.string.edit_medication), Icons.Default.Edit) { edit(person, schedule) } }
            // Archived: restore is the only action, and only once the person is active again.
            if (medication.archived) item {
                if (person.archived) Text(stringResource(R.string.restore_person_first), style = MaterialTheme.typography.bodyLarge, color = careColors.secondaryInk)
                else PrimaryAction(stringResource(R.string.restore), Icons.Default.Unarchive, action = restore)
            }
            item { SectionHeader(stringResource(R.string.last_seven_days)) }
            if (matrix.rows.isEmpty()) item { Text(stringResource(R.string.empty_history_body), color = careColors.secondaryInk) }
            else historyTable(matrix, today, scroll, available, sticky = false) { opened = it }
            if (editable) item { Box(Modifier.padding(top = MediSpace.large)) { DangerAction(stringResource(R.string.archive_medication), archive) } }
        }
    }
    opened?.let { HistoryCellDetail(it, { opened = null }, today, corrections) }
    if (addingStock) AddStockDialog({ addingStock = false }) { amount -> addingStock = false; addStock(amount) }
}

/** Stock at a glance: remaining amount, days it lasts, a text+icon "Azalıyor" when low, and Stok ekle. */
@Composable private fun StockLine(medication: MedicationEntity, schedule: MedicationScheduleEntity, editable: Boolean, add: () -> Unit) {
    val low = StockPlan.isLow(medication)
    val days = StockPlan.daysLeft(medication, schedule)
    val stockStatus = stringResource(if (low) R.string.stock_low else R.string.medication_stock)
    Column(Modifier.fillMaxWidth().semantics { stateDescription = stockStatus }, verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(R.drawable.ic_medication_stock), null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stock_remaining, StockPlan.format(medication.stockQuantity)), style = MaterialTheme.typography.titleMedium)
                days?.let { Text(stringResource(R.string.stock_days, it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Text(stringResource(R.string.medication_threshold, StockPlan.format(medication.stockThreshold)), style = MaterialTheme.typography.bodySmall, color = careColors.secondaryInk)
        if (low) MedicationStatusChip(stringResource(R.string.stock_low), true)
        if (editable) FilledTonalButton(onClick = add, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.stock_add), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable fun AddStockDialog(dismiss: () -> Unit, add: (Double) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    val parsed = StockPlan.parse(amount)?.takeIf { it > 0 }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    MediDialog(stringResource(R.string.stock_add), dismiss) {
        NumberField(amount, { amount = it }, stringResource(R.string.stock_add_amount))
        PrimaryAction(stringResource(R.string.stock_add), Icons.Default.Add, enabled = parsed != null) { focus.clearFocus(); parsed?.let(add) }
        CancelAction(dismiss)
    }
}
