package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h800dp")
class DesignSystemTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longStatusReflowsBelowTimeAtLargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MediTheme("Dark") {
                    Box(Modifier.width(280.dp)) {
                        AdaptivePair({ Text("20:00") }, { StatusChip("Alındı olarak işaretlenmedi") })
                    }
                }
            }
        }
        val time = compose.onNodeWithText("20:00").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithText("Alındı olarak işaretlenmedi").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Status must reflow without overlapping the time: time=$time, status=$status", status.top >= time.bottom)
        assertTrue("Long status remains within the available width", status.right <= 280f)
    }

    @Test fun navigationRemainsReachableAndSelectableWithLargeTurkishLabels() {
        val labels = listOf("Bugün", "İlaçlar", "Kişiler", "Geçmiş", "Ayarlar")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MediTheme("Light") {
                    var selected by remember { mutableIntStateOf(0) }
                    Box(Modifier.width(320.dp)) {
                        MediNavigationBar(labels, List(5) { Icons.Default.Today }, selected) { selected = it }
                    }
                }
            }
        }
        labels.forEach { label ->
            val tab = compose.onNode(hasText(label) and isSelectable())
            tab.performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            tab.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertTextFits(label)
        }
    }

    private fun assertTextFits(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("Text layout should be available for $label", results.isNotEmpty())
        // Navigation measures labels more than once. A reused paragraph can retain the wider
        // measurement constraint even when its glyphs fit the final Text size; check ink lines.
        results.forEach { result ->
            assertTrue("$label must fit vertically", !result.didOverflowHeight)
            (0 until result.lineCount).forEach { line ->
                assertTrue("$label must not ellipsize", !result.isLineEllipsized(line))
                assertTrue("$label line must fit horizontally",
                    result.getLineLeft(line) >= -0.5f && result.getLineRight(line) <= result.size.width + 0.5f)
            }
        }
    }

    @Test fun fiveNavigationItemsFitAtDefaultFontSize() {
        val labels = listOf("Bugün", "İlaçlar", "Kişiler", "Geçmiş", "Ayarlar")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                MediTheme("Dark") {
                    Box(Modifier.width(360.dp)) {
                        MediNavigationBar(labels, List(5) { Icons.Default.Today }, 0) {}
                    }
                }
            }
        }
        labels.forEach { label ->
            val tab = compose.onNode(hasText(label) and isSelectable()).assertIsDisplayed()
            val bounds = tab.fetchSemanticsNode().boundsInRoot
            assertTrue("Every default-size tab fits inside the shell", bounds.left >= 12f && bounds.right <= 348f)
            tab.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertTextFits(label)
        }
    }

    @Test fun sharedActionsAndBackHeaderRemainAccessibleAtLargeFonts() {
        var secondary = false
        var quiet = false
        var back = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MediTheme("Dark") {
                    Column(Modifier.width(280.dp)) {
                        BackHeader("Kişi bilgilerini düzenle") { back = true }
                        SecondaryAction("Kişi bilgilerini kaydet") { secondary = true }
                        QuietAction("Değişikliklerden vazgeç") { quiet = true }
                        PrimaryAction("Kaydet", enabled = false) { error("Disabled action invoked") }
                    }
                }
            }
        }
        listOf("Kişi bilgilerini kaydet", "Değişikliklerden vazgeç").forEach { label ->
            compose.onNodeWithText(label).assertHeightIsAtLeast(48.dp).performClick()
            assertTextFits(label)
        }
        compose.onNodeWithContentDescription("Geri").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Kaydet").assertIsNotEnabled()
        compose.onNodeWithText("Kişi bilgilerini düzenle")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertTrue(secondary && quiet && back)
    }

    @Test fun longPrimaryActionGrowsAndKeepsItsTouchTarget() {
        var clicked = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MediTheme("Light") {
                    Box(Modifier.width(280.dp)) { PrimaryAction("Yeni aile üyesi için ilaç ekle") { clicked = true } }
                }
            }
        }
        compose.onNode(hasClickAction()).assertIsDisplayed().assertHeightIsAtLeast(56.dp).performClick()
        assertTrue(clicked)
    }

    @Test
    @Config(qualifiers = "w1200dp-h900dp")
    fun readingColumnIsFluidAndCenteredOnLargeScreens() {
        compose.setContent {
            MediTheme("Light") {
                MediContent(Modifier.fillMaxSize().testTag("outer")) {
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag("inner"))
                }
            }
        }
        compose.onNodeWithTag("inner").assertWidthIsEqualTo(MediSize.readingWidth)
        val outer = compose.onNodeWithTag("outer").fetchSemanticsNode().boundsInRoot
        val inner = compose.onNodeWithTag("inner").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(outer.center.x - inner.center.x) < 1f)
    }
}
