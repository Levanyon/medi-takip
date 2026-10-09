package com.levanyon.meditakip.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.levanyon.meditakip.domain.*

data class CameraPermissionAccess(val state: CameraAccess, val busy: Boolean, val request: () -> Unit)
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Composed only after the explicit scan action; never part of onboarding or startup. */
@Composable fun rememberCameraPermission(): CameraPermissionAccess {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preferences = remember { context.getSharedPreferences("scanner", Context.MODE_PRIVATE) }
    fun read() = CameraPolicy.state(
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        preferences.getBoolean("asked", false), context.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true)
    var state by remember { mutableStateOf(read()) }
    var busy by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { busy = false; state = read() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { busy = false; state = read() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { state = read(); busy = false } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return CameraPermissionAccess(state, busy) {
        if (!busy) {
            state = read()
            try {
                when (state) {
                    CameraAccess.RATIONALE, CameraAccess.DENIED -> {
                        preferences.edit().putBoolean("asked", true).apply()
                        busy = true; permission.launch(Manifest.permission.CAMERA)
                    }
                    CameraAccess.SETTINGS -> {
                        busy = true
                        settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                    else -> Unit
                }
            } catch (_: Exception) { busy = false; state = CameraAccess.UNAVAILABLE }
        }
    }
}
