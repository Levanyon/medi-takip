package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Selection only: occurrence generation and completion/revision rules belong to Schedule.day. */
internal fun medicationNextDose(snapshot: Snapshot, medicationId: String, today: LocalDate): Occurrence? {
    return NextDose.forMedication(snapshot, medicationId, today)
}

@Composable
internal fun MedicationInfoGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = mediSurfaceBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MediSpace.regular), verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
            content()
        }
    }
}

@Composable
internal fun MedicationInfoRow(label: String, value: String, @DrawableRes icon: Int, supporting: String? = null) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(icon), null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = careColors.secondaryInk)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp))
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = careColors.secondaryInk) }
        }
    }
}

@Composable
internal fun MedicationHero(person: PersonEntity, medication: MedicationEntity, schedule: MedicationScheduleEntity,
    next: Occurrence?, today: LocalDate, now: LocalDateTime) {
    MedicationInfoGroup {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(R.drawable.ic_medication_pill), null,
                    Modifier.padding(15.dp).size(26.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Box(Modifier.weight(1f)) {
                AdaptivePair(leading = {
                    Column(verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                        Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                        Text(person.name, style = MaterialTheme.typography.labelMedium, color = careColors.secondaryInk)
                    }
                }, trailing = {
                    MedicationStatusChip(stringResource(when {
                        medication.archived -> R.string.archived
                        schedule.active -> R.string.person_medication_active
                        else -> R.string.inactive
                    }), false)
                })
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Row(Modifier.padding(MediSpace.medium), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                Icon(painterResource(R.drawable.ic_medication_clock), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (next != null && next.date.atTime(next.time) < now) R.string.medication_due else R.string.medication_next), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Text(next?.let { "${if (it.date != today) TurkishFormat.date(it.date) + " · " else ""}${it.time} · ${it.schedule.dose}" }
                        ?: stringResource(R.string.medication_no_next), style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
    }
}

@Composable
internal fun MedicationTimeChip(time: String, edit: () -> Unit, remove: () -> Unit) {
    val description = stringResource(R.string.medication_edit_time, time)
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = edit, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = description }) {
                Icon(painterResource(R.drawable.ic_medication_time), null, Modifier.size(16.dp))
                Spacer(Modifier.width(MediSpace.small))
                Text(time, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = remove, modifier = Modifier.size(MediSize.touch)) {
                Icon(Icons.Default.Close, stringResource(R.string.remove_time, time), Modifier.size(MediSize.statusIcon))
            }
        }
    }
}

@Composable
internal fun MedicationHeader(title: String, back: () -> Unit, edit: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        MedicationIconButton(R.drawable.ic_medication_back, stringResource(R.string.back), 22, back)
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
            modifier = Modifier.weight(1f).semantics { heading() })
        edit?.let { MedicationIconButton(R.drawable.ic_medication_edit, stringResource(R.string.edit_medication), 20, it) }
    }
}

@Composable
private fun MedicationIconButton(@DrawableRes icon: Int, label: String, size: Int, action: () -> Unit) {
    FilledTonalIconButton(onClick = action, modifier = Modifier.size(MediSize.touch), shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = careColors.secondaryInk)) {
        Icon(painterResource(icon), label, Modifier.size(size.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationScheduleSection(times: String, days: Int, changeDays: (Int) -> Unit, pickTime: (Int?) -> Unit, removeTime: (Int) -> Unit) {
    var choosingDays by rememberSaveable { mutableStateOf(false) }
    MedicationInfoGroup(stringResource(R.string.medication_schedule_title)) {
        Surface(onClick = { choosingDays = !choosingDays }, shape = MaterialTheme.shapes.small,
            border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
            Row(Modifier.padding(MediSpace.medium), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.weekdays), style = MaterialTheme.typography.labelMedium, color = careColors.secondaryInk)
                    Text(daysText(days), style = MaterialTheme.typography.bodyLarge)
                }
                Icon(if (choosingDays) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
        }
        if (choosingDays) FlowRow(horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            FilterChip(modifier = Modifier.heightIn(min = MediSize.touch), selected = days == 127, onClick = { changeDays(127) }, label = { Text(stringResource(R.string.every_day)) })
            stringArrayResource(R.array.weekday_short).forEachIndexed { i, label ->
                FilterChip(selected = days != 127 && days and (1 shl i) != 0,
                    onClick = { changeDays(if (days == 127) 1 shl i else days xor (1 shl i)) },
                    label = { Text(label) }, modifier = Modifier.heightIn(min = MediSize.touch))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            if (times.isNotBlank()) Schedule.minutes(times).forEach { minute ->
                MedicationTimeChip(LocalTime.of(minute / 60, minute % 60).toString(), { pickTime(minute) }, { removeTime(minute) })
            }
            FilledTonalButton(onClick = { pickTime(null) }, shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary), modifier = Modifier.heightIn(min = MediSize.touch)) {
                Icon(painterResource(R.drawable.ic_medication_plus), null, Modifier.size(16.dp))
                Spacer(Modifier.width(MediSpace.small))
                Text(stringResource(R.string.add_time), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(stringResource(R.string.times_help), style = MaterialTheme.typography.bodySmall, color = careColors.secondaryInk)
    }
}
