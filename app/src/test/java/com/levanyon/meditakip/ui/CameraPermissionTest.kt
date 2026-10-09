package com.levanyon.meditakip.ui

import android.Manifest
import android.app.Application
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.levanyon.meditakip.domain.CameraAccess
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CameraPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication()
    private fun show() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        compose.setContent { MediTheme("Light") {
            val access = rememberCameraPermission()
            Text(access.state.name)
            ScanButton("request", action = access.request)
        } }
    }
    @Test fun requestsCameraOnlyAfterRationaleActionThenRecoversViaSettings() {
        show()
        compose.onNodeWithText(CameraAccess.RATIONALE.name).assertExists()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        compose.onNodeWithText("request").performClick()
        val requested = shadowOf(compose.activity).lastRequestedPermission
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA), requested.requestedPermissions)
        compose.runOnIdle { compose.activity.onRequestPermissionsResult(requested.requestCode, requested.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED)) }
        compose.onNodeWithText(CameraAccess.SETTINGS.name).assertExists()
        compose.onNodeWithText("request").performClick()
        val launched = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, launched.action)
        assertEquals("package:${app.packageName}", launched.data.toString())
        compose.runOnIdle {
            shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
            shadowOf(compose.activity).receiveResult(launched, Activity.RESULT_OK, Intent())
        }
        compose.onNodeWithText(CameraAccess.READY.name).assertExists()
    }
    @Test fun noCameraNeverRequestsPermission() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        compose.setContent { val access = rememberCameraPermission(); Text(access.state.name); ScanButton("request", action = access.request) }
        compose.onNodeWithText(CameraAccess.UNAVAILABLE.name).assertExists()
        compose.onNodeWithText("request").performClick()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }
}
