package com.levanyon.meditakip

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.levanyon.meditakip.domain.TurkishFormat
import com.levanyon.meditakip.ui.*
import androidx.compose.ui.graphics.luminance
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(TurkishFormat.locale) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    override fun onResume() { super.onResume(); (application as MediApp).reminders.refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val snapshot by vm.snapshot.collectAsStateWithLifecycle()
            val error by vm.error.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val onboardingCompleted by vm.onboardingCompleted.collectAsStateWithLifecycle()
            MediTheme(settings.theme) {
                val background = MaterialTheme.colorScheme.background
                SideEffect {
                    val bars = WindowCompat.getInsetsController(window, window.decorView)
                    bars.isAppearanceLightStatusBars = background.luminance() > 0.5f
                    bars.isAppearanceLightNavigationBars = background.luminance() > 0.5f
                }
                Surface { if (onboardingCompleted) Home(snapshot, vm) else MediContent { Onboarding(vm) } }
                error?.let { message ->
                    MediDialog(stringResource(R.string.error_title), { vm.error.value = null }) {
                        Text(message)
                        SecondaryAction(stringResource(R.string.ok)) { vm.error.value = null }
                    }
                }
            }
        }
    }
}
