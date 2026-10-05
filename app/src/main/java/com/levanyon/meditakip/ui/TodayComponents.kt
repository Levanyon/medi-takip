package com.levanyon.meditakip.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import com.levanyon.meditakip.R
import com.levanyon.meditakip.domain.Occurrence

/** Today opts into a quieter selector; History retains its existing presentation. */
@Composable
internal fun TodayPersonSelector(people: List<PersonTab>, selected: String?, onSelect: (String) -> Unit, showCounts: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            people.forEach { person ->
                val active = person.id == selected
                val description = when {
                    person.archived -> stringResource(R.string.person_archived_description, person.name)
                    !showCounts -> person.name
                    person.remaining > 0 -> stringResource(R.string.person_pending_description, person.name, person.remaining)
                    else -> stringResource(R.string.person_done_description, person.name)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = MediSize.touch, max = availableWidth).heightIn(min = MediSize.touch)
                        .selectable(active, role = Role.Tab) { onSelect(person.id) }
                        .semantics(mergeDescendants = true) { contentDescription = description }
                ) {
                    Row(Modifier.padding(horizontal = MediSpace.regular, vertical = MediSpace.medium),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                        if (active) Icon(Icons.Default.Check, null, Modifier.size(MediSize.statusIcon))
                        Text(person.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
                        if (showCounts && person.remaining > 0) Text(person.remaining.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** One focal surface for the existing earliest-unhandled dose, including overdue doses. */
@Composable
internal fun NextDoseHero(item: Occurrence, status: DoseStatus, actions: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = mediSurfaceBorder(), tonalElevation = MediElevation.flat, shadowElevation = MediElevation.card) {
        Column(Modifier.padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            AdaptivePair(
                leading = { Text(stringResource(R.string.next_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() }) },
                trailing = {
                    if (status == DoseStatus.OVERDUE) Text(stringResource(R.string.overdue), style = MaterialTheme.typography.labelMedium)
                }
            )
            Text(item.time.toString(), style = MaterialTheme.typography.headlineLarge.copy(
                fontFeatureSettings = MaterialTheme.typography.headlineSmall.fontFeatureSettings))
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                Text(item.schedule.name, style = MaterialTheme.typography.titleLarge)
                Text(item.schedule.dose, style = MaterialTheme.typography.bodyLarge)
                if (item.schedule.note.isNotBlank()) Text(item.schedule.note, style = MaterialTheme.typography.bodyMedium)
            }
            actions()
        }
    }
}

/** A compact, actionable alert slot. Callers supply only existing reminder/stock information. */
@Composable
internal fun TodayAlert(text: String, icon: ImageVector, actionLabel: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = MediSize.touch)) {
        Row(Modifier.padding(MediSpace.medium), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
            Icon(icon, null, Modifier.size(MediSize.statusIcon), tint = MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
                Text(text, style = MaterialTheme.typography.bodySmall)
                actionLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(MediSize.statusIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
