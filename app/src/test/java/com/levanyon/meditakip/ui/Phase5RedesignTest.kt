package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.Snapshot
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class Phase5RedesignTest {
    @get:Rule val compose = createComposeRule()
    private val data = Phase5TestData
    private fun show(body: @Composable () -> Unit) { compose.setContent { MediTheme("Light") { Surface(Modifier.fillMaxSize()) { body() } } } }
    private fun scroll(text: String) { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text)) }
    private fun history(snapshot: Snapshot = data.snapshot) { show { HistoryScreen(snapshot, data.today, null, {}, {}) } }
    private fun settings(access: PermissionAccess = data.access(), value: AppSettings = AppSettings(), change: (AppSettings) -> Unit = {},
        reminders: () -> Unit = {}, create: () -> Unit = {}, restore: () -> Unit = {}) {
        show { SettingsContent(value, access, "9.8-test", change, reminders) { BackupRows(false, create, restore) } }
    }
    @Test fun historyShowsApprovedEmptyStateWithoutInventedDoses() {
        history(data.snapshot.copy(logs = emptyList()))
        compose.onNodeWithText("Henüz geçmiş kaydı yok").assertIsDisplayed()
        compose.onNodeWithText("İlaçları işaretledikçe kullanım geçmişin burada görünür.").assertIsDisplayed()
        compose.onNodeWithText("İçildi").assertDoesNotExist()
    }
    @Test fun personFilterSwitchesRealPeopleAndAllWithoutWritingLogs() {
        history()
        compose.onNodeWithContentDescription("Kardeşim").performClick().assertIsSelected()
        compose.onNodeWithText("D Vitamini").assertIsDisplayed()
        compose.onNodeWithText("Beloc ZOK 50 mg").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tümü").performClick().assertIsSelected()
        compose.onAllNodesWithText("Beloc ZOK 50 mg").onFirst().assertIsDisplayed()
        assertEquals(7, data.snapshot.logs.size)
    }
    @Test fun sheetAppliesDateAndStateTogetherAndRetainsSelections() {
        history()
        compose.onNodeWithContentDescription("Geçmişi filtrele").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("30 gün").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("30 gün").assertIsSelected()
        compose.onNodeWithContentDescription("Atlandı").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Atlandı").assertIsSelected()
        compose.onNodeWithText("Filtreyi uygula").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Levotiron 50 mcg").assertIsDisplayed()
        compose.onNodeWithText("Beloc ZOK 50 mg").assertDoesNotExist()
        compose.onNodeWithContentDescription("Geçmişi filtrele").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("30 gün").assertIsSelected()
        compose.onNodeWithContentDescription("Atlandı").assertIsSelected()
    }
    @Test fun dismissingSheetDoesNotApplyDraftFilters() {
        var applied: HistoryFilter? = null
        var dismissed = false
        show { HistoryFilterSheet(HistoryFilter(), { dismissed = true }, { applied = it }) }
        compose.onNodeWithContentDescription("Atlandı").performClick()
        // The platform dismiss semantic follows the same onDismissRequest path as back/scrim.
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.Dismiss)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss)
        compose.waitForIdle()
        assertTrue(dismissed); assertNull(applied)
    }
    @Test fun recordedRowOpensExistingDoseDetails() {
        history()
        compose.onAllNodesWithText("Beloc ZOK 50 mg").onFirst().performClick()
        compose.onNodeWithText("Planlanan").assertIsDisplayed()
        compose.onNodeWithText("1 tablet").assertIsDisplayed()
        compose.onNodeWithText("Tamam").performScrollTo().performClick()
    }
    @Test fun normalPermissionsAndRealVersionAreDisplayed() {
        settings()
        compose.onNodeWithText("Tam zamanında hatırlatma kapalı").assertDoesNotExist()
        compose.onAllNodesWithText("Açık").assertCountEquals(2)
        scroll("MediTakip hakkında")
        compose.onNodeWithText("9.8-test").assertIsDisplayed()
    }
    @Test fun missingExactAlarmActionUsesOnlyExactCallback() {
        var exact = 0; var notifications = 0
        settings(data.access(exact = false, onExact = { exact++ }, onNotification = { notifications++ }))
        compose.onNodeWithText("Tam zamanında hatırlatma kapalı").assertIsDisplayed()
        compose.onNodeWithText("Aç").performClick()
        assertEquals(1, exact); assertEquals(0, notifications)
    }
    @Test fun notificationPermissionRequiresExplicitActionAndPreservesToggle() {
        var requested = 0; var changed: AppSettings? = null
        settings(data.access(notifications = false, onNotification = { requested++ }), change = { changed = it })
        compose.onNodeWithText("Bildirimler").performClick()
        compose.onNodeWithText("Android bildirim izni kapalı. Hatırlatmalar için izin ver.").assertIsDisplayed()
        assertEquals(0, requested)
        compose.onNodeWithText("Ayarları Aç").performScrollTo().performClick()
        assertEquals(1, requested); assertNull(changed)
    }
    @Test fun applicationNotificationOffIsTruthfulEvenWithOsPermission() {
        settings(value = AppSettings(notifications = false))
        compose.onNodeWithText("Kapalı").assertIsDisplayed()
    }
    @Test fun themeRowRetainsAllThreeExistingPreferences() {
        var selected: AppSettings? = null
        settings(change = { selected = it })
        compose.onNodeWithText("Tema").performClick()
        compose.onNode(hasText("Sistem") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Açık") and isSelectable()).assertIsDisplayed()
        compose.onNodeWithText("Koyu").performClick()
        assertEquals(AppSettings(theme = "Dark"), selected)
    }
    @Test fun backupAndRestoreRowsRouteSeparateActions() {
        var created = 0; var restored = 0
        settings(create = { created++ }, restore = { restored++ })
        scroll("Yedek oluştur"); compose.onNodeWithText("Yedek oluştur").performClick()
        assertEquals(1, created); assertEquals(0, restored)
        scroll("Yedekten geri yükle"); compose.onNodeWithText("Yedekten geri yükle").performClick()
        assertEquals(1, restored)
    }
    @Test fun busyBackupRowsDoNotAllowDuplicateActions() {
        show { BackupRows(true, { fail("busy export") }, { fail("busy restore") }) }
        compose.onNodeWithText("Yedek oluştur").assertIsNotEnabled()
        compose.onNodeWithText("Yedekten geri yükle").assertIsNotEnabled()
    }
    @Test fun privacyExplainsLocalStorageAndUnencryptedExports() {
        settings()
        scroll("Veriler"); compose.onNodeWithText("Veriler").performClick()
        compose.onNodeWithText("Yedek sağlık bilgilerinizi içerir ve şifrelenmez.", substring = true).performScrollTo().assertIsDisplayed()
    }
}
