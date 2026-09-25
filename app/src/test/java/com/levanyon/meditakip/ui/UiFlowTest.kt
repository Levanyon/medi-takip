package com.levanyon.meditakip.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class, qualifiers = "w360dp-h800dp")
class UiFlowTest {
    @get:Rule val compose = createComposeRule()
    @After fun closeAppResources() {
        val app = RuntimeEnvironment.getApplication() as MediApp
        runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin() }
        app.database.close()
    }
    private val date = LocalDate.now()
    private val schedule = MedicationScheduleEntity("s", "m", "Anne", "Parol", "1 Tablet", "", "08:00,20:00", 127, date.toEpochDay(), null, true, date.toEpochDay())
    private val taken = date.atTime(8, 4).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val fixture = Snapshot(listOf(PersonEntity("p", "Anne")), listOf(MedicationEntity("m", "p")), listOf(schedule), listOf(MedicationLogEntity("s:${date.toEpochDay()}:480", "s", date.toEpochDay(), 480, taken)))

    @Test fun primaryNavigationHasFourDestinationsAndNoMedicationsTab() {
        compose.setContent { MediTheme("Light") { Home(fixture, viewModel()) } }
        listOf("Bugün", "Kişiler", "Geçmiş", "Ayarlar").forEach { compose.onNode(hasText(it) and isSelectable()).assertExists() }
        compose.onNode(hasText("İlaçlar") and isSelectable()).assertDoesNotExist()
        compose.onNodeWithText("✓ İçildi • 08:04").assertIsDisplayed()
    }
    @Test fun peopleLeadToPersonDetailAndMedicationDetail() {
        compose.setContent { MediTheme("Light") { Home(fixture, viewModel()) } }
        compose.onNode(hasText("Kişiler") and isSelectable()).performClick()
        compose.onNodeWithText("Kişi Ekle").assertIsDisplayed()
        compose.onNodeWithText("Anne").performClick()
        compose.onNodeWithText("Düzenle").assertIsDisplayed()
        compose.onNodeWithText("İlaç Ekle").assertIsDisplayed()
        compose.onNodeWithText("Parol").performClick()
        compose.onNodeWithText("İlacı Düzenle").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Son 7 gün"))
        compose.onNodeWithText("Son 7 gün").assertIsDisplayed()
    }
    @Test fun personEditorOpensInsideTheActivityWindowWithSaveReachable() {
        compose.setContent { MediTheme("Dark") { Home(fixture, viewModel()) } }
        compose.onNode(hasText("Kişiler") and isSelectable()).performClick()
        compose.onNodeWithText("Kişi Ekle").performClick()
        compose.onNodeWithText("Ad").assertIsDisplayed()
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed()
        // The scaffold underneath is hidden from accessibility while the form is open.
        compose.onNode(hasText("Kişiler") and isSelectable()).assertDoesNotExist()
    }
    @Test fun historyShowsPersonTableFromRealLogs() {
        compose.setContent { MediTheme("Light") { Home(fixture, viewModel()) } }
        compose.onNode(hasText("Geçmiş") and isSelectable()).performClick()
        compose.onNodeWithText("Tarih").assertIsDisplayed()
        compose.onAllNodesWithText("Parol").onFirst().assertIsDisplayed()
        compose.onNodeWithText("08:04").assertIsDisplayed().performClick()
        compose.onNodeWithText("Planlanan").assertIsDisplayed()
    }
    @Test fun restoringPersonTellsThatMedicationsStayArchived() {
        val archived = Snapshot(
            listOf(PersonEntity("p", "Anne", archived = true)),
            listOf(MedicationEntity("m", "p", archived = true), MedicationEntity("m2", "p", archived = true)),
            listOf(schedule.copy(effectiveTo = date.minusDays(1).toEpochDay()), schedule.copy(id = "s2", medicationId = "m2", name = "Vitamin", effectiveTo = date.minusDays(1).toEpochDay())))
        compose.setContent { MediTheme("Light") { Home(archived, viewModel()) } }
        compose.onNode(hasText("Kişiler") and isSelectable()).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Arşivdekiler"))
        compose.onNodeWithText("Anne").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Arşivden Çıkar"))
        compose.onNodeWithText("Arşivden Çıkar").performClick()
        val message = "Anne arşivden çıkarıldı. Arşivde 2 ilacı var, aşağıdaki \"Arşivlenen ilaçlar\" bölümünden geri alabilirsiniz."
        compose.waitUntil(5_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun satisfiedRequirementShowsNoFixButton() {
        compose.setContent { MediTheme("Light") { StatusRow("Bildirim izni", true, "İzin verildi", "İzin yok", androidx.compose.material.icons.Icons.Default.Notifications) {} } }
        compose.onNodeWithText("İzin verildi").assertExists()
        compose.onNodeWithText("Düzelt").assertDoesNotExist()
    }
    @Test fun missingRequirementExplainsAndOffersFix() {
        var fixed = false
        compose.setContent { MediTheme("Light") { StatusRow("Bildirim izni", false, "İzin verildi", "İzin yok", androidx.compose.material.icons.Icons.Default.Notifications) { fixed = true } } }
        compose.onNodeWithText("İzin yok").assertExists()
        compose.onNodeWithText("Düzelt").performClick()
        org.junit.Assert.assertTrue(fixed)
    }
    @Test fun onboardingSkipsPermissionStepsThatAreAlreadyGranted() {
        compose.setContent { MediTheme("Light") { Onboarding(viewModel()) } }
        // Android 9 needs neither runtime notification nor exact-alarm access: one step only.
        compose.onNodeWithText("MediTakip'e Hoş Geldiniz").assertIsDisplayed()
        compose.onNodeWithText("Adım", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Başla").performScrollTo().performClick()
    }
    @Test fun medicationEditorSaveIsReachableAtLargeTextSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                MediTheme("Dark") { MedicationEditor(fixture.people.single(), schedule, {}, {}) }
            }
        }
        compose.onNodeWithText("İlacı Düzenle").assertIsDisplayed()
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed()
    }
}
