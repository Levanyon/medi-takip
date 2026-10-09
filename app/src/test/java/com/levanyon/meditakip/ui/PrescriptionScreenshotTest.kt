package com.levanyon.meditakip.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.levanyon.meditakip.domain.Prescription
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],application=Application::class,qualifiers="w390dp-h844dp-mdpi")
class PrescriptionScreenshotTest(private val detail:Boolean,private val dark:Boolean,private val width:Int,private val scale:Float) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="detail={0}-dark={1}-w{2}-font{3}")
        fun cases()=listOf(false,true).flatMap { detail -> listOf(false,true).flatMap { dark ->
            listOf(390 to 1f,320 to 2f,1200 to 1f).map { (width,scale) -> arrayOf<Any>(detail,dark,width,scale) }
        } }
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private var qualifiers:String?=null
    @Before fun setup() {
        Assume.assumeTrue(System.getenv("MEDITAKIP_SCREENSHOTS")!=null)
        qualifiers=RuntimeEnvironment.getQualifiers()
        RuntimeEnvironment.setQualifiers("w${width}dp-h844dp-mdpi")
    }
    @After fun restore() { qualifiers?.let(RuntimeEnvironment::setQualifiers) }
    @Test fun prescriptionFieldsRenderAndActionsRemainReachable() {
        val data=MedicationTestData
        val rx=Prescription(data.date.toEpochDay(),data.date.plusDays(45).toEpochDay(),true,7)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
            MediTheme(if(dark) "Dark" else "Light") { Surface {
                if(detail) MedicationDetail(data.snapshot.copy(medications=data.snapshot.medications.map(rx::applyTo)),"m",data.date,{}, {_,_->},{})
                else MedicationEditor(data.person,data.schedule,{},data.stock,rx) {_,_,_->}
            } }
        } }
        if(detail) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Reçete yenileme tarihi"))
        else compose.onNodeWithText("Kaç gün önce?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reçete yenileme tarihi").assertExists()
        save("prescription")
        if(!detail) { compose.onNodeWithText("Kaydet").performScrollTo().assertIsDisplayed(); save("save") }
    }
    private fun save(part:String) {
        compose.waitForIdle()
        val view=compose.activity.window.decorView
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach {
            val density=it.layoutInfo.density.density
            assertTrue(it.size.width/density>=47.5f && it.size.height/density>=47.5f)
        }
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir=File("build/screenshots/phase7").apply { mkdirs() }
        File(dir,"detail-$detail-dark-$dark-w$width-font$scale-$part.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
    }
}
