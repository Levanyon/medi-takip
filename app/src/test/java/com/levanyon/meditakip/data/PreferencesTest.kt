package com.levanyon.meditakip.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PreferencesTest {
    @Test fun onboardingPersistsWithoutChangingExistingPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val first = Preferences(context)
        val settings = AppSettings(false, true, 60, "Dark")
        first.save(settings)
        assertFalse(first.onboardingCompleted.value)
        first.completeOnboarding()
        val reopened = Preferences(context)
        assertTrue(reopened.onboardingCompleted.value)
        assertEquals(settings, reopened.state.value)
    }
    @Test fun permissionRequestHistoryDoesNotImplyPermissionGranted() {
        val context = RuntimeEnvironment.getApplication()
        val first = Preferences(context)
        assertFalse(first.notificationAsked)
        first.notificationAsked = true
        val reopened = Preferences(context)
        assertTrue(reopened.notificationAsked)
        assertFalse(reopened.onboardingCompleted.value)
    }
    @Test fun lastSelectedPersonSurvivesRestartAndCanBeCleared() {
        val context = RuntimeEnvironment.getApplication()
        val first = Preferences(context)
        assertNull(first.lastPerson.value)
        first.rememberPerson("anne")
        assertEquals("anne", Preferences(context).lastPerson.value)
        first.rememberPerson(null)
        assertNull(Preferences(context).lastPerson.value)
    }
}
