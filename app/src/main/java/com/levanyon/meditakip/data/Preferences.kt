package com.levanyon.meditakip.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** retry defaults to on (30 min follow-up), see docs/DECISIONS.md D2.6. */
data class AppSettings(val notifications: Boolean = true, val retry: Boolean = true, val delayMinutes: Int = 30, val theme: String = "System")
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboardingCompleted", false))
    /** [startSetup]: the user chose "İlk kişiyi ekle"; Home then walks them through person → medication. */
    fun completeOnboarding(startSetup: Boolean = false) {
        prefs.edit().putBoolean("onboardingCompleted", true).putBoolean("setupPending", startSetup).apply()
        setupPending.value = startSetup; onboardingCompleted.value = true
    }
    val setupPending = MutableStateFlow(prefs.getBoolean("setupPending", false))
    fun finishSetup() { prefs.edit().putBoolean("setupPending", false).apply(); setupPending.value = false }
    var notificationAsked: Boolean
        get() = prefs.getBoolean("notificationAsked", false)
        set(value) { prefs.edit().putBoolean("notificationAsked", value).apply() }
    val state = MutableStateFlow(AppSettings(prefs.getBoolean("notifications", true), prefs.getBoolean("retry", true), prefs.getInt("delay", 30), prefs.getString("theme", "System") ?: "System"))
    fun save(value: AppSettings) {
        require(value.delayMinutes in 5..180)
        prefs.edit().putBoolean("notifications", value.notifications).putBoolean("retry", value.retry).putInt("delay", value.delayMinutes).putString("theme", value.theme).apply()
        state.value = value
    }
    /** Last person chosen on Bugün/Geçmiş; UI-only convenience, not medical data. */
    val lastPerson = MutableStateFlow(prefs.getString("lastPerson", null))
    fun rememberPerson(id: String?) { prefs.edit().putString("lastPerson", id).apply(); lastPerson.value = id }
    var nextAlarm: Long
        get() = prefs.getLong("nextAlarm", 0)
        set(value) { prefs.edit().putLong("nextAlarm", value).apply() }
    /** Medications whose low-stock notification was shown and that are still low (DECISIONS D3.8). */
    var lowStockNotified: Set<String>
        get() = prefs.getStringSet("lowStockNotified", emptySet()).orEmpty()
        set(value) { prefs.edit().putStringSet("lowStockNotified", value).apply() }
    /** "10 dk ertele": occurrence key -> epoch millis of the snoozed reminder (DECISIONS D2.5). */
    var snoozes: Map<String, Long>
        get() = prefs.getStringSet("snoozes", emptySet()).orEmpty().mapNotNull { entry ->
            val split = entry.lastIndexOf('=')
            if (split <= 0) null else entry.substring(split + 1).toLongOrNull()?.let { entry.substring(0, split) to it }
        }.toMap()
        set(value) { prefs.edit().putStringSet("snoozes", value.map { (key, at) -> "$key=$at" }.toSet()).apply() }
}
