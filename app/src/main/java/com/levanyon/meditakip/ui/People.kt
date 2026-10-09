package com.levanyon.meditakip.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import java.time.LocalDate

@Composable
fun PeopleScreen(snapshot: Snapshot, open: (String) -> Unit, add: () -> Unit) =
    PeopleScreen(snapshot, open, LocalDate.now(), add)

/** Read-only projections of the same schedules and handled doses used by Today. */
@Composable
fun PeopleScreen(snapshot: Snapshot, open: (String) -> Unit, today: LocalDate, add: () -> Unit) {
    val people = snapshot.people.filter { !it.archived }
    val archived = snapshot.people.filter { it.archived }
    val current = remember(snapshot) { MedicationViews.current(snapshot) }
    val doses = remember(snapshot, today) { Schedule.day(snapshot, today).groupBy { it.personId } }
    val owners = remember(snapshot) { snapshot.medications.associate { it.id to it.personId } }
    LazyColumn(contentPadding = PeoplePadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { Box(Modifier.padding(bottom = MediSpace.small)) { PeopleHeading(people.isNotEmpty(), add) } }
        if (people.isEmpty()) {
            item { PeopleEmptyState(add) }
        } else {
            items(people, key = { it.id }) { person ->
                PersonSummaryCard(person, current.count { owners[it.medicationId] == person.id && it.active },
                    DailyProgress.from(doses[person.id].orEmpty())) { open(person.id) }
            }
        }
        if (archived.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.archived_people), stringResource(R.string.archived_people_body)) }
            items(archived, key = { it.id }) { person -> PersonRow(person, 0) { open(person.id) } }
        }
    }
}

/** Management hub; dose state is a summary only, with no duplicated daily timeline. */
@Composable
fun PersonDetail(
    snapshot: Snapshot, person: PersonEntity, back: () -> Unit, edit: () -> Unit,
    addMedication: () -> Unit, openMedication: (String) -> Unit, report: (() -> Unit)? = null,
    today: LocalDate = LocalDate.now(), scan: (() -> Unit)? = null, restore: () -> Unit = {}
) {
    val owned = snapshot.medications.filter { it.personId == person.id }
    val ownedIds = owned.filter { !it.archived }.map { it.id }.toSet()
    val medications = remember(snapshot, person.id) { MedicationViews.current(snapshot).filter { it.medicationId in ownedIds } }
    val active = medications.filter { it.active }
    val inactive = medications.filter { !it.active }
    val archived = owned.filter { it.archived }.mapNotNull { MedicationViews.latestRevision(snapshot, it.id) }
        .sortedBy { it.name.lowercase(TurkishFormat.locale) }
    val progress = remember(snapshot, today, person.id) { DailyProgress.from(Schedule.day(snapshot, today).filter { it.personId == person.id }) }
    val lowStock = remember(snapshot, person.id) { StockPlan.low(snapshot, person.id).map { it.medicationId }.toSet() }
    LazyColumn(contentPadding = PeoplePadding, verticalArrangement = Arrangement.spacedBy(MediSpace.page)) {
        item { PersonHeader(person, active.size, back) }
        if (person.note.isNotBlank()) item {
            Text(person.note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (person.archived) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                    StatusChip(stringResource(R.string.archived), icon = Icons.Default.Archive)
                    Text(stringResource(R.string.archived_person_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryAction(stringResource(R.string.restore), Icons.Default.Unarchive, action = restore)
                }
            }
        } else {
            // No fake 0/0 progress: an empty person follows the approved empty composition.
            if (progress.total > 0 || medications.isNotEmpty()) item { PersonTodaySummary(progress) }
            item { PersonMedicationActions(addMedication, scan) }
            if (medications.isEmpty()) item { PersonMedicationsEmptyState() }
            if (active.isNotEmpty()) item { PersonMedicationGroup(active, lowStock, openMedication) }
            // Existing stopped medications remain reachable, without calling them active.
            if (inactive.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.person_inactive_medications)) }
                items(inactive, key = { it.id }) { schedule -> MedicationRow(schedule) { openMedication(schedule.medicationId) } }
            }
            item { PersonEditAction(person.name, edit) }
        }
        if (report != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
                SectionHeader(stringResource(R.string.report_title))
                Text(stringResource(R.string.report_help), style = MaterialTheme.typography.bodyMedium)
                SecondaryAction(stringResource(R.string.report_create), action = report)
            }
        }
        if (archived.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.archived_medications)) }
            items(archived, key = { "archived:${it.id}" }) { schedule -> MedicationRow(schedule, archived = true) { openMedication(schedule.medicationId) } }
        }
    }
}
