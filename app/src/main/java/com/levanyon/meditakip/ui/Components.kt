package com.levanyon.meditakip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun BackHeader(title: String, back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back, modifier = Modifier.size(MediSize.touch)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
    }
}
@Composable fun PrimaryAction(label: String, icon: ImageVector = Icons.Default.Add, enabled: Boolean = true, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.primaryAction), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(MediSpace.regular)) {
        Icon(icon, null, Modifier.size(MediSize.icon)); Spacer(Modifier.width(MediSpace.medium)); Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
/** Low-emphasis action for secondary paths; still a full 48dp target. */
@Composable fun QuietAction(label: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch), shape = MaterialTheme.shapes.large) {
        icon?.let { Icon(it, null, tint = color); Spacer(Modifier.width(MediSpace.small)) }
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}
/** Tonal, centered empty state: icon badge, title, one helpful sentence and at most one action. */
@Composable fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Default.Medication, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = MediSpace.large, vertical = MediSpace.extraLarge), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(icon, null, Modifier.padding(MediSpace.page).size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null && actionLabel != null) PrimaryAction(actionLabel, action = action)
        }
    }
}
@Composable fun Avatar(name: String, large: Boolean = true) {
    val size = if (large) MediSize.primaryAction else MediSize.touch
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.sizeIn(minWidth = size, minHeight = size).padding(MediSpace.small), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(TurkishFormat.locale), style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
@Composable fun StatusChip(label: String, completed: Boolean = false, showIcon: Boolean = true, icon: ImageVector? = null) {
    Surface(shape = CircleShape, color = if (completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (completed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.padding(horizontal = MediSpace.medium, vertical = MediSpace.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            if (showIcon) Icon(icon ?: if (completed) Icons.Default.CheckCircle else Icons.Default.Schedule, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Segmented person switcher shared by Bugün and Geçmiş. One tonal track holds pill segments;
 * with many people the track scrolls horizontally so nobody becomes unreachable.
 */
data class PersonTab(val id: String, val name: String, val remaining: Int = 0, val archived: Boolean = false)
@Composable fun PersonSelector(people: List<PersonTab>, selected: String?, onSelect: (String) -> Unit, showCounts: Boolean = true) {
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

/** A single compact summary: progress ring + "3 / 5 tamamlandı" + what is next. */
@Composable fun DaySummary(progress: DailyProgress, next: Occurrence?) {
    val fraction by animateFloatAsState(progress.fraction, animationSpec = tween(MediMotion.progressMillis), label = "progress")
    val done = progress.total > 0 && progress.remaining == 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.regular), modifier = Modifier.padding(vertical = MediSpace.tiny)) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(52.dp), strokeWidth = 6.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, gapSize = 0.dp)
            if (done) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (done) stringResource(R.string.all_taken_today) else stringResource(R.string.completed_fraction, progress.completed, progress.total), style = MaterialTheme.typography.titleMedium)
            next?.let { Text(stringResource(R.string.today_summary_next, it.time.toString(), it.schedule.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

enum class DoseStatus { NEXT, PENDING, OVERDUE, TAKEN, MISSED }

/**
 * Timeline entry: a status rail on the left and a tonal row with time, medication, dose, status
 * and its action. Every pending dose keeps its own İçildi button; only the highlighted one is filled.
 */
@Composable
fun ScheduledDoseRow(item: Occurrence, editable: Boolean = false, busy: Boolean = false, status: DoseStatus = defaultStatus(item, editable), highlight: Boolean = status == DoseStatus.NEXT, last: Boolean = true, onTaken: () -> Unit = {}, onUndo: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val completed = item.takenAt != null
    val background by animateColorAsState(if (highlight && !completed) scheme.primaryContainer else scheme.surfaceContainerLow, animationSpec = tween(MediMotion.feedbackMillis), label = "doseStatus")
    val foreground = if (highlight && !completed) scheme.onPrimaryContainer else scheme.onSurface
    val lineColor = scheme.outlineVariant
    // The connector to the next entry is drawn (not measured), so rows keep their natural height; it spans the
    // list spacing below the row. Decorative only: the status is also written as text in the row.
    Row(Modifier.fillMaxWidth().drawBehind {
        if (!last) {
            val x = 14.dp.toPx()
            drawLine(lineColor, Offset(x, (MediSpace.page + 28.dp).toPx()), Offset(x, size.height + MediSpace.medium.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        Box(Modifier.width(28.dp).padding(top = MediSpace.page), contentAlignment = Alignment.TopCenter) {
            val (icon, tint) = when (status) {
                DoseStatus.TAKEN -> Icons.Default.CheckCircle to scheme.primary
                DoseStatus.OVERDUE -> Icons.Default.Error to scheme.tertiary
                DoseStatus.NEXT -> Icons.Default.RadioButtonChecked to scheme.primary
                else -> Icons.Outlined.Circle to scheme.outline
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Box(Modifier.weight(1f)) {
            Surface(shape = MaterialTheme.shapes.large, color = background, contentColor = foreground, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(MediSpace.regular), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                    AdaptivePair(
                        leading = { Text(item.time.toString(), style = MaterialTheme.typography.headlineSmall) },
                        trailing = { DoseStatusLabel(status, item) }
                    )
                    Text(item.schedule.name, style = MaterialTheme.typography.titleLarge)
                    Text(item.schedule.dose, style = MaterialTheme.typography.bodyLarge, color = if (highlight && !completed) foreground else scheme.onSurfaceVariant)
                    if (item.schedule.note.isNotBlank() && !completed) Text(item.schedule.note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    if (editable) {
                        val description = stringResource(if (completed) R.string.undo_action_description else R.string.dose_action_description, item.personName, item.schedule.name, item.time.toString())
                        val modifier = Modifier.padding(top = MediSpace.small).fillMaxWidth().heightIn(min = MediSize.primaryAction).semantics { contentDescription = description }
                        when {
                            completed -> TextButton(onClick = onUndo, enabled = !busy, shape = MaterialTheme.shapes.large, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = description }) {
                                Icon(Icons.AutoMirrored.Filled.Undo, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.undo), style = MaterialTheme.typography.labelLarge)
                            }
                            highlight -> Button(shape = MaterialTheme.shapes.large, onClick = onTaken, enabled = !busy, modifier = modifier) {
                                Icon(Icons.Default.Check, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.taken), style = MaterialTheme.typography.labelLarge)
                            }
                            else -> FilledTonalButton(shape = MaterialTheme.shapes.large, onClick = onTaken, enabled = !busy, modifier = modifier) {
                                Icon(Icons.Default.Check, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.taken), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
fun defaultStatus(item: Occurrence, editable: Boolean) = when {
    item.takenAt != null -> DoseStatus.TAKEN
    editable -> DoseStatus.PENDING
    else -> DoseStatus.MISSED
}
@Composable private fun DoseStatusLabel(status: DoseStatus, item: Occurrence) {
    when (status) {
        DoseStatus.TAKEN -> StatusChip(stringResource(R.string.taken_status, TurkishFormat.completed(item.takenAt!!, item.date)), completed = true, showIcon = false)
        DoseStatus.NEXT -> StatusChip(stringResource(R.string.next_label), icon = Icons.Default.NotificationsActive)
        DoseStatus.OVERDUE -> Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
            Row(Modifier.padding(horizontal = MediSpace.medium, vertical = MediSpace.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                Icon(Icons.Default.Alarm, null, Modifier.size(18.dp)); Text(stringResource(R.string.overdue), style = MaterialTheme.typography.labelLarge)
            }
        }
        DoseStatus.PENDING -> StatusChip(stringResource(R.string.pending))
        DoseStatus.MISSED -> StatusChip(stringResource(R.string.not_marked), icon = Icons.Outlined.Circle)
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
