package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*

/** Kişiler: every family member as one clear row, an always-visible Kişi Ekle, archived people last. */
@Composable
fun PeopleScreen(snapshot: Snapshot, open: (String) -> Unit, add: () -> Unit) {
    val people = snapshot.people.filter { !it.archived }
    val archived = snapshot.people.filter { it.archived }
    val current = MedicationViews.current(snapshot)
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { ScreenHeading(stringResource(R.string.people), stringResource(R.string.people_subtitle)) }
        if (people.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_people_title), stringResource(R.string.empty_people_body), Icons.Default.People, stringResource(R.string.add_person), add) }
        } else {
            item { Box(Modifier.padding(bottom = MediSpace.small)) { PrimaryAction(stringResource(R.string.add_person), Icons.Default.PersonAdd, action = add) } }
            items(people, key = { it.id }) { person ->
                val ids = snapshot.medications.filter { it.personId == person.id }.map { it.id }.toSet()
                PersonRow(person, current.count { it.medicationId in ids && it.active }) { open(person.id) }
            }
        }
        if (archived.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.archived_people), stringResource(R.string.archived_people_body)) }
            items(archived, key = { it.id }) { person -> PersonRow(person, 0) { open(person.id) } }
        }
    }
}

/**
 * Kişi Detay is management only: identity, Düzenle, the person's medications and, separately, archived
 * medications. No daily timeline. An archived person shows only "Arşivden Çıkar" as the action.
 */
@Composable
fun PersonDetail(snapshot: Snapshot, person: PersonEntity, back: () -> Unit, edit: () -> Unit, addMedication: () -> Unit, openMedication: (String) -> Unit, report: (() -> Unit)? = null, restore: () -> Unit = {}) {
    val owned = snapshot.medications.filter { it.personId == person.id }
    val activeIds = owned.filter { !it.archived }.map { it.id }.toSet()
    val medications = MedicationViews.current(snapshot).filter { it.medicationId in activeIds }
    val archivedMedications = owned.filter { it.archived }.mapNotNull { MedicationViews.latestRevision(snapshot, it.id) }
        .sortedBy { it.name.lowercase(TurkishFormat.locale) }
    val editDescription = stringResource(R.string.edit_person_description, person.name)
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { BackHeader(stringResource(R.string.people), back) }
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                AdaptivePair(
                    leading = {
                        Row(horizontalArrangement = Arrangement.spacedBy(MediSpace.regular), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person.name)
                            Text(person.name, style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    trailing = {
                        if (person.archived) StatusChip(stringResource(R.string.archived), icon = Icons.Default.Archive)
                        else FilledTonalButton(onClick = edit, shape = MaterialTheme.shapes.large, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = editDescription }) {
                            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
                if (person.note.isNotBlank()) Text(person.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (person.archived) {
                    Text(stringResource(R.string.archived_person_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction(stringResource(R.string.restore), Icons.Default.Unarchive, action = restore)
                }
            }
        }
        if (!person.archived) {
            item { SectionHeader(stringResource(R.string.medications), stringResource(R.string.active_count, medications.count { it.active })) }
            if (medications.isEmpty()) {
                item { EmptyState(stringResource(R.string.empty_medications_title), stringResource(R.string.empty_person_medications_body, person.name), Icons.Default.Medication, stringResource(R.string.add_medication), addMedication) }
            } else {
                item { PrimaryAction(stringResource(R.string.add_medication), action = addMedication) }
                items(medications, key = { it.id }) { schedule -> MedicationRow(schedule) { openMedication(schedule.medicationId) } }
            }
        }
        if (report != null) item {
            SectionHeader(stringResource(R.string.report_title))
            Text(stringResource(R.string.report_help), style = MaterialTheme.typography.bodyMedium)
            SecondaryAction(stringResource(R.string.report_create), action = report)
        }
        if (archivedMedications.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.archived_medications)) }
            items(archivedMedications, key = { "archived:${it.id}" }) { schedule -> MedicationRow(schedule, archived = true) { openMedication(schedule.medicationId) } }
        }
    }
}
