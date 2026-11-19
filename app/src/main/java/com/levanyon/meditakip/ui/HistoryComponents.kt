package com.levanyon.meditakip.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.LocalDate

@Composable internal fun HistoryFilterAction(open: () -> Unit) {
    FilledTonalIconButton(onClick = open, modifier = Modifier.size(MediSize.touch), shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Icon(painterResource(R.drawable.ic_phase5_filter), stringResource(R.string.history_filter), Modifier.size(20.dp), tint = careColors.secondaryInk)
    }
}

@Composable internal fun HistoryPersonFilter(people: List<PersonEntity>, selected: String?, select: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        HistoryChoice(stringResource(R.string.history_all), selected == null, { select(null) })
        people.forEach { person ->
            val label = if (person.archived) "${person.name}, ${stringResource(R.string.archived)}" else person.name
            HistoryChoice(label, selected == person.id, { select(person.id) }, compact = true)
        }
    }
}

@Composable private fun HistoryChoice(label: String, selected: Boolean, choose: () -> Unit, compact: Boolean = false) {
    Surface(selected = selected, onClick = choose, shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else careColors.secondaryInk,
        modifier = Modifier.widthIn(min = MediSize.touch, max = 240.dp).heightIn(min = MediSize.touch).semantics { contentDescription = label; role = Role.RadioButton }) {
        Box(Modifier.padding(horizontal = MediSpace.medium, vertical = MediSpace.small), contentAlignment = Alignment.Center) {
            Text(label, style = phase5Label, maxLines = if (compact) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun HistorySummary(progress: DailyProgress) {
    Surface(shape = MaterialTheme.shapes.medium, border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AdaptivePair(leading = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.history_seven), style = phase5Label, color = careColors.secondaryInk)
                    Text(stringResource(R.string.history_summary, progress.completed, progress.total), style = MaterialTheme.typography.titleMedium)
                }
            }, trailing = { StatusChip("${progress.percent}%", completed = true, showIcon = false) })
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = careColors.success, trackColor = careColors.progressTrack,
                gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable internal fun HistoryDateGroup(date: LocalDate, today: LocalDate, doses: List<Occurrence>, open: (Occurrence) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        Text(if (date == today) stringResource(R.string.today) else TurkishFormat.date(date), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Surface(shape = MaterialTheme.shapes.medium, border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface) {
            Column { doses.forEachIndexed { index, dose ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                HistoryDoseRow(dose) { open(dose) }
            } }
        }
    }
}

@Composable internal fun HistoryDoseRow(dose: Occurrence, open: () -> Unit) {
    val state = stringResource(if (dose.skipped) R.string.skipped else R.string.taken)
    Surface(onClick = open, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch).semantics { role = Role.Button }) {
        BoxWithConstraints(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            val stacked = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                Row(verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                    Text(dose.time.toString(), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dose.schedule.name, style = phase5Body)
                        Text(dose.personName, style = phase5Caption, color = careColors.secondaryInk)
                    }
                    if (!stacked) HistoryState(state, dose.skipped)
                }
                if (stacked) HistoryState(state, dose.skipped)
            }
        }
    }
}

@Composable private fun HistoryState(label: String, skipped: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraSmall,
        color = if (skipped) MaterialTheme.colorScheme.tertiaryContainer else careColors.successContainer,
        contentColor = if (skipped) MaterialTheme.colorScheme.onTertiaryContainer else careColors.successInk) {
        Text(label, style = phase5Caption, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable internal fun HistoryEmpty(filtered: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 72.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = mediSurfaceBorder()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_phase5_history), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Text(stringResource(if (filtered) R.string.history_filtered_empty else R.string.history_v2_empty), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(stringResource(if (filtered) R.string.history_filtered_body else R.string.history_v2_empty_body), style = phase5Body,
                    color = careColors.secondaryInk, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun HistoryFilterSheet(filter: HistoryFilter, dismiss: () -> Unit, apply: (HistoryFilter) -> Unit) {
    var days by rememberSaveable { mutableStateOf(filter.days) }
    var status by rememberSaveable { mutableStateOf(filter.status) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = MediSpace.page, end = MediSpace.page, bottom = MediSpace.large),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.history_filter), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                Text(stringResource(R.string.history_date_range), style = phase5Label)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                    listOf(7 to R.string.history_seven, 30 to R.string.history_thirty, null to R.string.history_all).forEach { (value, label) ->
                        HistoryChoice(stringResource(label), days == value, { days = value })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                Text(stringResource(R.string.history_status), style = phase5Label)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                    listOf(null to R.string.history_all, LogStatus.TAKEN to R.string.taken, LogStatus.SKIPPED to R.string.skipped).forEach { (value, label) ->
                        HistoryChoice(stringResource(label), status == value, { status = value })
                    }
                }
            }
            Text(stringResource(R.string.history_person_hint), style = phase5Caption, color = careColors.secondaryInk)
            Button(onClick = { apply(HistoryFilter(days, status)) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
                Text(stringResource(R.string.history_apply), style = phase5Label)
            }
        }
    }
}
