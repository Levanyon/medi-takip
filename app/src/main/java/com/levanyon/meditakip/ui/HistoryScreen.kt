package com.levanyon.meditakip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.LogStatus
import com.levanyon.meditakip.domain.*
import java.time.LocalDate

/** UI-only filters. Null means all dates/statuses; no records or preferences are written. */
internal data class HistoryFilter(val days: Int? = 7, val status: LogStatus? = null)

internal fun historyRecords(snapshot: Snapshot, today: LocalDate, personId: String?, filter: HistoryFilter): List<Occurrence> {
    val from = filter.days?.let { today.minusDays(it.toLong() - 1) }
    // Resolve logged dates through the existing schedule domain, including archived revisions.
    return snapshot.logs.asSequence().map { it.date }.distinct().sortedDescending()
        .map(LocalDate::ofEpochDay).filter { it <= today && (from == null || it >= from) }
        .flatMap { Schedule.day(snapshot, it).asSequence() }
        .filter { it.handled && (personId == null || it.personId == personId) && (filter.status == null || it.status == filter.status) }
        .sortedWith(compareByDescending<Occurrence> { it.date }.thenBy { it.minute }.thenBy { it.personName }.thenBy { it.key }).toList()
}

internal fun historyProgress(snapshot: Snapshot, today: LocalDate, personId: String?): DailyProgress = DailyProgress.from(
    (0L..6L).flatMap { Schedule.day(snapshot, today.minusDays(it)) }.filter { personId == null || it.personId == personId }
)

@Composable
fun HistoryScreen(snapshot: Snapshot, today: LocalDate, selectedPersonId: String?, selectPerson: (String) -> Unit,
    goToPeople: () -> Unit, corrections: DoseCorrections? = null) {
    var personId by rememberSaveable { mutableStateOf(selectedPersonId) }
    val selected = personId?.takeIf { id -> snapshot.people.any { it.id == id } }
    var days by rememberSaveable { mutableStateOf<Int?>(7) }
    var status by rememberSaveable { mutableStateOf<LogStatus?>(null) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var planned by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Occurrence?>(null) }
    val filter = HistoryFilter(days, status)
    val records = remember(snapshot, today, selected, filter) { historyRecords(snapshot, today, selected, filter) }
    val progress = remember(snapshot, today, selected) { historyProgress(snapshot, today, selected) }
    if (planned) {
        BackHandler { planned = false }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = MediSpace.page, vertical = MediSpace.small)) {
                BackHeader(stringResource(R.string.history_planned)) { planned = false }
            }
            Box(Modifier.weight(1f)) { HistoryScheduleScreen(snapshot, today, selected, { personId = it; selectPerson(it) }, goToPeople, corrections) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = Phase5Padding, verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
            item { Phase5Heading(stringResource(R.string.history), stringResource(R.string.history_v2_subtitle)) {
                HistoryFilterAction { filtering = true }
            } }
            item { HistoryPersonFilter(snapshot.people.sortedBy { it.archived }, selected) {
                personId = it
                if (it != null) selectPerson(it)
            } }
            if (records.isNotEmpty()) {
                item { HistorySummary(progress) }
                records.groupBy { it.date }.forEach { (date, doses) ->
                    item(key = date.toString()) { HistoryDateGroup(date, today, doses) { opened = it } }
                }
            } else item {
                HistoryEmpty(filtered = status != null || snapshot.logs.any { log ->
                    snapshot.schedules.any { schedule -> schedule.id == log.scheduleId &&
                        snapshot.medications.any { it.id == schedule.medicationId && (selected == null || it.personId == selected) } }
                })
            }
            if (snapshot.schedules.isNotEmpty()) item { QuietAction(stringResource(R.string.history_planned)) { planned = true } }
            if (snapshot.people.isEmpty()) item { QuietAction(stringResource(R.string.go_to_people), action = goToPeople) }
        }
        if (filtering) HistoryFilterSheet(filter, { filtering = false }) { days = it.days; status = it.status; filtering = false }
        opened?.let { HistoryCellDetail(it, { opened = null }, today, corrections) }
    }
}
