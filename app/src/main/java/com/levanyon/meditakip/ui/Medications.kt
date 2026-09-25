package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.LocalDate

/**
 * Most important first: name, person, dose, status, then the schedule (times and days) in one tonal
 * section. Dates and notes follow in a quieter group; recent use reuses the history matrix.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationDetail(snapshot: Snapshot, medicationId: String, today: LocalDate, back: () -> Unit, edit: (PersonEntity, MedicationScheduleEntity) -> Unit, archive: () -> Unit, corrections: DoseCorrections? = null, addStock: (Double) -> Unit = {}, restore: () -> Unit = {}) {
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
            item { BackHeader(person.name, back) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(MediSpace.regular), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Default.Medication, null, Modifier.padding(MediSpace.regular).size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(schedule.name, style = MaterialTheme.typography.headlineMedium)
                        Text("${person.name} • ${schedule.dose}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                when {
                    medication.archived -> StatusChip(stringResource(R.string.archived), icon = Icons.Default.Archive)
                    schedule.active -> StatusChip(stringResource(R.string.in_use), completed = true, icon = Icons.Default.CheckCircle)
                    else -> StatusChip(stringResource(R.string.inactive), icon = Icons.Default.PauseCircle)
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                        Text(stringResource(R.string.times), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                            Schedule.minutes(schedule.times).forEach { minute ->
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                    Row(Modifier.padding(horizontal = MediSpace.regular, vertical = MediSpace.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                                        Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                                        Text("%02d:%02d".format(minute / 60, minute % 60), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                        LabelValue(stringResource(R.string.weekdays), daysText(schedule.days))
                    }
                }
            }
            if (medication.stockEnabled) item { StockLine(medication, schedule, editable) { addingStock = true } }
            if (editable) item { PrimaryAction(stringResource(R.string.edit_medication), Icons.Default.Edit) { edit(person, schedule) } }
            // Archived: restore is the only action, and only once the person is active again.
            if (medication.archived) item {
                if (person.archived) Text(stringResource(R.string.restore_person_first), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else PrimaryAction(stringResource(R.string.restore), Icons.Default.Unarchive, action = restore)
            }
            item { SectionHeader(stringResource(R.string.other_info)) }
            item {
                Column(Modifier.padding(horizontal = MediSpace.tiny), verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                    LabelValue(stringResource(R.string.start_date), TurkishFormat.date(LocalDate.ofEpochDay(schedule.start)))
                    LabelValue(stringResource(R.string.end_date), schedule.end?.let { TurkishFormat.date(LocalDate.ofEpochDay(it)) } ?: stringResource(R.string.no_end_date))
                    if (schedule.note.isNotBlank()) LabelValue(stringResource(R.string.notes), schedule.note)
                }
            }
            item { SectionHeader(stringResource(R.string.last_seven_days)) }
            if (matrix.rows.isEmpty()) item { Text(stringResource(R.string.empty_history_body), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else historyTable(matrix, today, scroll, available, sticky = false) { opened = it }
            if (editable) item { Box(Modifier.padding(top = MediSpace.large)) { QuietAction(stringResource(R.string.archive_medication), Icons.Default.Archive, action = archive) } }
        }
    }
    opened?.let { HistoryCellDetail(it, { opened = null }, today, corrections) }
    if (addingStock) AddStockDialog({ addingStock = false }) { amount -> addingStock = false; addStock(amount) }
}

/** Stock at a glance: remaining amount, days it lasts, a text+icon "Azalıyor" when low, and Stok ekle. */
@Composable private fun StockLine(medication: MedicationEntity, schedule: MedicationScheduleEntity, editable: Boolean, add: () -> Unit) {
    val low = StockPlan.isLow(medication)
    val days = StockPlan.daysLeft(medication, schedule)
    Surface(shape = MaterialTheme.shapes.large, color = if (low) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (low) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                Icon(if (low) Icons.Default.Warning else Icons.Default.Inventory2, null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.stock_remaining, StockPlan.format(medication.stockQuantity)), style = MaterialTheme.typography.titleMedium)
                    days?.let { Text(stringResource(R.string.stock_days, it), style = MaterialTheme.typography.bodyMedium) }
                }
                if (low) Text(stringResource(R.string.stock_low), style = MaterialTheme.typography.labelLarge)
            }
            if (editable) FilledTonalButton(onClick = add, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.stock_add), style = MaterialTheme.typography.labelLarge)
            }
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
