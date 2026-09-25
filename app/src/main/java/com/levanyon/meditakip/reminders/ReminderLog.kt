package com.levanyon.meditakip.reminders

import java.io.File
import java.time.Instant

/**
 * Small local record of reminder problems (AUDIT R3), so failures are never silently swallowed.
 * Keeps the last [MAX] entries in a private app file. Contains no medication or person data and is
 * never sent anywhere; the Hatırlatma Durumu screen shows the latest entry.
 */
class ReminderLog(private val file: File) {
    data class Entry(val at: Long, val place: String, val message: String)

    @Synchronized fun record(place: String, error: Throwable) = record(place, "${error::class.java.simpleName}: ${error.message.orEmpty()}")

    @Synchronized fun record(place: String, message: String) {
        val line = "${Instant.now().toEpochMilli()}\t${place.clean()}\t${message.clean().take(300)}"
        val kept = (read() + line).takeLast(MAX)
        runCatching { file.parentFile?.mkdirs(); file.writeText(kept.joinToString("\n") + "\n") }
        android.util.Log.w("MediTakip", "$place: $message")
    }

    @Synchronized fun entries(): List<Entry> = read().mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        parts.getOrNull(0)?.toLongOrNull()?.let { Entry(it, parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" }) }
    }

    fun latest(): Entry? = entries().lastOrNull()

    @Synchronized fun clear() { runCatching { file.delete() } }

    private fun read(): List<String> = runCatching { if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList() }.getOrDefault(emptyList())
    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')

    companion object { const val MAX = 50 }
}
