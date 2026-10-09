package com.levanyon.meditakip.ui

import android.app.Application
import android.app.DatePickerDialog
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
class PrescriptionUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=MedicationTestData
    private var saved:Prescription?=null
    private fun show(initial:Prescription=Prescription(),add:Boolean=false,dark:Boolean=false,scale:Float=1f) {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            MediTheme(if(dark) "Dark" else "Light") { Surface {
                MedicationEditor(data.person,if(add) null else data.schedule,{},initialPrescription=initial) { _,_,rx -> saved=rx }
            } }
        } }
    }
    private fun click(label:String)=compose.onNodeWithText(label).performScrollTo().performClick()
    private fun date(label:String,value:LocalDate) {
        click(label)
        compose.runOnIdle {
            val dialog=ShadowDialog.getLatestDialog() as DatePickerDialog
            dialog.updateDate(value.year,value.monthValue-1,value.dayOfMonth)
            dialog.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick()
        }
    }
    @Test fun addCanSavePrescriptionAndRenewalUsingRealDatePickers() {
        show(add=true)
        compose.onNodeWithText("İlaç Adı").performScrollTo().performTextInput("İlaç")
        compose.onNodeWithText("Doz").performScrollTo().performTextInput("1 tablet")
        val written=LocalDate.now().minusDays(2); val renewal=written.plusDays(45)
        date("Reçete yazılma tarihi",written); date("Reçete yenileme tarihi",renewal)
        click("Reçete yenilemesini hatırlat")
        compose.onNodeWithText("Kaç gün önce?").performScrollTo().performTextReplacement("7")
        click("Kaydet")
        assertEquals(Prescription(written.toEpochDay(),renewal.toEpochDay(),true,7),saved)
    }
    @Test fun editLoadsUpdatesAndClearsOptionalDates() {
        val d=LocalDate.now()
        show(Prescription(d.toEpochDay(),d.plusDays(30).toEpochDay(),true,3))
        compose.onNodeWithText(TurkishFormat.date(d.plusDays(30))).performScrollTo().assertIsDisplayed()
        date("Reçete yenileme tarihi",d.plusDays(60)); click("Kaydet")
        assertEquals(d.plusDays(60).toEpochDay(),saved?.renewal)
        click("Reçete tarihini kaldır"); click("Yenileme tarihini kaldır"); click("Kaydet")
        assertEquals(Prescription(leadDays=3),saved)
    }
    @Test fun renewalOnlyIsAllowedAndInvalidOrderingOrLeadDoesNotSave() {
        val d=LocalDate.now()
        show(Prescription(renewal=d.toEpochDay(),enabled=true))
        click("Kaydet"); assertEquals(d.toEpochDay(),saved?.renewal); saved=null
        date("Reçete yazılma tarihi",d.plusDays(1)); click("Kaydet"); assertNull(saved)
        click("Reçete tarihini kaldır")
        compose.onNodeWithText("Kaç gün önce?").performScrollTo().performTextReplacement("-1")
        click("Kaydet"); assertNull(saved)
    }
    @Test @Config(qualifiers="w320dp-h800dp-mdpi")
    fun narrowDarkLargeFontKeepsPrescriptionControlsAccessible() {
        val d=LocalDate.now().toEpochDay()
        show(Prescription(d,d+30,true,7),dark=true,scale=2f)
        listOf("Reçete yazılma tarihi","Reçete yenileme tarihi","Reçete tarihini kaldır","Yenileme tarihini kaldır","Reçete yenilemesini hatırlat","Kaydet").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        click("Kaydet"); assertEquals(Prescription(d,d+30,true,7),saved)
    }
    @Test fun disablingReminderDoesNotLeaveAnInvalidHiddenLeadField() {
        val d=LocalDate.now().toEpochDay()
        show(Prescription(renewal=d,enabled=true))
        compose.onNodeWithText("Kaç gün önce?").performScrollTo().performTextReplacement("-1")
        click("Reçete yenilemesini hatırlat"); click("Kaydet")
        assertEquals(Prescription(renewal=d),saved)
    }
    @Test fun detailShowsStoredDatesAndOmitsEmptyPrescription() {
        val d=data.date.toEpochDay()
        var rx by mutableStateOf(Prescription(d-2,d+30))
        compose.setContent { MediTheme("Dark") { Surface {
            MedicationDetail(data.snapshot.copy(medications=data.snapshot.medications.map(rx::applyTo)),"m",data.date,{}, {_,_->},{})
        } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Reçete yazılma tarihi"))
        compose.onNodeWithText(TurkishFormat.date(data.date.minusDays(2))).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Reçete yenileme tarihi"))
        compose.onNodeWithText(TurkishFormat.date(data.date.plusDays(30))).assertExists()
        compose.runOnIdle { rx=Prescription() }
        compose.onNodeWithText("Reçete bilgileri").assertDoesNotExist()
    }
}
