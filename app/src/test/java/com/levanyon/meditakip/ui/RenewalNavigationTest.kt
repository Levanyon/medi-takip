package com.levanyon.meditakip.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.domain.Prescription
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=MediApp::class)
class RenewalNavigationTest {
    @get:Rule val compose=createComposeRule()
    @After fun close() = runBlocking {
        val app=RuntimeEnvironment.getApplication() as MediApp
        app.reminders.scope.coroutineContext[Job]?.cancelAndJoin(); app.database.close()
    }
    @Test fun renewalOpensMedicationDetailAndEditWithPersistedPrescription() {
        val data=MedicationTestData
        val rx=Prescription(data.date.toEpochDay(),data.date.plusDays(40).toEpochDay(),true,5)
        var request by mutableStateOf<String?>("m")
        val app=RuntimeEnvironment.getApplication() as MediApp
        compose.setContent { MediTheme("Light") {
            val vm:AppViewModel=viewModel(factory=ViewModelProvider.AndroidViewModelFactory(app))
            Home(data.snapshot.copy(medications=data.snapshot.medications.map(rx::applyTo)),vm,request) { request=null }
        } }
        compose.onNodeWithText("Test ilacı").assertExists()
        compose.runOnIdle { assertNull(request) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("İlacı Düzenle"))
        compose.onNodeWithText("İlacı Düzenle").performClick()
        compose.onNodeWithText("Reçete yenileme tarihi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kaç gün önce?").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Kaç gün önce?") and hasText("5")).assertExists()
    }
}
