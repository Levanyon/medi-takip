package com.levanyon.meditakip.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w390dp-h844dp-mdpi")
class ScannerFlowTest {
    @get:Rule val compose = createComposeRule()
    private val person = PersonEntity("p", "Anne")
    private val gtin = "08699580090635"
    private val code = "01${gtin}1729123121SER1"
    private var permission by mutableStateOf(CameraAccess.RATIONALE)
    private var state by mutableStateOf(Snapshot(people = listOf(person)))
    private var catalog: MedicationCatalog = MedicationCatalog { CatalogLookup.Found(CatalogProduct(gtin, "Katalog ürün adı")) }
    private var decoded: ((String) -> Unit)? = null
    private var requests = 0
    private var closed = false
    private var saved: MedicationScheduleEntity? = null
    private var added: Pair<String, Double>? = null
    private var previewDisposals = 0
    private fun content(): @Composable () -> Unit = {
        MediTheme("Light") { Surface {
            ScannerFlow(state, person, catalog, CameraPermissionAccess(permission, false) { requests++ }, { closed = true },
                create = { s, _, _, _, _, _ -> saved = s }, add = { id, _, amount, _, _ -> added = id to amount },
                preview = { modifier, _, decode, flash, ready, _ ->
                    Box(modifier)
                    SideEffect { decoded = decode; flash(false); ready() }
                    DisposableEffect(Unit) { onDispose { previewDisposals++ } }
                })
        } }
    }
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun awaitResult() { compose.waitUntil(5000) { compose.onAllNodesWithText("İlaç bulundu").fetchSemanticsNodes().isNotEmpty() } }
    private fun scan(value: String = code) { permission = CameraAccess.READY; compose.setContent(content()); compose.runOnIdle { decoded!!(value) }; awaitResult() }
    @Test fun permissionRationaleRequiresExplicitActionAndManualWorksWithoutPermission() {
        compose.setContent(content()); assertEquals(0, requests)
        compose.onNodeWithText("Kameraya erişim gerekli").assertExists()
        click("Kamerayı etkinleştir"); assertEquals(1, requests)
        click("Barkodu elle girmek istiyorum")
        compose.onNodeWithText("Barkod / karekod içeriği").performTextInput(code)
        click("Kodu kontrol et"); awaitResult(); assertEquals(1, requests)
    }
    @Test fun deniedSettingsAndNoHardwareKeepManualRecovery() {
        permission = CameraAccess.DENIED; compose.setContent(content())
        click("Kamerayı etkinleştir"); assertEquals(1, requests)
        compose.runOnIdle { permission = CameraAccess.SETTINGS }
        click("Kamera iznini ayarlardan aç"); assertEquals(2, requests)
        compose.runOnIdle { permission = CameraAccess.UNAVAILABLE }
        compose.onNodeWithText("Kamera kullanılamıyor. Barkodu elle girebilirsin.").assertExists()
        click("Barkodu elle girmek istiyorum")
    }
    @Test fun resultDebouncesFramesDisposesCameraAndPrefillsOnlyName() {
        permission = CameraAccess.READY; compose.setContent(content())
        compose.runOnIdle { val frame = decoded!!; repeat(20) { frame(code) } }
        awaitResult(); assertEquals(1, previewDisposals)
        click("İlacı ekle")
        compose.onNodeWithText("Katalog ürün adı").assertExists()
        compose.onNodeWithText("Doz").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithText("Kaydet").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Doz").performScrollTo().performTextInput("1 tablet")
        click("Doz, saatler ve tedavi tarihlerini kontrol ettim"); click("Kaydet")
        assertEquals("1 tablet", saved?.dose); assertEquals("08:00", saved?.times); assertNull(saved?.end)
    }
    @Test fun unknownCatalogNeverInventsName() {
        catalog = MedicationCatalog { CatalogLookup.Unknown }; permission = CameraAccess.READY; compose.setContent(content())
        compose.runOnIdle { decoded!!(code) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("İlaç bulunamadı").fetchSemanticsNodes().isNotEmpty() }
        click("Elle devam et")
        compose.onNodeWithText("İlaç Adı").assertExists(); compose.onNodeWithText("Katalog ürün adı").assertDoesNotExist()
    }
    @Test fun invalidContentStaysInScannerAndNoFlashIsDisabled() {
        permission = CameraAccess.READY; compose.setContent(content())
        compose.runOnIdle { decoded!!("https://example.com") }
        compose.onNodeWithContentDescription("Fener desteklenmiyor").assertIsNotEnabled()
        compose.onNodeWithText("Geçerli bir ürün barkodu okunamadı. Kodu kontrol et veya elle devam et.").assertExists()
        assertNull(saved); assertNull(added)
    }
    @Test fun samePackageCannotContinue() {
        state = state.copy(packages = listOf(MedicationPackageEntity("a", "m", gtin, "SER1", null, null, 1, 5.0)))
        scan(); compose.onNodeWithText("Bu fiziksel paket daha önce kaydedilmiş. Stok tekrar eklenmez.").assertExists()
        compose.onNodeWithText("İlacı ekle").assertDoesNotExist()
    }
    @Test fun multipleMatchesRequireExplicitChoiceAndConfirmedQuantity() {
        val d = LocalDate.now().toEpochDay()
        state = state.copy(medications = listOf(MedicationEntity("a", "p", stockEnabled = true, gtin = gtin), MedicationEntity("b", "p", stockEnabled = true, gtin = gtin)),
            schedules = listOf("a", "b").map { MedicationScheduleEntity(it, it, "Anne", "Tedavi $it", "1", "", "08:00", 127, d, null, true, d) })
        scan(); compose.onNodeWithText("İlacı ekle").assertDoesNotExist(); compose.onNodeWithText("Paketi kaydet").assertDoesNotExist()
        click("Tedavi b"); compose.onNodeWithText("Paketi kaydet").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Eklenecek birim miktarı").performScrollTo().performTextInput("12,5")
        click("Paketi kaydet"); assertEquals("b" to 12.5, added)
    }
    @Test fun eanAndExpiredNeedSeparateAcknowledgements() {
        scan("01${gtin}17200101")
        compose.onNodeWithText("İlacı ekle").performScrollTo().assertIsNotEnabled()
        click("Son kullanma tarihi uyarısını gördüm")
        compose.onNodeWithText("İlacı ekle").performScrollTo().assertIsNotEnabled()
        click("Bu kutuyu daha önce eklemediğimi kontrol ettim")
        compose.onNodeWithText("İlacı ekle").performScrollTo().assertIsEnabled()
    }
    @Test fun resultSurvivesRecreationWithoutReopeningCamera() {
        permission = CameraAccess.READY
        val restoration = StateRestorationTester(compose); restoration.setContent(content())
        compose.runOnIdle { decoded!!(code) }; awaitResult()
        restoration.emulateSavedInstanceStateRestore(); awaitResult()
        compose.onNodeWithText("Seri: SER1").assertExists()
        assertEquals(1, previewDisposals)
    }
    @Test fun personDetailOffersScanOnlyForActivePerson() {
        var scans = 0
        compose.setContent { MediTheme("Light") { Surface { PersonDetail(state, person, {}, {}, {}, {}, scan = { scans++ }) } } }
        click("İlaç tara"); assertEquals(1, scans)
    }
    @Test fun backLeavesWithoutSaving() {
        scan(); compose.onNodeWithContentDescription("Geri").performClick()
        assertTrue(closed); assertNull(saved); assertNull(added)
    }
}
