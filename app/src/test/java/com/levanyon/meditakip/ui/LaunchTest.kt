package com.levanyon.meditakip.ui

import android.app.Application
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LaunchTest {
    @get:Rule val compose = createComposeRule()
    @Test fun launchOverlayDoesNotAllowTapsThroughToHiddenActions() {
        compose.mainClock.autoAdvance = false
        var clicked = false
        compose.setContent { MediTheme("Light") {
            LaunchTransition(true) { Button(onClick = { clicked = true }, modifier = Modifier.fillMaxSize()) { Text("Destination action") } }
        } }
        compose.onRoot().performTouchInput { click(center) }
        compose.runOnIdle { assertFalse(clicked) }
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("Destination action").performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
    @Test fun firstUseRoutesToOnboarding() {
        compose.setContent { MediTheme("Light") { AppDestination(false, { Text("Welcome destination") }, { Text("Home destination") }) } }
        compose.onNodeWithText("Welcome destination").assertIsDisplayed()
        compose.onNodeWithText("Home destination").assertDoesNotExist()
    }
    @Test fun returningUserRoutesToHomeInDarkTheme() {
        compose.setContent { MediTheme("Dark") { AppDestination(true, { Text("Welcome destination") }, { Text("Home destination") }) } }
        compose.onNodeWithText("Home destination").assertIsDisplayed()
        compose.onNodeWithText("Welcome destination").assertDoesNotExist()
    }
    @Test fun destinationInitializesDuringAnimationAndAnimationCompletes() {
        compose.mainClock.autoAdvance = false
        var initialized = false
        compose.setContent { MediTheme("Light") { LaunchTransition(true) { SideEffect { initialized = true }; Text("Destination") } } }
        compose.runOnIdle { assertTrue(initialized) }
        compose.onNodeWithTag("launchArtwork").assertExists()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("Destination").assertIsDisplayed()
        compose.onNodeWithTag("launchArtwork").assertDoesNotExist()
    }
    @Test fun disabledMotionShowsDestinationImmediately() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MediTheme("Dark") { LaunchTransition(false) { Text("Destination") } } }
        compose.onNodeWithText("Destination").assertIsDisplayed()
        compose.onNodeWithTag("launchArtwork").assertDoesNotExist()
    }
    @Test fun systemDisabledAndReducedAnimatorScaleSkipMotion() {
        val context = RuntimeEnvironment.getApplication()
        val original = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            assertFalse(launchMotionEnabled(context))
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, .5f)
            assertFalse(launchMotionEnabled(context))
        } finally { Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, original) }
    }
    @Test fun completedAnimationDoesNotReplayAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MediTheme("Light") { LaunchTransition(true) { Text("Destination") } } }
        compose.mainClock.advanceTimeBy(900)
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Destination").assertIsDisplayed()
        compose.onNodeWithTag("launchArtwork").assertDoesNotExist()
    }
    @Test fun keyframesHonorSpecifiedTiming() {
        assertEquals(.35f, launchFrame(0).logoAlpha, .001f)
        assertEquals(.92f, launchFrame(0).logoScale, .001f)
        assertEquals(1f, launchFrame(220).logoAlpha, .001f)
        assertEquals(0f, launchFrame(220).textAlpha, .001f)
        assertEquals(6f, launchFrame(220).textOffset, .001f)
        assertEquals(1f, launchFrame(560).textAlpha, .001f)
        assertEquals(0f, launchFrame(560).textOffset, .001f)
        assertEquals(.97f, launchFrame(800).settle, .001f)
        assertEquals(1f, launchFrame(800).destinationAlpha, .001f)
    }
}
