package com.levanyon.meditakip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.PersonEntity
import com.levanyon.meditakip.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val TODAY = 0
private const val PEOPLE = 1
private const val HISTORY = 2
private const val SETTINGS = 3

/**
 * Four destinations: Bugün (daily use), Kişiler (people → medications), Geçmiş (history table), Ayarlar.
 * Add/edit forms are drawn over the scaffold in the same activity window so window insets
 * (navigation bar, IME) are always delivered; the scaffold is hidden from accessibility meanwhile.
 */
@Composable fun Home(snapshot: Snapshot, vm: AppViewModel) {
    var section by rememberSaveable { mutableIntStateOf(TODAY) }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    var medicationId by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderStatus by rememberSaveable { mutableStateOf(false) }
    var editPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var personEditor by rememberSaveable { mutableStateOf(false) }
    var choosePerson by rememberSaveable { mutableStateOf(false) }
    var continueToMedication by rememberSaveable { mutableStateOf(false) }
    var medicationPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var editScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var archivePerson by rememberSaveable { mutableStateOf(false) }
    var archiveMedication by rememberSaveable { mutableStateOf(false) }
    // Minute resolution: recomposes only when the clock minute (or date) changes.
    var now by remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    val selectedPerson by vm.selectedPerson.collectAsStateWithLifecycle()
    val setupPending by vm.setupPending.collectAsStateWithLifecycle()
    val access = rememberPermissionAccess(vm)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES); delay(1000) } }
    val today = now.toLocalDate()
    val formOpen = personEditor || medicationPersonId != null
    fun back() { when { medicationId != null -> medicationId = null; personId != null -> personId = null; reminderStatus -> reminderStatus = false; else -> section = TODAY } }
    BackHandler(!formOpen && (medicationId != null || personId != null || reminderStatus || section != TODAY)) { back() }
    val health = rememberReminderHealth(settings.notifications)
    fun addPerson(thenMedication: Boolean) { editPersonId = null; personEditor = true; continueToMedication = thenMedication }
    fun addMedication(forPerson: String?) {
        editScheduleId = null
        val people = snapshot.people.filter { !it.archived }
        when {
            forPerson != null -> medicationPersonId = forPerson
            people.isEmpty() -> addPerson(true)
            people.size == 1 -> medicationPersonId = people.single().id
            else -> choosePerson = true
        }
    }
    fun openPerson(id: String) { section = PEOPLE; personId = id; medicationId = null }
    fun inform(message: String) {
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long) }
    }
    // First-run setup (onboarding "İlk kişiyi ekle"): person form, then medication form, then a pointer to
    // Hatırlatma Durumu. Closing either form ends the guided setup.
    LaunchedEffect(setupPending) { if (setupPending && snapshot.people.none { !it.archived } && !personEditor) addPerson(true) }
    fun finishFirstSetup() {
        vm.finishSetup()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(context.getString(R.string.setup_done), context.getString(R.string.setup_check), withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                section = SETTINGS; reminderStatus = true; personId = null; medicationId = null
            }
        }
    }
    // Restoring a person leaves their medications archived; say so, so they do not seem lost.
    fun restorePerson(person: PersonEntity) {
        val archivedMedications = snapshot.medications.count { it.personId == person.id && it.archived }
        vm.restorePerson(person.id) {
            inform(if (archivedMedications > 0) context.getString(R.string.person_restored_with_medications, person.name, archivedMedications) else context.getString(R.string.person_restored, person.name))
        }
    }
    fun restoreMedication(id: String) {
        val name = MedicationViews.latestRevision(snapshot, id)?.name.orEmpty()
        vm.restoreMedication(id) { inform(context.getString(R.string.medication_restored, name)) }
    }
    fun markTaken(item: Occurrence) {
        vm.taken(item)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(context.getString(R.string.snackbar_taken, item.schedule.name), context.getString(R.string.undo), duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.undo(item)
        }
    }
    val labels = listOf(R.string.today, R.string.people, R.string.history, R.string.settings)
    val icons = listOf(Icons.Outlined.Today, Icons.Outlined.People, Icons.Outlined.CalendarMonth, Icons.Outlined.Settings)
    val selectedIcons = listOf(Icons.Filled.Today, Icons.Filled.People, Icons.Filled.CalendarMonth, Icons.Filled.Settings)
    Scaffold(
        modifier = if (formOpen) Modifier.clearAndSetSemantics {} else Modifier,
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            MediNavigationBar(labels.map { stringResource(it) }, icons, section, selectedIcons) { index ->
                section = index; personId = null; medicationId = null; reminderStatus = false
            }
        }) { padding ->
        MediContent(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                medicationId != null -> MedicationDetail(snapshot, medicationId!!, today, ::back, { p, s -> medicationPersonId = p.id; editScheduleId = s.id }, { archiveMedication = true }, { restoreMedication(medicationId!!) })
                section == PEOPLE && personId != null -> snapshot.people.find { it.id == personId }?.let { person ->
                    PersonDetail(snapshot, person, ::back, { editPersonId = person.id; personEditor = true }, { addMedication(person.id) }, { medicationId = it }) { restorePerson(person) }
                } ?: LaunchedEffect(personId) { personId = null }
                section == PEOPLE -> PeopleScreen(snapshot, { personId = it }) { addPerson(false) }
                section == HISTORY -> HistoryScreen(snapshot, today, selectedPerson, vm::selectPerson) { section = PEOPLE }
                section == SETTINGS && reminderStatus -> ReminderStatusScreen(vm, access, ::back)
                section == SETTINGS -> SettingsScreen(vm) { reminderStatus = true }
                else -> TodayScreen(snapshot, now, selectedPerson, vm::selectPerson, vm, ::markTaken,
                    addPerson = { addPerson(true) }, addMedication = { addMedication(it) }, manage = ::openPerson,
                    warning = if (health.working) null else R.string.reminders_need_attention,
                    openSettings = { section = SETTINGS; reminderStatus = true })
            }
        }
    }
    if (personEditor) {
        val editing = snapshot.people.find { it.id == editPersonId }
        PersonEditor(editing, { personEditor = false; continueToMedication = false; if (setupPending) vm.finishSetup() }, archive = editing?.let { { archivePerson = true } }) { person ->
            vm.person(person) {
                personEditor = false
                if (editing == null) vm.selectPerson(person.id)
                if (continueToMedication) { medicationPersonId = person.id; editScheduleId = null; continueToMedication = false }
            }
        }
    }
    medicationPersonId?.let { id -> snapshot.people.find { it.id == id }?.let { person ->
        key(id, editScheduleId) { MedicationEditor(person, snapshot.schedules.find { it.id == editScheduleId }, { medicationPersonId = null; if (setupPending) vm.finishSetup() }) { schedule ->
            vm.medication(person, snapshot.schedules.find { it.id == editScheduleId }?.medicationId, schedule) { medicationPersonId = null; if (setupPending) finishFirstSetup() }
        } }
    } }
    if (choosePerson) MediDialog(stringResource(R.string.choose_person), { choosePerson = false }) {
        snapshot.people.filter { !it.archived }.forEach { person ->
            SecondaryAction(person.name) { medicationPersonId = person.id; choosePerson = false }
        }
        CancelAction { choosePerson = false }
    }
    if (archivePerson) snapshot.people.find { it.id == editPersonId }?.let { person -> ArchiveConfirmation(stringResource(R.string.archive_person_message, person.name), { archivePerson = false }) {
        vm.archivePerson(person) { archivePerson = false; personEditor = false; personId = null; if (selectedPerson == person.id) vm.selectPerson(null) }
    } }
    if (archiveMedication) snapshot.schedules.lastOrNull { it.medicationId == medicationId }?.let { schedule -> ArchiveConfirmation(stringResource(R.string.archive_medication_message, schedule.name), { archiveMedication = false }) { vm.archiveMedication(schedule.medicationId) { archiveMedication = false; medicationId = null } } }
}

/**
 * The daily experience: who → how far along → the timeline with one highlighted dose.
 * Every pending dose keeps its own İçildi action.
 */
@Composable fun TodayScreen(
    snapshot: Snapshot, now: LocalDateTime, selectedPersonId: String?, selectPerson: (String) -> Unit, vm: AppViewModel,
    markTaken: (Occurrence) -> Unit, addPerson: () -> Unit, addMedication: (String?) -> Unit, manage: (String) -> Unit,
    warning: Int? = null, openSettings: () -> Unit = {}
) {
    val today = now.toLocalDate()
    val people = snapshot.people.filter { !it.archived }
    val entries = remember(snapshot, today) { Schedule.day(snapshot, today) }
    val person = people.find { it.id == selectedPersonId } ?: people.firstOrNull()
    val doses = entries.filter { it.personId == person?.id }.sortedWith(compareBy({ it.minute }, { it.schedule.name }))
    val minute = now.hour * 60 + now.minute
    // The single dose that deserves the strongest emphasis: the earliest one not yet taken.
    val focus = doses.firstOrNull { it.takenAt == null }
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
        item { ScreenHeading(stringResource(R.string.today), TurkishFormat.longWeekdayDate(today)) }
        if (warning != null) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = MediSpace.regular, end = MediSpace.tiny, top = MediSpace.small, bottom = MediSpace.small), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                    Icon(Icons.Default.NotificationsOff, null)
                    Text(stringResource(warning), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = openSettings, modifier = Modifier.heightIn(min = MediSize.touch)) { Text(stringResource(R.string.fix), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        if (person == null) {
            item { EmptyState(stringResource(R.string.start_title), stringResource(R.string.start_body), Icons.Default.People, stringResource(R.string.add_person), addPerson) }
            return@LazyColumn
        }
        item {
            val tabs = people.map { p -> PersonTab(p.id, p.name, entries.count { it.personId == p.id && it.takenAt == null }) }
            PersonSelector(tabs, person.id, selectPerson)
        }
        if (doses.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_today_person_title), stringResource(R.string.empty_today_person_body, person.name), Icons.Default.EventAvailable,
                stringResource(R.string.add_medication_for, person.name)) { addMedication(person.id) } }
        } else {
            item { DaySummary(DailyProgress.from(doses), focus) }
            itemsIndexed(doses, key = { _, it -> it.key }) { index, item ->
                val status = when {
                    item.takenAt != null -> DoseStatus.TAKEN
                    item.minute < minute -> DoseStatus.OVERDUE
                    item == focus -> DoseStatus.NEXT
                    else -> DoseStatus.PENDING
                }
                ScheduledDoseRow(item, editable = true, status = status, highlight = item == focus, last = index == doses.lastIndex,
                    onTaken = { markTaken(item) }, onUndo = { vm.undo(item) })
            }
        }
        item { Box(Modifier.padding(top = MediSpace.small)) { QuietAction(stringResource(R.string.edit_medications, person.name), Icons.Default.Edit) { manage(person.id) } } }
    }
}
