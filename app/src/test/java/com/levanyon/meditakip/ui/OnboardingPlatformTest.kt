package com.levanyon.meditakip.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.Preferences
import com.levanyon.meditakip.domain.Snapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config

/** Exercise the actual shared Android launchers and completion persistence, not replacement handlers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MediApp::class, qualifiers = "w390dp-h844dp-mdpi")
class OnboardingPlatformTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication() as MediApp
    @After fun close() {
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }
    private fun click(label: String) = compose.onNodeWithText(label).performScrollTo().performClick()
    private fun show() {
        compose.setContent { MediTheme("Light") { Surface {
            // Robolectric replaces Application between tests; do not reuse Android's singleton factory.
            Onboarding(viewModel(factory = ViewModelProvider.AndroidViewModelFactory(app)))
        } } }
    }
    @Test fun notificationRequestContainsOnlyPostNotificationsAndDenialOpensAppSettings() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show(); click("Başla")
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        click("Bildirimleri etkinleştir")
        val requested = shadowOf(compose.activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requested.requestedPermissions)
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(requested.requestCode, requested.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
        }
        compose.onNodeWithText("Bildirim izni kapalı").assertIsDisplayed()
        click("Ayarları aç")
        val launched = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, launched.action)
        assertEquals(app.packageName, launched.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(launched, Activity.RESULT_CANCELED, Intent()) }
        compose.onNodeWithText("Bildirim izni kapalı").assertIsDisplayed()
        click("Şimdilik geç")
        assertFalse(app.preferences.onboardingCompleted.value)
    }
    @Test @Config(sdk = [31])
    fun exactAlarmUsesExistingPackageSettingsAndRefreshesOnReturn() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        show(); click("Başla")
        compose.onNodeWithText("Hatırlatmalar tam zamanında gelsin").assertIsDisplayed()
        click("Tam zamanında hatırlatmayı aç")
        val launched = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, launched.action)
        assertEquals("package:" + app.packageName, launched.data.toString())
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(launched, Activity.RESULT_CANCELED, Intent()) }
        compose.onNodeWithText("Tam zamanında hatırlatma kapalı").assertIsDisplayed()
        click("Ayarları aç")
        val second = shadowOf(compose.activity).nextStartedActivityForResult.intent
        compose.runOnIdle {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
            shadowOf(compose.activity).receiveResult(second, Activity.RESULT_OK, Intent())
        }
        compose.onNodeWithText("Her şey hazır").assertIsDisplayed()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }
    @Test @Config(sdk = [28])
    fun readyPersistsCompletionAndOpensExistingFirstPersonEditor() {
        var model: AppViewModel? = null
        compose.setContent {
            val vm: AppViewModel = viewModel<AppViewModel>(factory = ViewModelProvider.AndroidViewModelFactory(app)).also { model = it }
            val completed by vm.onboardingCompleted.collectAsState()
            MediTheme("Light") {
                LaunchTransition(false) {
                    AppDestination(completed, { Onboarding(vm) }, { Home(Snapshot(), vm) })
                }
            }
        }
        compose.runOnIdle { assertSame(app, model!!.getApplication<MediApp>()) }
        click("Başla")
        assertFalse(app.preferences.onboardingCompleted.value)
        click("İlk kişiyi ekle")
        compose.onNodeWithText("Ad").assertIsDisplayed()
        compose.onNodeWithText("Kişi Ekle").assertIsDisplayed()
        assertTrue(app.preferences.setupPending.value)
        assertTrue(Preferences(app).onboardingCompleted.value)
        compose.onNodeWithContentDescription("Geri").performClick()
        compose.waitUntil(5_000) { !app.preferences.setupPending.value }
        assertTrue(Preferences(app).onboardingCompleted.value)
        compose.onNodeWithText("Başla").assertDoesNotExist()
    }
}
