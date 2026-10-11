package com.levanyon.meditakip.ui

import android.app.Application
import android.app.KeyguardManager
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Phase9SettingsTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.now()
    private val schedule = MedicationScheduleEntity("s", "m", "old name", "Gerçek ilaç", "1 tablet", "", "12:00", 127, today.toEpochDay(), null, true, today.toEpochDay())
    private val snapshot = Snapshot(listOf(PersonEntity("p", "Anne")), listOf(MedicationEntity("m", "p")), listOf(schedule))
    private fun scroll(label: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
    }
    @Test fun settingsPersistAndModesExposeRadioSelection() {
        val preferences = Preferences(RuntimeEnvironment.getApplication())
        compose.setContent {
            val settings by preferences.lockScreen.collectAsState()
            MediTheme("Light") { LockScreenSettingsContent(settings, snapshot, preferences::saveLockScreen, {}) }
        }
        scroll("Gizli"); compose.onNodeWithText("Gizli").assertIsSelected()
        compose.onNodeWithText("Ayrıntılı").performClick().assertIsSelected()
        scroll("Kilit ekranında göster"); compose.onNodeWithText("Kilit ekranında göster").performClick().assertIsOff()
        scroll("Sıradaki dozu sabitle"); compose.onNodeWithText("Sıradaki dozu sabitle").performClick().assertIsOff()
        scroll("Kilit ekranında ‘İçildi’"); compose.onNodeWithText("Kilit ekranında ‘İçildi’").performClick().assertIsOn()
        compose.runOnIdle { assertEquals(LockScreenSettings(false, true, false, true), Preferences(RuntimeEnvironment.getApplication()).lockScreen.value) }
    }
    @Test fun notificationNavigationRequiresExplicitUnlockedConfirmation() {
        val keyguard = RuntimeEnvironment.getApplication().getSystemService(KeyguardManager::class.java)
        shadowOf(keyguard).setIsDeviceLocked(true)
        var calls = 0
        var dismissed = false
        val item = NextDose.next(snapshot, today)!!
        compose.setContent { MediTheme("Light") {
            NotificationDoseConfirmation(snapshot, item.key, { dismissed = true }) { assertEquals(item, it); calls++ }
        } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("İçildi olarak kaydet").performClick()
        compose.runOnIdle { assertEquals(0, calls); assertFalse(dismissed); shadowOf(keyguard).setIsDeviceLocked(false); shadowOf(keyguard).setKeyguardLocked(false) }
        compose.onNodeWithText("İçildi olarak kaydet").performClick()
        compose.runOnIdle { assertEquals(1, calls); assertTrue(dismissed) }
    }
    @Test fun archivedOrHandledOccurrenceCannotBeConfirmed() {
        val item = NextDose.next(snapshot, today)!!
        compose.setContent { MediTheme("Light") {
            NotificationDoseConfirmation(snapshot.copy(people = listOf(snapshot.people.single().copy(archived = true))), item.key, {}) { fail("Archived dose mutated") }
        } }
        compose.onNodeWithText("İçildi olarak kaydet").assertDoesNotExist()
        compose.onNodeWithText("Bu doz artık işlem için uygun değil.").assertIsDisplayed()
    }
    @Test fun emptyPreviewDoesNotFabricateMedication() {
        compose.setContent { MediTheme("Light") { LockScreenSettingsContent(LockScreenSettings(), Snapshot(), {}, {}) } }
        compose.onNodeWithText("Sıradaki doz yok").assertExists()
        compose.onNodeWithText("Beloc ZOK 50 mg").assertDoesNotExist()
    }
}
