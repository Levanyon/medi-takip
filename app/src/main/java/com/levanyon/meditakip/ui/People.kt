package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*

/** Kişiler: every family member as one clear row, with an always-visible Kişi Ekle. */
@Composable
fun PeopleScreen(snapshot: Snapshot, open: (String) -> Unit, add: () -> Unit) {
    val people = snapshot.people.filter { !it.archived }
    val current = MedicationViews.current(snapshot)
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { ScreenHeading(stringResource(R.string.people), stringResource(R.string.people_subtitle)) }
        if (people.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_people_title), stringResource(R.string.empty_people_body), Icons.Default.People, stringResource(R.string.add_person), add) }
            return@LazyColumn
        }
        item { Box(Modifier.padding(bottom = MediSpace.small)) { PrimaryAction(stringResource(R.string.add_person), Icons.Default.PersonAdd, action = add) } }
        items(people, key = { it.id }) { person ->
            val ids = snapshot.medications.filter { it.personId == person.id }.map { it.id }.toSet()
            PersonRow(person, current.count { it.medicationId in ids && it.active }) { open(person.id) }
        }
    }
}

/** Kişi Detay is management only: identity, Düzenle, and the person's medications. No daily timeline. */
@Composable
fun PersonDetail(snapshot: Snapshot, person: PersonEntity, back: () -> Unit, edit: () -> Unit, addMedication: () -> Unit, openMedication: (String) -> Unit) {
    val ids = snapshot.medications.filter { it.personId == person.id && !it.archived }.map { it.id }.toSet()
    val medications = MedicationViews.current(snapshot).filter { it.medicationId in ids }
    val editDescription = stringResource(R.string.edit_person_description, person.name)
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { BackHeader(stringResource(R.string.people), back) }
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                AdaptivePair(
                    leading = {
                        Row(horizontalArrangement = Arrangement.spacedBy(MediSpace.regular), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Avatar(person.name)
                            Text(person.name, style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    trailing = {
                        FilledTonalButton(onClick = edit, shape = MaterialTheme.shapes.large, modifier = Modifier.heightIn(min = MediSize.touch).semantics { contentDescription = editDescription }) {
                            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(MediSpace.small)); Text(stringResource(R.string.edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
                if (person.note.isNotBlank()) Text(person.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SectionHeader(stringResource(R.string.medications), stringResource(R.string.active_count, medications.count { it.active })) }
        if (medications.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_medications_title), stringResource(R.string.empty_person_medications_body, person.name), Icons.Default.Medication, stringResource(R.string.add_medication), addMedication) }
        } else {
            item { PrimaryAction(stringResource(R.string.add_medication), action = addMedication) }
            items(medications, key = { it.id }) { schedule -> MedicationRow(schedule) { openMedication(schedule.medicationId) } }
        }
    }
}
