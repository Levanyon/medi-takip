package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.levanyon.meditakip.domain.PermissionAction
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PermissionState(false, false, PermissionAction.REQUEST, PermissionAction.SETTINGS, false))
    private var busy by mutableStateOf(false)
    private var notificationRequests = 0
    private var exactRequests = 0
    private var completed: Boolean? = null
    private var dark by mutableStateOf(false)
    private fun content(): @Composable () -> Unit = {
        MediTheme(if (dark) "Dark" else "Light") { Surface {
            OnboardingFlow(PermissionAccess(state, busy,
                { notificationRequests++; busy = true }, { exactRequests++; busy = true })) { completed = it }
        } }
    }
    private fun show() = compose.setContent(content())
    private fun click(label: String) = compose.onNodeWithText(label).performScrollTo().performClick()
    private fun start() { show(); click("Başla") }
    @Test fun welcomeExplainsLocalDataWithoutRequestingPermissions() {
        show()
        compose.onNodeWithText("İlaç takibini kafandan çıkar.").assertIsDisplayed()
        compose.onNodeWithText("Verilerin cihazında tutulur.").assertIsDisplayed()
        assertEquals(0, notificationRequests); assertEquals(0, exactRequests); assertNull(completed)
        compose.onNodeWithContentDescription("Adım 1 / 4").assertExists()
    }
    @Test fun notificationRationalePrecedesExplicitRequest() {
        start()
        compose.onNodeWithText("İlaç saatlerini kaçırma").assertIsDisplayed()
        assertEquals(0, notificationRequests)
        click("Bildirimleri etkinleştir")
        assertEquals(1, notificationRequests)
        compose.onNodeWithText("Şimdilik geç").assertIsNotEnabled()
        assertNull(completed)
    }
    @Test fun skipBothPermissionsReachesReadyWithoutCompletingEarly() {
        start(); click("Şimdilik geç")
        compose.onNodeWithText("Hatırlatmalar tam zamanında gelsin").assertIsDisplayed()
        click("Şimdilik geç")
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        assertNull(completed); assertEquals(0, notificationRequests); assertEquals(0, exactRequests)
        click("İlk kişiyi ekle"); assertEquals(true, completed)
    }
    @Test fun alreadyGrantedPermissionsGoStraightFromWelcomeToReady() {
        state = state.copy(notifications = true, exact = true)
        start()
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        compose.onNodeWithContentDescription("Adım 2 / 2").assertExists()
        click("Daha sonra"); assertEquals(false, completed)
    }
    @Test fun alreadyGrantedNotificationSkipsOnlyNotification() {
        state = state.copy(notifications = true)
        start()
        compose.onNodeWithText("Hatırlatmalar tam zamanında gelsin").assertIsDisplayed()
        assertEquals(0, notificationRequests)
    }
    @Test fun alreadyGrantedExactSkipsOnlyExact() {
        state = state.copy(exact = true)
        start(); click("Şimdilik geç")
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        assertEquals(0, exactRequests)
    }
    @Test fun deniedNotificationOffersRecoveryAndCanBeSkipped() {
        start(); click("Bildirimleri etkinleştir")
        compose.runOnIdle { state = state.copy(previouslyAsked = true, notificationAction = PermissionAction.SETTINGS); busy = false }
        compose.onNodeWithText("Bildirim izni kapalı").assertIsDisplayed()
        click("Ayarları aç"); assertEquals(2, notificationRequests)
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Bildirim izni kapalı").assertIsDisplayed()
        assertEquals(2, notificationRequests)
        click("Şimdilik geç")
        compose.onNodeWithText("Hatırlatmalar tam zamanında gelsin").assertIsDisplayed()
    }
    @Test fun dismissedNotificationDialogHasRecoveryWithoutAutoRetry() {
        start(); click("Bildirimleri etkinleştir")
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Bildirim izni kapalı").assertIsDisplayed()
        assertEquals(1, notificationRequests)
    }
    @Test fun exactDenialReturnsToRecoveryWithoutDeadEnd() {
        start(); click("Şimdilik geç"); click("Tam zamanında hatırlatmayı aç")
        assertEquals(1, exactRequests)
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Tam zamanında hatırlatma kapalı").assertIsDisplayed()
        click("Ayarları aç"); assertEquals(2, exactRequests)
        compose.runOnIdle { busy = false }
        click("Şimdilik geç"); compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
    }
    @Test fun notificationGrantedOnSettingsReturnAdvancesOnce() {
        start(); click("Bildirimleri etkinleştir")
        compose.runOnIdle { state = state.copy(notifications = true); busy = false }
        compose.onNodeWithText("Hatırlatmalar tam zamanında gelsin").assertIsDisplayed()
        assertEquals(1, notificationRequests); assertEquals(0, exactRequests)
    }
    @Test fun exactGrantedOnSettingsReturnAdvancesToReady() {
        state = state.copy(notifications = true)
        start(); click("Tam zamanında hatırlatmayı aç")
        compose.runOnIdle { state = state.copy(exact = true); busy = false }
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        assertNull(completed)
    }
    @Test fun futurePermissionGrantedWhileAwayIsOmitted() {
        start()
        compose.runOnIdle { state = state.copy(exact = true) }
        click("Şimdilik geç")
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        compose.onNodeWithContentDescription("Adım 3 / 3").assertExists()
    }
    @Test fun savedStateRestoresExactRecoveryAndThemeDoesNotRestartFlow() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content())
        click("Başla"); click("Şimdilik geç"); click("Tam zamanında hatırlatmayı aç")
        compose.runOnIdle { busy = false }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Tam zamanında hatırlatma kapalı").assertIsDisplayed()
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("Tam zamanında hatırlatma kapalı").assertIsDisplayed()
        assertEquals(1, exactRequests)
    }
    @Test fun readySurvivesSavedStateRestorationWithoutCompleting() {
        state = state.copy(notifications = true, exact = true)
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content())
        click("Başla")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        assertNull(completed)
    }
}
