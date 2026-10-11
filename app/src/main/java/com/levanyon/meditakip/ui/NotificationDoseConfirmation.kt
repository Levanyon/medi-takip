package com.levanyon.meditakip.ui

import android.app.KeyguardManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.levanyon.meditakip.domain.*

/** Navigation is read-only. Only an explicit unlocked confirmation calls the existing dose flow. */
@Composable internal fun NotificationDoseConfirmation(snapshot: Snapshot, key: String, dismiss: () -> Unit, taken: (Occurrence) -> Unit) {
    val keyguard = LocalContext.current.getSystemService(KeyguardManager::class.java)
    val item = NextDose.resolve(snapshot, key)
    MediDialog("İçildi", dismiss) {
        if (item == null) {
            Text("Bu doz artık işlem için uygun değil.")
        } else {
            Text(item.schedule.name)
            Text("${item.personName} • ${TurkishFormat.shortDate(item.date)} • ${item.time}")
            PrimaryAction("İçildi olarak kaydet") {
                // Recheck at the actual click, including if the device locked after navigation.
                if (!keyguard.isDeviceLocked && !keyguard.isKeyguardLocked) {
                    NextDose.resolve(snapshot, key)?.let(taken)
                    dismiss()
                }
            }
        }
        CancelAction(dismiss)
    }
}
