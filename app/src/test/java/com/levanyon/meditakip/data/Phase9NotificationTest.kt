package com.levanyon.meditakip.data

import android.app.*
import android.content.Intent
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.MainActivity
import com.levanyon.meditakip.domain.*
import com.levanyon.meditakip.reminders.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class)
class Phase9NotificationTest {
    private lateinit var app: MediApp
    private val date = LocalDate.of(2026, 10, 10)
    private val now = date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
    private val person = PersonEntity("p", "Anne gerçek kişi")
    private val schedule = MedicationScheduleEntity("s", "m", "obsolete", "Gerçek ilaç 50 mg", "1 tablet", "", "12:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val data get() = Snapshot(listOf(person), listOf(MedicationEntity("m", "p")), listOf(schedule))
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private fun current() = manager.activeNotifications.single { it.id == LockScreenNotifications.ID }.notification
    private fun post(snapshot: Snapshot = data) = LockScreenNotifications.refresh(app, snapshot, now)
    private fun text(notification: Notification) = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
        .joinToString { notification.extras.getCharSequence(it).toString() }
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as MediApp
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        app.preferences.save(AppSettings())
        app.preferences.saveLockScreen(LockScreenSettings())
        manager.cancelAll()
    }
    @After fun close() = runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin(); app.database.close() }

    @Test fun defaultsAreVisiblePrivateOngoingAndNoQuickAction() {
        assertEquals(LockScreenSettings(true, false, true, false), Preferences(app).lockScreen.value)
        post(); assertEquals(Notification.VISIBILITY_PRIVATE, current().visibility)
        assertTrue(current().actions.isNullOrEmpty())
    }
    @Test fun allSettingsPersistInExistingPreferenceStore() {
        val expected = LockScreenSettings(false, true, false, true)
        app.preferences.saveLockScreen(expected)
        assertEquals(expected, Preferences(app).lockScreen.value)
        app.preferences.save(AppSettings(theme = "Dark"))
        assertEquals(expected, Preferences(app).lockScreen.value)
    }
    @Test fun privateContainsNeitherMedicationNorPersonNorDose() {
        post()
        for (n in listOf(current(), current().publicVersion)) {
            assertFalse(text(n).contains(schedule.name)); assertFalse(text(n).contains(person.name))
            assertFalse(text(n).contains(schedule.dose)); assertTrue(text(n).contains("12:00"))
        }
    }
    @Test fun detailedContainsRealIdentityAndPublicRemainsRedacted() {
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true)); post()
        assertTrue(text(current()).contains(schedule.name)); assertTrue(text(current()).contains(person.name))
        assertFalse(text(current()).contains("obsolete"))
        assertEquals(Notification.VISIBILITY_PUBLIC, current().visibility)
        assertFalse(text(current().publicVersion).contains(schedule.name)); assertFalse(text(current().publicVersion).contains(person.name))
    }
    @Test fun masterOffCancelsNextDoseStatus() {
        post()
        app.preferences.saveLockScreen(LockScreenSettings(visible = false, detailed = true)); post()
        assertFalse(manager.activeNotifications.any { it.id == LockScreenNotifications.ID })
    }
    @Test fun ongoingIsSilentAndOnlyAlertsOnce() {
        post(); post()
        assertTrue(current().flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(current().flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        val channel = manager.getNotificationChannel(LockScreenNotifications.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound); assertFalse(channel.shouldVibrate())
        assertEquals(1, manager.activeNotifications.size)
    }
    @Test fun nativeCountdownUsesTheScheduledTimestamp() {
        post()
        assertEquals(NextDose.next(data, date)!!.instant(ZoneId.systemDefault()).toEpochMilli(), current().`when`)
        assertTrue(current().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(current().extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }
    @Test fun disabledStatusChannelFailsSafely() {
        post()
        manager.createNotificationChannel(NotificationChannel(LockScreenNotifications.CHANNEL, "Status", NotificationManager.IMPORTANCE_NONE))
        post()
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun noOccurrenceCancelsPreviouslyPostedStatus() {
        post(); post(Snapshot()); assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun disablingOngoingKeepsDismissibleStatus() {
        post(); app.preferences.saveLockScreen(LockScreenSettings(ongoing = false)); post()
        assertEquals(0, current().flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(1, manager.activeNotifications.size)
    }
    @Test fun disablingRemindersCancelsStatus() {
        post(); app.preferences.save(AppSettings(notifications = false)); post()
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun handledDoseAdvancesAndUndoRestoresRealOccurrence() {
        val first = NextDose.next(data, date)!!
        val log = MedicationLogEntity(first.key, "s", date.toEpochDay(), first.minute, now.toEpochMilli())
        post(data.copy(logs = listOf(log))); assertTrue(text(current()).contains("20:00"))
        post(); assertTrue(text(current()).contains("12:00"))
        assertEquals(date.plusDays(1), NextDose.next(data.copy(logs = listOf(log,
            log.copy(key = "s:${date.toEpochDay()}:1200", minute = 1200, status = LogStatus.SKIPPED))), date)?.date)
    }
    @Test fun archivedMedicationOrPersonStoppedOrEndedTreatmentCannotRemain() {
        val invalid = listOf(data.copy(medications = listOf(data.medications.single().copy(archived = true))),
            data.copy(people = listOf(person.copy(archived = true))), data.copy(schedules = listOf(schedule.copy(active = false))),
            data.copy(schedules = listOf(schedule.copy(end = date.minusDays(1).toEpochDay()))),
            data.copy(schedules = listOf(schedule.copy(effectiveTo = date.toEpochDay()))), data.copy(medications = emptyList()))
        invalid.forEach { post(); post(it); assertTrue(manager.activeNotifications.isEmpty()) }
    }
    @Test fun refreshNeverMutatesBusinessData() = runBlocking {
        app.repository.replaceAll(data)
        val before = app.repository.snapshot()
        post(before); post(before)
        assertEquals(before, app.repository.snapshot())
    }
    @Test fun quickActionIsActivityIntentAndSendingItDoesNotRecordTaken() = runBlocking {
        app.preferences.saveLockScreen(LockScreenSettings(takenAction = true)); post()
        val action = current().actions.single()
        assertEquals("İçildi", action.title)
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertEquals(NextDose.next(data, date)!!.key, intent.getStringExtra(Reminders.EXTRA_KEY))
        action.actionIntent.send()
        assertTrue(app.repository.snapshot().logs.isEmpty())
    }
    @Test fun legacyTakenBroadcastIsInertEvenWithValidOccurrenceWhileLocked() = runBlocking {
        app.repository.replaceAll(data)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceLocked(true)
        ReminderActionReceiver().onReceive(app, Intent(app, ReminderActionReceiver::class.java)
            .setAction(Reminders.ACTION_TAKEN).putExtra(Reminders.EXTRA_KEY, NextDose.next(data, date)!!.key))
        assertEquals(data, app.repository.snapshot())
    }
    @Test @Config(sdk = [34]) fun android12PlusActionRequiresAuthentication() {
        assertTrue(LockScreenNotifications.takenAction(app, NextDose.next(data, date)!!.key).isAuthenticationRequired)
    }
    @Test fun longNamesAreRealAndNeverLeakToRedactedVersion() {
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true))
        val long = data.copy(people = listOf(person.copy(name = person.name.repeat(20))), schedules = listOf(schedule.copy(name = schedule.name.repeat(20))))
        post(long)
        assertTrue(text(current()).contains(long.people.single().name))
        assertTrue(text(current()).contains(long.schedules.single().name))
        assertFalse(text(current().publicVersion).contains(person.name))
    }
    @Test fun midnightTomorrowFarFutureAndOverdueFollowSharedDoseRule() {
        assertEquals(date, NextDose.next(data, date)?.date)
        assertEquals(date.plusDays(1), NextDose.next(data, date.plusDays(1))?.date)
        val future = data.copy(schedules = listOf(schedule.copy(start = date.plusMonths(3).toEpochDay(), days = 1)))
        val next = NextDose.next(future, date)!!
        assertTrue(next.date >= date.plusMonths(3)); assertEquals(DayOfWeek.MONDAY, next.date.dayOfWeek)
        assertEquals(720, NextDose.next(data, date)?.minute)
        assertTrue(LockScreenNotifications.body(NextDose.next(data, date)!!, LockScreenSettings()).contains("12:00"))
    }
    @Test fun invalidAndHandledNavigationKeysDoNotResolve() {
        assertNull(NextDose.resolve(data, "bad")); assertNull(NextDose.resolve(data, "s:99999999999999999:720"))
        val item = NextDose.next(data, date)!!
        assertNotNull(NextDose.resolve(data, item.key))
        assertNull(NextDose.resolve(data.copy(logs = listOf(MedicationLogEntity(item.key, "s", date.toEpochDay(), item.minute, now.toEpochMilli()))), item.key))
    }
    @Test fun permissionRegressionHasOnlyExistingRuntimePermissions() {
        val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        listOf("INTERNET", "ACCESS_FINE_LOCATION", "READ_CONTACTS", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "FOREGROUND_SERVICE").forEach {
            assertFalse(it, "android.permission.$it" in permissions)
        }
    }
    @Test @Config(sdk = [34]) fun notificationPermissionDenialDoesNotPostStatus() {
        shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        post(); assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun reminderRebuildRefreshesWithoutChangingOccurrenceHistory() = runBlocking {
        app.repository.replaceAll(data)
        val reminders = Reminders(app, Clock.fixed(now, ZoneId.systemDefault()))
        reminders.rebuild(false)
        assertTrue(text(current()).contains("12:00"))
        val first = NextDose.next(app.repository.snapshot(), date)!!
        app.repository.completions.taken(first)
        val afterTaken = app.repository.snapshot()
        reminders.rebuild(false)
        assertEquals(afterTaken, app.repository.snapshot()); assertTrue(text(current()).contains("20:00"))
        app.repository.archiveMedication("m"); reminders.rebuild(false)
        assertFalse(manager.activeNotifications.any { it.id == LockScreenNotifications.ID })
    }
    @Test fun existingDosePrivacyUpdatesAndActionsRemainSafe() = runBlocking {
        val due = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        app.repository.replaceAll(data)
        val reminders = Reminders(app, Clock.fixed(due, ZoneId.systemDefault()))
        app.preferences.nextAlarm = due.toEpochMilli()
        reminders.rebuild(true)
        fun dose() = manager.activeNotifications.single { it.id == Reminders.DOSE_ID }.notification
        assertFalse(text(dose()).contains(schedule.name)); assertFalse(text(dose()).contains(person.name))
        assertEquals(listOf("10 dk ertele"), dose().actions.map { it.title.toString() })
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true, takenAction = true))
        reminders.rebuild(false)
        assertTrue(text(dose()).contains(schedule.name)); assertTrue(text(dose()).contains(person.name))
        assertEquals(MainActivity::class.java.name, shadowOf(dose().actions.first().actionIntent).savedIntent.component!!.className)
        app.preferences.saveLockScreen(LockScreenSettings(visible = false))
        reminders.rebuild(false)
        assertEquals(Notification.VISIBILITY_SECRET, dose().visibility)
        assertFalse(text(dose()).contains(schedule.name)); assertFalse(text(dose().publicVersion).contains(person.name))
        assertTrue(app.repository.snapshot().logs.isEmpty())
        assertEquals(1, app.database.dao().dispatches().size)
    }
    private suspend fun postPrivacyReminders(detailed: Boolean): Reminders {
        val fixture = data.copy(
            people = listOf(person.copy(note = "PRIVATE_PERSON_NOTE")),
            medications = listOf(data.medications.single().copy(stockEnabled = true, stockQuantity = 2.0,
                stockThreshold = 3.0, renewalDate = date.toEpochDay(), renewalReminderEnabled = true, gtin = "08691234567890")),
            schedules = listOf(schedule.copy(times = "09:00,20:00", note = "PRIVATE_TREATMENT_NOTE"),
                schedule.copy(id = "s2", medicationId = "m2", times = "09:00")),
            packages = listOf(MedicationPackageEntity("PRIVATE_PACKAGE", "m", "08691234567890", "PRIVATE_SERIAL",
                "PRIVATE_BATCH", "271231", now.toEpochMilli(), 2.0)))
        app.repository.replaceAll(fixture.copy(medications = fixture.medications + MedicationEntity("m2", "p")))
        app.preferences.saveLockScreen(LockScreenSettings(detailed = detailed))
        val due = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant()
        app.preferences.nextAlarm = due.toEpochMilli()
        return Reminders(app, Clock.fixed(due, ZoneId.systemDefault())).also { it.rebuild(true) }
    }

    private fun notification(id: Int) = manager.activeNotifications.first { it.id == id }.notification
    private fun assertIdentityFree(n: Notification) {
        val content = text(n) + n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString() +
            n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT) + n.sortKey
        listOf(schedule.name, person.name, schedule.dose, "obsolete", "08691234567890", "PRIVATE_PACKAGE",
            "PRIVATE_SERIAL", "PRIVATE_BATCH", "PRIVATE_PERSON_NOTE", "PRIVATE_TREATMENT_NOTE").forEach {
            assertFalse("Identity leaked: $it in $content", content.contains(it))
        }
    }

    @Test fun privateRenewalAndStockUseExactGenericContent() = runBlocking {
        postPrivacyReminders(false)
        assertEquals("Reçete hatırlatması", notification(Reminders.RENEWAL_ID).extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Yaklaşan bir reçete yenileme hatırlatman var.", notification(Reminders.RENEWAL_ID).extras.getString(Notification.EXTRA_TEXT))
        assertEquals("Stok azalıyor", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Bir ilaç için stok uyarısı var.", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TEXT))
        manager.activeNotifications.forEach { assertIdentityFree(it.notification) }
    }

    @Test fun detailedRenewalAndStockPreserveExistingContent() = runBlocking {
        postPrivacyReminders(true)
        assertEquals(app.getString(com.levanyon.meditakip.R.string.renewal_notification_text, schedule.name),
            notification(Reminders.RENEWAL_ID).extras.getString(Notification.EXTRA_TEXT))
        assertEquals("${schedule.name} azalıyor", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TITLE))
        assertEquals("${person.name} · yaklaşık 1 günlük ilaç kaldı", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TEXT))
    }

    @Test fun switchingToPrivateImmediatelySanitizesAllPostedNotifications() = runBlocking {
        val reminders = postPrivacyReminders(true)
        val ids = manager.activeNotifications.map { it.tag to it.id }.toSet()
        assertTrue(ids.map { it.second }.containsAll(listOf(Reminders.DOSE_ID, Reminders.RENEWAL_ID,
            Reminders.LOW_STOCK_ID, Reminders.SUMMARY_ID, LockScreenNotifications.ID)))
        app.preferences.saveLockScreen(LockScreenSettings(detailed = false))
        reminders.rebuild(false)
        assertEquals(ids, manager.activeNotifications.map { it.tag to it.id }.toSet())
        manager.activeNotifications.forEach {
            assertIdentityFree(it.notification)
            assertIdentityFree(it.notification.publicVersion)
            assertEquals(Notification.VISIBILITY_PRIVATE, it.notification.visibility)
        }
    }

    @Test fun publicVersionsOfDoseRenewalAndStockAreAlwaysIdentityFree() = runBlocking {
        val reminders = postPrivacyReminders(true)
        for (detailed in listOf(true, false)) {
            app.preferences.saveLockScreen(LockScreenSettings(detailed = detailed)); reminders.rebuild(false)
            for (id in listOf(Reminders.DOSE_ID, Reminders.RENEWAL_ID, Reminders.LOW_STOCK_ID)) {
                assertIdentityFree(notification(id).publicVersion)
            }
        }
    }

    @Test fun returningToDetailedUsesCurrentRepositoryIdentityAndStock() = runBlocking {
        val reminders = postPrivacyReminders(true)
        app.preferences.saveLockScreen(LockScreenSettings()); reminders.rebuild(false)
        val dao = app.database.dao()
        dao.savePerson(person.copy(name = "Yeni kişi"))
        val snapshot = app.repository.snapshot()
        snapshot.schedules.forEach { dao.saveSchedule(it.copy(name = "Yeni ilaç")) }
        dao.saveMedication(snapshot.medications.first { it.id == "m" }.copy(stockQuantity = 1.0))
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true)); reminders.rebuild(false)
        for (id in listOf(Reminders.DOSE_ID, Reminders.RENEWAL_ID, Reminders.LOW_STOCK_ID, Reminders.SUMMARY_ID, LockScreenNotifications.ID)) {
            assertTrue(text(notification(id)).contains("Yeni ilaç"))
            assertFalse(text(notification(id)).contains(schedule.name))
        }
        assertTrue(text(notification(Reminders.DOSE_ID)).contains("Yeni kişi"))
        assertEquals("Yeni kişi · yaklaşık 0 günlük ilaç kaldı", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TEXT))
    }

    @Test fun privacyRefreshPreservesAllBusinessDataAndDeliveryRecords() = runBlocking {
        val reminders = postPrivacyReminders(true)
        val yesterday = date.minusDays(1).toEpochDay()
        app.database.dao().insertLog(MedicationLogEntity("s:$yesterday:540", "s", yesterday, 540,
            now.toEpochMilli(), status = LogStatus.TAKEN, note = "PRIVATE_TAKEN_NOTE"))
        app.database.dao().insertLog(MedicationLogEntity("s:$yesterday:1200", "s", yesterday, 1200,
            now.toEpochMilli(), status = LogStatus.SKIPPED, note = "PRIVATE_SKIPPED_NOTE"))
        val before = app.repository.snapshot()
        val dispatches = app.database.dao().dispatches()
        val stockNotified = app.preferences.lowStockNotified
        for (detailed in listOf(false, true, false)) {
            app.preferences.saveLockScreen(LockScreenSettings(detailed = detailed)); reminders.rebuild(false)
            assertEquals(before, app.repository.snapshot())
            assertEquals(dispatches, app.database.dao().dispatches())
            assertEquals(stockNotified, app.preferences.lowStockNotified)
        }
    }

    @Test fun lowStockWithoutDailyEstimatePreservesDetailedQuantityAndSanitizesOnRefresh() = runBlocking {
        val reminders = postPrivacyReminders(true)
        val revision = app.repository.snapshot().schedules.first { it.id == "s" }
        app.database.dao().saveSchedule(revision.copy(active = false))
        reminders.rebuild(false)
        assertEquals("${person.name} · kalan miktar: 2", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TEXT))
        app.preferences.saveLockScreen(LockScreenSettings()); reminders.rebuild(false)
        assertEquals("Bir ilaç için stok uyarısı var.", notification(Reminders.LOW_STOCK_ID).extras.getString(Notification.EXTRA_TEXT))
        assertIdentityFree(notification(Reminders.LOW_STOCK_ID))
    }

    @Test fun masterOffKeepsAllExistingNotificationsSecret() = runBlocking {
        val reminders = postPrivacyReminders(true)
        val ids = manager.activeNotifications.map { it.tag to it.id }.toSet()
        for (detailed in listOf(true, false)) {
            app.preferences.saveLockScreen(LockScreenSettings(visible = false, detailed = detailed)); reminders.rebuild(false)
            assertEquals(ids.filter { it.second != LockScreenNotifications.ID }.toSet(), manager.activeNotifications.map { it.tag to it.id }.toSet())
            manager.activeNotifications.forEach {
                assertEquals(Notification.VISIBILITY_SECRET, it.notification.visibility)
                assertIdentityFree(it.notification.publicVersion)
                if (!detailed) assertIdentityFree(it.notification)
            }
        }
    }

    private suspend fun restoreFixture(snapshot: Snapshot) {
        val backups = Backups(app.repository, app.preferences, app.filesDir, "test", Clock.fixed(now, ZoneId.systemDefault()), safetyCipher = testSafetyCipher())
        val json = BackupCodec.encode(snapshot, AppSettings(), now.toEpochMilli(), "test")
        backups.restore(backups.inspect(json.byteInputStream()))
    }

    @Test fun restoredColonScheduleIdsResolveAndRefreshFromCurrentModel() = runBlocking {
        val restored = data.copy(schedules = listOf(schedule.copy(id = "s:legacy:import")))
        restoreFixture(restored)
        val item = NextDose.next(app.repository.snapshot(), date)!!
        assertEquals("s:legacy:import", item.schedule.id)
        assertEquals(item, NextDose.resolve(app.repository.snapshot(), item.key))
        assertNull(NextDose.resolve(restored, "${item.key}:junk"))
        val due = item.instant(ZoneId.systemDefault())
        val reminders = Reminders(app, Clock.fixed(due, ZoneId.systemDefault()))
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true))
        app.preferences.nextAlarm = due.toEpochMilli(); reminders.rebuild(true)
        assertTrue(text(notification(Reminders.DOSE_ID)).contains(schedule.name))
        val before = app.repository.snapshot()
        app.preferences.saveLockScreen(LockScreenSettings()); reminders.rebuild(false)
        assertEquals(item.key, manager.activeNotifications.single { it.id == Reminders.DOSE_ID }.tag)
        assertIdentityFree(notification(Reminders.DOSE_ID))
        assertIdentityFree(notification(Reminders.DOSE_ID).publicVersion)
        assertEquals(before, app.repository.snapshot())
        app.database.dao().saveSchedule(restored.schedules.single().copy(name = "Current imported medication"))
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true)); reminders.rebuild(false)
        assertTrue(text(notification(Reminders.DOSE_ID)).contains("Current imported medication"))
        assertFalse(text(notification(Reminders.DOSE_ID)).contains(schedule.name))
    }

    private suspend fun unresolvedRestoredDosesAreCanceled(validCount: Int) {
        val invalidSchedules = listOf(schedule.copy(times = "12:00"),
            schedule.copy(id = "s2", medicationId = "m2", times = "12:00"))
        val validSchedules = (1..validCount).map {
            schedule.copy(id = "valid$it", medicationId = "vm$it", name = "Current medicine $it", times = "12:00")
        }
        val fixture = Snapshot(listOf(person, PersonEntity("q", "Current owner")),
            listOf(MedicationEntity("m", "p"), MedicationEntity("m2", "p")) +
                (1..validCount).map { MedicationEntity("vm$it", "q") }, invalidSchedules + validSchedules)
        restoreFixture(fixture)
        val due = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        val reminders = Reminders(app, Clock.fixed(due, ZoneId.systemDefault()))
        app.preferences.saveLockScreen(LockScreenSettings(detailed = true))
        app.preferences.nextAlarm = due.toEpochMilli(); reminders.rebuild(true)
        val oldDose = notification(Reminders.DOSE_ID)
        assertTrue(text(oldDose).contains(person.name)); assertTrue(text(oldDose).contains(schedule.name))
        assertTrue(text(oldDose).contains(schedule.dose))
        assertTrue(text(notification(Reminders.SUMMARY_ID)).contains(schedule.name))

        // Accepted restore data: the planner still sees these doses, but the resolver rejects their archived owner.
        restoreFixture(fixture.copy(people = listOf(person.copy(archived = true), PersonEntity("q", "Current owner"))))
        val before = app.repository.snapshot()
        val unresolvedKeys = Schedule.day(before, date).filter { it.personId == "p" }.map { it.key }.toSet()
        unresolvedKeys.forEach { assertNull(NextDose.resolve(before, it)) }
        assertTrue(ReminderPlan.events(before, AppSettings(), due, ZoneId.systemDefault()).map { it.occurrence.key }.containsAll(unresolvedKeys))
        val dispatches = app.database.dao().dispatches()
        for (detailed in listOf(false, true, false)) {
            app.preferences.saveLockScreen(LockScreenSettings(detailed = detailed)); reminders.rebuild(false)
            val posted = manager.activeNotifications
            assertFalse(posted.any { it.id == Reminders.DOSE_ID && it.tag in unresolvedKeys })
            assertEquals(validCount, posted.count { it.id == Reminders.DOSE_ID })
            assertEquals(if (validCount >= 2) 1 else 0, posted.count { it.id == Reminders.SUMMARY_ID })
            posted.forEach {
                val content = text(it.notification) + it.notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString() + it.notification.sortKey
                assertFalse(content.contains(person.name)); assertFalse(content.contains(schedule.name))
                assertFalse(content.contains(text(oldDose)))
                if (!detailed) assertIdentityFree(it.notification)
                assertIdentityFree(it.notification.publicVersion)
            }
            if (detailed && validCount > 0) assertTrue(text(notification(Reminders.DOSE_ID)).contains("Current medicine"))
            if (detailed && validCount >= 2) assertTrue(text(notification(Reminders.SUMMARY_ID)).contains("Current medicine"))
            assertEquals(before, app.repository.snapshot())
            assertEquals(dispatches, app.database.dao().dispatches())
        }
    }

    @Test fun unresolvedRestoredDosesLeaveNoNotificationOrSummary() = runBlocking { unresolvedRestoredDosesAreCanceled(0) }
    @Test fun unresolvedRestoredDosesDoNotHideTheOneRemainingValidDose() = runBlocking { unresolvedRestoredDosesAreCanceled(1) }
    @Test fun unresolvedRestoredDosesCannotContributeToSurvivingSummary() = runBlocking { unresolvedRestoredDosesAreCanceled(2) }

    @Test fun timezoneAndDstKeepScheduleInstantSemantics() {
        val spring = LocalDate.of(2026, 3, 29)
        val fall = LocalDate.of(2026, 10, 25)
        for (day in listOf(spring, fall)) {
            val sample = data.copy(schedules = listOf(schedule.copy(start = day.toEpochDay(), effectiveFrom = day.toEpochDay(), times = "02:30")))
            val item = NextDose.next(sample, day)!!
            for (zone in listOf(ZoneId.of("Europe/Berlin"), ZoneId.of("Europe/Istanbul"), ZoneOffset.UTC)) {
                assertEquals(day.atTime(2, 30).atZone(zone).toInstant(), item.instant(zone))
            }
        }
    }
    @Test fun midnightRefreshDoesNotInventAMedicationReminderInHealth() = runBlocking {
        app.repository.replaceAll(data)
        val evening = date.atTime(23, 0).atZone(ZoneId.systemDefault()).toInstant()
        val reminders = Reminders(app, Clock.fixed(evening, ZoneId.systemDefault()))
        reminders.rebuild(false)
        assertEquals(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), app.preferences.nextAlarm)
        assertEquals(date.plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), reminders.health().nextReminderAt)
    }
}
