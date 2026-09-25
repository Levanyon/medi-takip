package com.levanyon.meditakip.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.domain.*

data class PermissionState(val notifications: Boolean, val exact: Boolean, val notificationAction: PermissionAction, val exactAction: PermissionAction, val previouslyAsked: Boolean)
data class PermissionAccess(val state: PermissionState, val busy: Boolean, val notifications: () -> Unit, val exact: () -> Unit)

@Composable fun rememberPermissionAccess(vm: AppViewModel): PermissionAccess {
    val context = LocalContext.current
    val app = context.applicationContext as MediApp
    val owner = LocalLifecycleOwner.current
    fun read(): PermissionState {
        val runtime = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val allowed = app.reminders.notificationAllowed()
        val exact = app.reminders.exactAllowed()
        return PermissionState(allowed, exact, PermissionPolicy.notifications(Build.VERSION.SDK_INT, runtime, allowed, app.preferences.notificationAsked), PermissionPolicy.exact(Build.VERSION.SDK_INT, exact), app.preferences.notificationAsked)
    }
    var state by remember { mutableStateOf(read()) }
    var busy by remember { mutableStateOf(false) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { busy = false; state = read(); app.reminders.refresh() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { busy = false; state = read(); app.reminders.refresh() }
    DisposableEffect(owner, app) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { state = read(); app.reminders.refresh() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) {
        if (busy) return
        try { busy = true; settings.launch(intent) } catch (_: Exception) { busy = false; vm.error.value = context.getString(R.string.error_settings) }
    }
    return PermissionAccess(state, busy, notifications = {
        val fresh = read(); state = fresh
        if (!busy) when (fresh.notificationAction) {
            PermissionAction.NONE -> Unit
            PermissionAction.REQUEST -> if (Build.VERSION.SDK_INT >= 33) {
                app.preferences.notificationAsked = true
                try { busy = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                catch (_: Exception) { busy = false; state = read(); vm.error.value = context.getString(R.string.error_settings) }
            }
            PermissionAction.SETTINGS -> open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }, exact = {
        val fresh = read(); state = fresh
        if (fresh.exactAction != PermissionAction.NONE && Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    })
}
