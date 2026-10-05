package com.levanyon.meditakip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*

val ScreenPadding = PaddingValues(start = MediSpace.page, end = MediSpace.page, top = MediSpace.regular, bottom = MediSpace.extraLarge)

@Composable fun ScreenHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(MediSpace.tiny), modifier = Modifier.padding(top = MediSpace.small, bottom = MediSpace.tiny)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = MediSpace.medium, bottom = MediSpace.tiny), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun BackHeader(title: String, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        MediIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), action = back)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
    }
}
/** A calm control surface with an explicit label and a full touch target. */
@Composable fun MediIconButton(icon: ImageVector, contentDescription: String, enabled: Boolean = true, action: () -> Unit) {
    FilledTonalIconButton(onClick = action, enabled = enabled, modifier = Modifier.size(MediSize.touch), shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, contentDescription, Modifier.size(MediSize.icon))
    }
}
@Composable fun PrimaryAction(label: String, icon: ImageVector = Icons.Default.Add, enabled: Boolean = true, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction), shape = MaterialTheme.shapes.small, elevation = ButtonDefaults.buttonElevation(defaultElevation = MediElevation.flat, pressedElevation = MediElevation.flat, focusedElevation = MediElevation.flat, hoveredElevation = MediElevation.flat), contentPadding = PaddingValues(MediSpace.regular)) {
        Icon(icon, null, Modifier.size(MediSize.icon)); Spacer(Modifier.width(MediSpace.medium)); Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
/** Low-emphasis action for secondary paths; still a full 48dp target. */
@Composable fun QuietAction(label: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch), shape = MaterialTheme.shapes.small) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(MediSize.icon)); Spacer(Modifier.width(MediSpace.small)) }
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
/** Tonal, centered empty state: icon badge, title, one helpful sentence and at most one action. */
@Composable fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Default.Medication, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, border = mediSurfaceBorder(), tonalElevation = MediElevation.flat, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = MediSpace.large, vertical = MediSpace.extraLarge), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(MediSpace.regular).size(MediSize.featureIcon), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null && actionLabel != null) PrimaryAction(actionLabel, action = action)
        }
    }
}
@Composable fun Avatar(name: String, large: Boolean = true) {
    val size = if (large) MediSize.primaryAction else MediSize.touch
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(Modifier.sizeIn(minWidth = size, minHeight = size).padding(MediSpace.small), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(TurkishFormat.locale), style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
@Composable fun StatusChip(label: String, completed: Boolean = false, showIcon: Boolean = true, icon: ImageVector? = null) {
    Surface(shape = MaterialTheme.shapes.extraSmall, color = if (completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (completed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.padding(horizontal = MediSpace.medium, vertical = MediSpace.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            if (showIcon) Icon(icon ?: if (completed) Icons.Default.CheckCircle else Icons.Default.Schedule, null, Modifier.size(MediSize.statusIcon))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Segmented person switcher shared by Bugün and Geçmiş. One tonal track holds pill segments;
 * with many people the track scrolls horizontally so nobody becomes unreachable.
 */
data class PersonTab(val id: String, val name: String, val remaining: Int = 0, val archived: Boolean = false)
@Composable fun PersonSelector(people: List<PersonTab>, selected: String?, onSelect: (String) -> Unit, showCounts: Boolean = true, tonal: Boolean = false) {
    if (tonal) {
        TodayPersonSelector(people, selected, onSelect, showCounts)
        return
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(MediSpace.tiny), horizontalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
            people.forEach { person ->
                val isSelected = person.id == selected
                val container by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(MediMotion.feedbackMillis), label = "personTab")
                val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                val description = when {
                    person.archived -> stringResource(R.string.person_archived_description, person.name)
                    !showCounts -> person.name
                    person.remaining > 0 -> stringResource(R.string.person_pending_description, person.name, person.remaining)
                    else -> stringResource(R.string.person_done_description, person.name)
                }
                Surface(shape = CircleShape, color = container, contentColor = content,
                    modifier = Modifier.heightIn(min = MediSize.touch).selectable(isSelected, role = Role.Tab) { onSelect(person.id) }.semantics(mergeDescendants = true) { contentDescription = description }) {
                    Row(Modifier.padding(horizontal = MediSpace.page, vertical = MediSpace.medium), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                        if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                        else if (person.archived) Icon(Icons.Default.Archive, null, Modifier.size(20.dp))
                        Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
                        if (showCounts && person.remaining > 0) Surface(shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary) {
                            Text(person.remaining.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = MediSpace.small))
                        }
                    }
                }
            }
        }
    }
}

/** Supporting progress only; the next dose is presented once, in the hero below. */
@Suppress("UNUSED_PARAMETER") // Preserve the existing public API.
@Composable fun DaySummary(progress: DailyProgress, next: Occurrence?) {
    val fraction by animateFloatAsState(progress.fraction, animationSpec = tween(MediMotion.progressMillis), label = "progress")
    val summary = stringResource(R.string.completed_fraction, progress.completed, progress.total)
    Column(Modifier.padding(vertical = MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        AdaptivePair(
            leading = { Text(stringResource(R.string.today_medications), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() }) },
            trailing = { Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(MediSpace.tiny).semantics { contentDescription = summary },
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            gapSize = 0.dp, drawStopIndicator = {})
    }
}

enum class DoseStatus { NEXT, PENDING, OVERDUE, TAKEN, SKIPPED, MISSED }

/**
 * Today's single highlighted dose uses the hero; all others are plain, readable timeline entries.
 * The callbacks and status semantics are shared by both presentations.
 */
@Composable
@Suppress("UNUSED_PARAMETER") // `last` remains source-compatible; no decorative connector is needed.
fun ScheduledDoseRow(
    item: Occurrence, editable: Boolean = false, busy: Boolean = false, status: DoseStatus = defaultStatus(item, editable),
    highlight: Boolean = status == DoseStatus.NEXT, last: Boolean = true,
    onTaken: () -> Unit = {}, onUndo: () -> Unit = {}, onSkip: () -> Unit = {}, onChangeTime: () -> Unit = {},
) {
    if (highlight && !item.handled) {
        NextDoseHero(item, status) {
            if (editable) DoseActions(item, true, busy, onTaken, onUndo, onSkip, onChangeTime)
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
        AdaptivePair(
            leading = { Text(item.time.toString(), style = MaterialTheme.typography.headlineSmall, color = if (item.handled) scheme.onSurfaceVariant else scheme.onSurface) },
            trailing = { DoseStatusLabel(status, item) }
        )
        Text(item.schedule.name, style = MaterialTheme.typography.titleMedium, color = if (item.handled) scheme.onSurfaceVariant else scheme.onSurface)
        Text(item.schedule.dose, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        if (item.schedule.note.isNotBlank() && !item.handled) Text(item.schedule.note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (item.skipped && !item.note.isNullOrBlank()) Text(stringResource(R.string.skipped_note, item.note), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (editable) DoseActions(item, false, busy, onTaken, onUndo, onSkip, onChangeTime)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DoseActions(item: Occurrence, highlight: Boolean, busy: Boolean, onTaken: () -> Unit, onUndo: () -> Unit, onSkip: () -> Unit, onChangeTime: () -> Unit) {
    val undoDescription = stringResource(R.string.undo_action_description, item.personName, item.schedule.name, item.time.toString())
    if (item.handled) {
        // Quiet, text-only secondary actions: one line at normal sizes, wrapping (never clipping) at large fonts.
        FlowRow(Modifier.fillMaxWidth().padding(top = MediSpace.small), horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
            if (!item.skipped) {
                val timeDescription = stringResource(R.string.change_time_description, item.personName, item.schedule.name, item.time.toString())
                TextButton(onClick = onChangeTime, enabled = !busy, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = timeDescription }) {
                    Text(stringResource(R.string.change_time), style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = onUndo, enabled = !busy, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = undoDescription }) {
                Text(stringResource(R.string.undo), style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        // FlowRow: İçildi and Atla side by side when they fit, stacked at large font sizes; nothing is ever clipped.
        FlowRow(Modifier.fillMaxWidth().padding(top = MediSpace.small), horizontalArrangement = Arrangement.spacedBy(MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            val takenDescription = stringResource(R.string.dose_action_description, item.personName, item.schedule.name, item.time.toString())
            val skipDescription = stringResource(R.string.skip_action_description, item.personName, item.schedule.name, item.time.toString())
            val takenModifier = (if (highlight) Modifier.weight(1f) else Modifier).heightIn(min = if (highlight) MediSize.primaryAction else MediSize.touch).semantics { contentDescription = takenDescription }
            if (highlight) Button(shape = MaterialTheme.shapes.small, onClick = onTaken, enabled = !busy, modifier = takenModifier) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.taken), style = MaterialTheme.typography.labelLarge)
            } else TextButton(shape = MaterialTheme.shapes.small, onClick = onTaken, enabled = !busy, modifier = takenModifier) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.taken), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onSkip, enabled = !busy, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = skipDescription }) {
                Text(stringResource(R.string.skip), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
fun defaultStatus(item: Occurrence, editable: Boolean) = when {
    item.takenAt != null -> DoseStatus.TAKEN
    item.skipped -> DoseStatus.SKIPPED
    editable -> DoseStatus.PENDING
    else -> DoseStatus.MISSED
}
@Composable private fun DoseStatusLabel(status: DoseStatus, item: Occurrence) {
    val (label, icon) = when (status) {
        DoseStatus.TAKEN -> stringResource(R.string.taken_status, TurkishFormat.completed(item.takenAt!!, item.date)) to null
        DoseStatus.SKIPPED -> stringResource(R.string.skipped) to Icons.Default.DoNotDisturbOn
        DoseStatus.NEXT -> stringResource(R.string.next_label) to null
        DoseStatus.OVERDUE -> stringResource(R.string.overdue) to Icons.Default.Alarm
        DoseStatus.PENDING -> stringResource(R.string.pending) to null
        DoseStatus.MISSED -> stringResource(R.string.not_marked) to Icons.Outlined.Circle
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        icon?.let { Icon(it, null, Modifier.size(MediSize.statusIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Kişiler row: avatar, name, active medication count; the whole row opens the person. */
@Composable fun PersonRow(person: PersonEntity, activeCount: Int, open: () -> Unit) {
    val description = if (person.archived) stringResource(R.string.person_archived_description, person.name)
        else stringResource(R.string.open_person_description, person.name, activeCount)
    Surface(onClick = open, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction).semantics { contentDescription = description }) {
        Row(Modifier.padding(MediSpace.regular), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Avatar(person.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(person.name, style = MaterialTheme.typography.titleLarge)
                if (person.archived) StatusChip(stringResource(R.string.archived), icon = Icons.Default.Archive)
                else Text(stringResource(R.string.active_count, activeCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Medication row inside a person: identity first, schedule summary second, the whole row opens the detail. */
@Composable fun MedicationRow(schedule: MedicationScheduleEntity, archived: Boolean = false, open: () -> Unit) {
    val inUse = schedule.active && !archived
    Surface(onClick = open, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction)) {
        Row(Modifier.padding(MediSpace.regular), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Surface(shape = MaterialTheme.shapes.medium, color = if (inUse) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Default.Medication, null, tint = if (inUse) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(MediSpace.medium).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(schedule.name, style = MaterialTheme.typography.titleLarge)
                Text(schedule.dose, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(timesText(schedule.times) + if (schedule.days != 127) " • " + daysText(schedule.days, short = true) else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (archived) Box(Modifier.padding(top = MediSpace.tiny)) { StatusChip(stringResource(R.string.archived), icon = Icons.Default.Archive) }
                else if (!schedule.active) Box(Modifier.padding(top = MediSpace.tiny)) { StatusChip(stringResource(R.string.inactive), icon = Icons.Default.PauseCircle) }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
fun timesText(times: String) = times.replace(", ", " • ").replace(",", " • ")
@Composable fun daysText(days: Int, short: Boolean = false): String {
    val names = stringArrayResource(if (short) R.array.weekday_short else R.array.weekday_names)
    return if (days == 127) stringResource(R.string.every_day) else names.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" • ")
}
@Composable fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyLarge) }
}
