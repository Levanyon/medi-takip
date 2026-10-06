package com.levanyon.meditakip.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.DailyProgress

internal val PeoplePadding = PaddingValues(start = MediSpace.page, end = MediSpace.page, top = 28.dp, bottom = MediSpace.extraLarge)

// Scoped Figma type roles inherit the Phase 1 font family; other screens retain their typography.
private val peopleLabel @Composable get() = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
private val peopleBody @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp)

@Composable
internal fun PeopleHeading(showAdd: Boolean, add: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
            Text(stringResource(R.string.people), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.people_v2_subtitle), style = peopleBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showAdd) {
            val description = stringResource(R.string.people_add_person)
            FilledTonalButton(onClick = add, shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = description }) {
                Icon(painterResource(R.drawable.ic_people_plus), null, Modifier.size(18.dp))
                Spacer(Modifier.width(MediSpace.small))
                Text(stringResource(R.string.people_add), style = peopleLabel)
            }
        }
    }
}

@Composable
internal fun PersonSummaryCard(person: PersonEntity, activeCount: Int, progress: DailyProgress, open: () -> Unit) {
    val status = when {
        progress.total == 0 -> stringResource(R.string.people_today_no_doses)
        progress.remaining == 0 -> stringResource(R.string.today_complete_title)
        else -> stringResource(R.string.people_today_remaining, progress.remaining)
    }
    Surface(onClick = open, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = mediSurfaceBorder(), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { role = Role.Button }) {
        Row(Modifier.padding(MediSpace.regular), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(person.name, large = false, primary = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.people_active_count, activeCount), style = peopleLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, style = peopleLabel, color = MaterialTheme.colorScheme.primary)
            }
            PeopleChevron()
        }
    }
}

@Composable
internal fun PersonHeader(person: PersonEntity, activeCount: Int, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalIconButton(onClick = back, modifier = Modifier.size(MediSize.touch), shape = MaterialTheme.shapes.small,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
            Icon(painterResource(R.drawable.ic_people_back), stringResource(R.string.back), Modifier.size(20.dp))
        }
        Avatar(person.name, large = false, size = 52.dp, primary = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(person.name, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), modifier = Modifier.semantics { heading() })
            if (!person.archived) Text(if (activeCount == 0) stringResource(R.string.people_no_active) else stringResource(R.string.people_active_count, activeCount),
                style = peopleLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PersonTodaySummary(progress: DailyProgress) {
    val fraction by animateFloatAsState(progress.fraction, tween(MediMotion.progressMillis), label = "personProgress")
    val title = stringResource(R.string.person_today_title)
    val summary = stringResource(R.string.completed_fraction, progress.completed, progress.total)
    val remaining = if (progress.remaining == 0) stringResource(R.string.today_complete_title) else stringResource(R.string.person_remaining, progress.remaining)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(MediSpace.regular), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progress.total == 0) {
                Text(title, style = peopleLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.people_today_no_doses), style = MaterialTheme.typography.titleMedium)
            } else {
                AdaptivePair(
                    leading = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = peopleLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(summary, style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    trailing = { Text(remaining, style = peopleLabel, color = MaterialTheme.colorScheme.primary) }
                )
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp)
                    .semantics { contentDescription = "$title, $summary, $remaining" },
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    gapSize = 0.dp, drawStopIndicator = {})
            }
        }
    }
}

@Composable
internal fun PersonAddMedication(add: () -> Unit) {
    Button(onClick = add, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction), shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(MediSpace.regular)) {
        Icon(painterResource(R.drawable.ic_people_plus), null, Modifier.size(18.dp))
        Spacer(Modifier.width(MediSpace.small))
        Text(stringResource(R.string.person_add_medication), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PersonMedicationGroup(medications: List<MedicationScheduleEntity>, lowStock: Set<String>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.person_active_medications), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = mediSurfaceBorder()) {
            Column {
                medications.forEachIndexed { index, schedule ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MedicationSummaryRow(schedule, schedule.medicationId in lowStock) { open(schedule.medicationId) }
                }
            }
        }
    }
}

@Composable
internal fun MedicationSummaryRow(schedule: MedicationScheduleEntity, lowStock: Boolean, open: () -> Unit) {
    val status = stringResource(if (lowStock) R.string.person_medication_low else R.string.person_medication_active)
    Surface(onClick = open, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).semantics { role = Role.Button; stateDescription = status }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.clearAndSetSemantics {}) {
                Box(Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp).padding(MediSpace.tiny), contentAlignment = Alignment.Center) {
                    Text("Rx", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Box(Modifier.weight(1f)) {
                AdaptivePair(
                    leading = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(schedule.name, style = MaterialTheme.typography.titleSmall.copy(lineHeight = 21.sp))
                            Text(stringResource(R.string.person_medication_schedule, timesText(schedule.times), schedule.dose), style = peopleLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (schedule.days != 127) Text(daysText(schedule.days, short = true), style = peopleLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    trailing = { MedicationStatusChip(status, lowStock) }
                )
            }
            PeopleChevron()
        }
    }
}

@Composable
internal fun MedicationStatusChip(label: String, lowStock: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraSmall,
        color = if (lowStock) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (lowStock) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(label, style = peopleLabel.copy(fontSize = 11.sp, lineHeight = 14.sp), modifier = Modifier.padding(horizontal = MediSpace.small, vertical = 5.dp))
    }
}

@Composable
private fun PeopleChevron() {
    Icon(painterResource(R.drawable.ic_people_chevron), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PersonEditAction(name: String, edit: () -> Unit) {
    val description = stringResource(R.string.edit_person_description, name)
    FilledTonalButton(onClick = edit, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = description }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Text(stringResource(R.string.person_edit), style = peopleLabel)
    }
}

@Composable
internal fun PeopleEmptyState(add: () -> Unit) {
    PersonEmptyState(stringResource(R.string.people_empty_title), stringResource(R.string.people_empty_body), R.drawable.ic_people_people, people = true) {
        Button(onClick = add, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = MediSize.touch), contentPadding = PaddingValues(horizontal = 18.dp, vertical = MediSpace.medium)) {
            Text(stringResource(R.string.people_add_person), style = peopleLabel)
        }
    }
}

@Composable
internal fun PersonMedicationsEmptyState() {
    PersonEmptyState(stringResource(R.string.person_empty_title), stringResource(R.string.person_empty_body), R.drawable.ic_people_medication)
}

@Composable
private fun PersonEmptyState(title: String, body: String, icon: Int, people: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, border = mediSurfaceBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = MediSpace.large, vertical = if (people) 28.dp else MediSpace.large),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(if (people) 64.dp else 56.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(if (people) 28.dp else 26.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(body, style = peopleBody, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            action?.invoke()
        }
    }
}
