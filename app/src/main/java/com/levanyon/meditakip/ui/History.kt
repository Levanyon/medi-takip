package com.levanyon.meditakip.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.domain.*
import java.time.LocalDate

private const val DAYS_PER_PAGE = 14

/** Geçmiş: pick a person, then read many days at a glance in one date × medication matrix. */
@Composable fun HistoryScreen(snapshot: Snapshot, today: LocalDate, selectedPersonId: String?, selectPerson: (String) -> Unit, goToPeople: () -> Unit) {
    // Archived people stay selectable (after the active ones) so their history is never hidden.
    val people = snapshot.people.filter { !it.archived } + snapshot.people.filter { it.archived }
    val person = people.find { it.id == selectedPersonId } ?: people.firstOrNull()
    var daysShown by rememberSaveable(person?.id) { mutableIntStateOf(DAYS_PER_PAGE) }
    var opened by remember { mutableStateOf<Occurrence?>(null) }
    // A person whose medications have all ended/been archived opens at their last records, not at today.
    val through = remember(snapshot, person?.id, today) { person?.let { HistoryMatrices.lastDay(snapshot, it.id) }?.let { minOf(it, today) } ?: today }
    val from = through.minusDays(daysShown.toLong() - 1)
    val matrix = remember(snapshot, person?.id, from, through) { person?.let { HistoryMatrices.build(snapshot, it.id, from, through) } }
    val earliest = remember(snapshot, person?.id) { person?.let { HistoryMatrices.earliest(snapshot, it.id) } }
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxWidth - MediSpace.page * 2
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            item { ScreenHeading(stringResource(R.string.history), stringResource(R.string.history_subtitle)) }
            if (person == null) {
                item { EmptyState(stringResource(R.string.empty_people_title), stringResource(R.string.history_no_person_body), Icons.Default.People, stringResource(R.string.go_to_people), goToPeople) }
                return@LazyColumn
            }
            item { Box(Modifier.padding(bottom = MediSpace.small)) { PersonSelector(people.map { PersonTab(it.id, it.name, archived = it.archived) }, person.id, selectPerson, showCounts = false) } }
            if (matrix == null || matrix.rows.isEmpty()) {
                item { EmptyState(stringResource(R.string.empty_history_title), stringResource(R.string.empty_history_body), Icons.Default.History) }
                return@LazyColumn
            }
            historyTable(matrix, today, scroll, available, sticky = true) { opened = it }
            if (earliest != null && from > earliest) item { Box(Modifier.padding(top = MediSpace.medium)) { SecondaryAction(stringResource(R.string.older_records)) { daysShown += DAYS_PER_PAGE } } }
        }
    }
    opened?.let { HistoryCellDetail(it) { opened = null } }
}

/** Column sizes grow with the user's font scale (capped) so cells never become unreadably narrow. */
private data class TableMetrics(val date: Dp, val cell: Dp)
@Composable private fun tableMetrics(): TableMetrics {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    return TableMetrics(date = 76.dp * scale, cell = 92.dp * scale)
}

/**
 * Emits the history matrix as lazy rows. The date column is outside the horizontal scroll, so it
 * stays visible; every row's medication cells share one [ScrollState], so the columns scroll together.
 * When the table is narrower than the screen it keeps its natural width and is centered.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.historyTable(matrix: HistoryMatrix, today: LocalDate, scroll: ScrollState, available: Dp, sticky: Boolean, open: (Occurrence) -> Unit) {
    item(key = "legend") { HistoryLegend(matrix, available) }
    if (sticky) stickyHeader(key = "header") { HistoryHeader(matrix, scroll) } else item(key = "header") { HistoryHeader(matrix, scroll) }
    itemsIndexed(matrix.rows, key = { _, row -> "row:${row.date}" }) { index, row ->
        HistoryTableRow(matrix, row, today, scroll, shaded = index % 2 == 0, open = open)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun HistoryLegend(matrix: HistoryMatrix, available: Dp) {
    val metrics = tableMetrics()
    val overflows = metrics.date + metrics.cell * matrix.columns.size > available
    Column(Modifier.fillMaxWidth().padding(bottom = MediSpace.small), verticalArrangement = Arrangement.spacedBy(MediSpace.small)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MediSpace.regular), verticalArrangement = Arrangement.spacedBy(MediSpace.tiny)) {
            LegendItem({ Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }, stringResource(R.string.legend_taken))
            LegendItem({ Icon(Icons.Outlined.Circle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline) }, stringResource(R.string.legend_open))
            LegendItem({ Icon(Icons.Default.DoNotDisturbOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }, stringResource(R.string.legend_skipped))
            LegendItem({ Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant) }, stringResource(R.string.legend_none))
        }
        if (overflows) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.small)) {
            Icon(Icons.Default.SwipeLeft, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.history_hint_scroll), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun LegendItem(icon: @Composable () -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.tiny)) { icon(); Text(label, style = MaterialTheme.typography.bodyMedium) }
}

@Composable private fun HistoryHeader(matrix: HistoryMatrix, scroll: ScrollState) {
    val metrics = tableMetrics()
    // Opaque background so rows scrolling beneath the sticky header never show through.
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = MediSpace.tiny), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).height(IntrinsicSize.Min)) {
            Box(Modifier.width(metrics.date).fillMaxHeight().padding(MediSpace.small), contentAlignment = Alignment.BottomStart) {
                Text(stringResource(R.string.history_date_header), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            }
            Row(Modifier.weight(1f, fill = false).horizontalScroll(scroll)) {
                matrix.columns.forEach { column ->
                    Column(Modifier.width(metrics.cell).padding(horizontal = MediSpace.small, vertical = MediSpace.small), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(column.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(column.time.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable private fun HistoryTableRow(matrix: HistoryMatrix, row: HistoryRow, today: LocalDate, scroll: ScrollState, shaded: Boolean, open: (Occurrence) -> Unit) {
    val metrics = tableMetrics()
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.clip(MaterialTheme.shapes.medium).background(if (shaded) scheme.surfaceContainerLow else scheme.background).height(IntrinsicSize.Min)) {
            Column(Modifier.width(metrics.date).fillMaxHeight().padding(horizontal = MediSpace.small, vertical = MediSpace.small), verticalArrangement = Arrangement.Center) {
                Text(TurkishFormat.shortDate(row.date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(if (row.date == today) stringResource(R.string.today) else TurkishFormat.weekday(row.date), style = MaterialTheme.typography.bodySmall,
                    color = if (row.date == today) scheme.primary else scheme.onSurfaceVariant)
            }
            Row(Modifier.weight(1f, fill = false).horizontalScroll(scroll)) {
                matrix.columns.forEach { column -> HistoryCell(column, row, metrics.cell, open) }
            }
        }
    }
}

@Composable private fun HistoryCell(column: HistoryColumn, row: HistoryRow, width: Dp, open: (Occurrence) -> Unit) {
    val occurrence = row.cells[column.key]
    val scheme = MaterialTheme.colorScheme
    val date = TurkishFormat.date(row.date)
    val description = when {
        occurrence == null -> stringResource(R.string.history_cell_none, column.name, column.time.toString(), date)
        occurrence.takenAt != null -> stringResource(R.string.history_cell_taken, occurrence.schedule.name, column.time.toString(), date, TurkishFormat.completed(occurrence.takenAt, occurrence.date))
        occurrence.skipped -> stringResource(R.string.history_cell_skipped, occurrence.schedule.name, column.time.toString(), date)
        else -> stringResource(R.string.history_cell_open, occurrence.schedule.name, column.time.toString(), date)
    }
    val base = Modifier.width(width).heightIn(min = MediSize.primaryAction).fillMaxHeight()
    val modifier = if (occurrence != null) base.clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { open(occurrence) } else base
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }.padding(vertical = MediSpace.small),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when {
            occurrence == null -> Text("–", style = MaterialTheme.typography.titleMedium, color = scheme.outlineVariant)
            occurrence.takenAt != null -> {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = scheme.primary)
                Text(TurkishFormat.time(occurrence.takenAt), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            }
            occurrence.skipped -> {
                Icon(Icons.Default.DoNotDisturbOn, null, Modifier.size(22.dp), tint = scheme.onSurfaceVariant)
                Text(stringResource(R.string.skipped), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
            else -> Icon(Icons.Outlined.Circle, null, Modifier.size(22.dp), tint = scheme.outline)
        }
    }
}

/**
 * Details of one dose. Name and dose come from the occurrence's own schedule revision — the one
 * valid on that date — never from the column label (which shows the latest revision).
 */
@Composable fun HistoryCellDetail(occurrence: Occurrence, dismiss: () -> Unit) {
    MediDialog(occurrence.schedule.name, dismiss) {
        Text(occurrence.schedule.dose, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabelValue(stringResource(R.string.detail_date), TurkishFormat.longWeekdayDate(occurrence.date))
        LabelValue(stringResource(R.string.detail_planned), occurrence.time.toString())
        LabelValue(stringResource(R.string.detail_taken), when {
            occurrence.takenAt != null -> TurkishFormat.completed(occurrence.takenAt, occurrence.date)
            occurrence.skipped -> stringResource(R.string.skipped) + (occurrence.note?.let { " · $it" } ?: "")
            else -> stringResource(R.string.not_marked)
        })
        SecondaryAction(stringResource(R.string.ok), action = dismiss)
    }
}
