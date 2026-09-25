package com.levanyon.meditakip.data

import com.levanyon.meditakip.domain.Schedule
import com.levanyon.meditakip.domain.Snapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate

/** What a restore would bring in; shown to the user before anything is replaced (DECISIONS D4.3). */
data class BackupSummary(val exportedAt: Long, val people: Int, val medications: Int, val records: Int, val firstDate: LocalDate?, val lastDate: LocalDate?)

/** A validated backup, ready to replace all data. */
data class BackupContent(val exportedAt: Long, val snapshot: Snapshot, val settings: AppSettings) {
    val summary get() = BackupSummary(exportedAt, snapshot.people.size, snapshot.medications.size, snapshot.logs.size,
        snapshot.logs.minOfOrNull { it.date }?.let(LocalDate::ofEpochDay), snapshot.logs.maxOfOrNull { it.date }?.let(LocalDate::ofEpochDay))
}

enum class BackupProblem { NOT_A_BACKUP, TOO_NEW, TOO_LARGE, DAMAGED }
/** [detail] is technical (English, for logs and tests); the UI shows a Turkish message per [problem]. */
class BackupException(val problem: BackupProblem, val detail: String) : Exception("$problem: $detail")

/**
 * Versioned JSON backup (DECISIONS D4.1/D4.2) using the platform's org.json — no library.
 * Values are stored exactly as in the database; device-local runtime state is not part of a backup.
 */
object BackupCodec {
    const val FORMAT = "meditakip-backup"
    const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 2
    const val MAX_BYTES = 10 * 1024 * 1024

    fun encode(snapshot: Snapshot, settings: AppSettings, exportedAt: Long, appVersion: String): String = JSONObject().apply {
        put("format", FORMAT); put("formatVersion", FORMAT_VERSION); put("schemaVersion", SCHEMA_VERSION)
        put("exportedAt", exportedAt); put("appVersion", appVersion)
        put("people", JSONArray(snapshot.people.map { p -> JSONObject().put("id", p.id).put("name", p.name).put("note", p.note).put("color", p.color).put("archived", p.archived) }))
        put("medications", JSONArray(snapshot.medications.map { m ->
            JSONObject().put("id", m.id).put("personId", m.personId).put("archived", m.archived).put("stockEnabled", m.stockEnabled)
                .put("stockQuantity", m.stockQuantity).put("stockPerDose", m.stockPerDose).put("stockThreshold", m.stockThreshold)
        }))
        put("schedules", JSONArray(snapshot.schedules.map { s ->
            JSONObject().put("id", s.id).put("medicationId", s.medicationId).put("personName", s.personName).put("name", s.name).put("dose", s.dose).put("note", s.note)
                .put("times", s.times).put("days", s.days).put("start", s.start).put("end", s.end ?: JSONObject.NULL).put("active", s.active)
                .put("effectiveFrom", s.effectiveFrom).put("effectiveTo", s.effectiveTo ?: JSONObject.NULL).put("sequence", s.sequence)
        }))
        put("logs", JSONArray(snapshot.logs.map { l ->
            JSONObject().put("key", l.key).put("scheduleId", l.scheduleId).put("date", l.date).put("minute", l.minute).put("takenAt", l.takenAt)
                .put("status", l.status.name).put("recordedAt", l.recordedAt).put("note", l.note ?: JSONObject.NULL)
        }))
        put("settings", JSONObject().put("notifications", settings.notifications).put("retry", settings.retry).put("delayMinutes", settings.delayMinutes).put("theme", settings.theme))
    }.toString(2)

    /** Parses and validates; throws [BackupException] without side effects on any problem. */
    fun decode(text: String): BackupContent {
        if (text.length > MAX_BYTES) throw BackupException(BackupProblem.TOO_LARGE, "${text.length} characters")
        val root = try { JSONObject(text) } catch (e: JSONException) { throw BackupException(BackupProblem.NOT_A_BACKUP, "not JSON") }
        if (root.optString("format") != FORMAT) throw BackupException(BackupProblem.NOT_A_BACKUP, "format=${root.optString("format")}")
        val version = root.optInt("formatVersion", -1)
        if (version > FORMAT_VERSION) throw BackupException(BackupProblem.TOO_NEW, "formatVersion=$version")
        if (version < 1) throw BackupException(BackupProblem.DAMAGED, "formatVersion missing")
        return try { read(root) } catch (e: BackupException) { throw e } catch (e: Exception) { throw BackupException(BackupProblem.DAMAGED, e.message ?: e::class.java.simpleName) }
    }

    private fun read(root: JSONObject): BackupContent {
        fun damaged(detail: String): Nothing = throw BackupException(BackupProblem.DAMAGED, detail)
        fun JSONObject.text(key: String) = getString(key)
        fun JSONObject.nullableLong(key: String) = if (isNull(key)) null else getLong(key)
        fun JSONObject.nullableText(key: String) = if (!has(key) || isNull(key)) null else getString(key)
        fun array(key: String) = root.optJSONArray(key) ?: damaged("$key missing")
        fun <T> JSONArray.mapObjects(block: (JSONObject) -> T) = (0 until length()).map { block(getJSONObject(it)) }

        val people = array("people").mapObjects { PersonEntity(it.text("id"), it.text("name"), it.optString("note"), it.optInt("color"), it.getBoolean("archived")) }
        val medications = array("medications").mapObjects {
            MedicationEntity(it.text("id"), it.text("personId"), it.getBoolean("archived"), it.optBoolean("stockEnabled", false),
                it.optDouble("stockQuantity", 0.0), it.optDouble("stockPerDose", 1.0), it.optDouble("stockThreshold", 0.0))
        }
        val schedules = array("schedules").mapObjects {
            MedicationScheduleEntity(it.text("id"), it.text("medicationId"), it.optString("personName"), it.text("name"), it.text("dose"), it.optString("note"),
                it.text("times"), it.getInt("days"), it.getLong("start"), it.nullableLong("end"), it.getBoolean("active"),
                it.getLong("effectiveFrom"), it.nullableLong("effectiveTo"), it.getLong("sequence"))
        }
        val logs = array("logs").mapObjects {
            val status = runCatching { LogStatus.valueOf(it.optString("status", "TAKEN")) }.getOrElse { _ -> damaged("unknown status") }
            val takenAt = it.getLong("takenAt")
            MedicationLogEntity(it.text("key"), it.text("scheduleId"), it.getLong("date"), it.getInt("minute"), takenAt, status, it.optLong("recordedAt", takenAt), it.nullableText("note"))
        }
        val s = root.optJSONObject("settings")
        val settings = if (s == null) AppSettings() else AppSettings(s.optBoolean("notifications", true), s.optBoolean("retry", true),
            s.optInt("delayMinutes", 30).takeIf { it in 5..180 } ?: 30, s.optString("theme", "System").takeIf { it in setOf("System", "Light", "Dark") } ?: "System")

        // Structure and references.
        fun unique(name: String, ids: List<String>) { if (ids.any { it.isBlank() }) damaged("$name: blank id"); if (ids.toSet().size != ids.size) damaged("$name: duplicate id") }
        unique("people", people.map { it.id }); unique("medications", medications.map { it.id }); unique("schedules", schedules.map { it.id }); unique("logs", logs.map { it.key })
        if (people.any { it.name.isBlank() }) damaged("person without name")
        val personIds = people.map { it.id }.toSet(); val medicationIds = medications.map { it.id }.toSet(); val scheduleIds = schedules.map { it.id }.toSet()
        medications.firstOrNull { it.personId !in personIds }?.let { damaged("medication ${it.id} has no person") }
        schedules.firstOrNull { it.medicationId !in medicationIds }?.let { damaged("schedule ${it.id} has no medication") }
        logs.firstOrNull { it.scheduleId !in scheduleIds }?.let { damaged("record ${it.key} has no schedule") }
        logs.firstOrNull { it.key != "${it.scheduleId}:${it.date}:${it.minute}" }?.let { damaged("record key ${it.key} does not match its dose") }
        // Plausible values.
        val validDays = LocalDate.of(2000, 1, 1).toEpochDay()..LocalDate.of(2200, 1, 1).toEpochDay()
        schedules.forEach { r ->
            if (r.name.isBlank() || r.dose.isBlank()) damaged("schedule ${r.id}: name or dose empty")
            if (r.days !in 1..127) damaged("schedule ${r.id}: days")
            if (r.start !in validDays || r.effectiveFrom !in validDays || (r.end != null && r.end !in validDays) || (r.effectiveTo != null && r.effectiveTo !in (validDays.first - 1)..validDays.last)) damaged("schedule ${r.id}: date out of range")
            if (r.end != null && r.end < r.start) damaged("schedule ${r.id}: end before start")
            if (r.effectiveTo != null && r.effectiveTo < r.effectiveFrom - 1) damaged("schedule ${r.id}: invalid validity interval")
            if (runCatching { Schedule.minutes(r.times) }.getOrNull().isNullOrEmpty()) damaged("schedule ${r.id}: times")
        }
        schedules.groupBy { it.medicationId }.forEach { (id, list) -> if (list.map { it.sequence }.toSet().size != list.size) damaged("medication $id: revision order is not unique") }
        logs.forEach { l -> if (l.date !in validDays || l.minute !in 0..1439) damaged("record ${l.key}: date or minute") }
        medications.forEach { m ->
            if (!m.stockQuantity.isFinite() || !m.stockPerDose.isFinite() || !m.stockThreshold.isFinite()) damaged("medication ${m.id}: stock is not a number")
            if (m.stockEnabled && (m.stockPerDose <= 0 || m.stockThreshold < 0)) damaged("medication ${m.id}: stock settings")
        }
        return BackupContent(root.optLong("exportedAt", 0), Snapshot(people, medications, schedules, logs), settings)
    }
}

/**
 * Export, inspect and restore (DECISIONS D4.2/D4.3). Restoring first keeps a private safety copy of the
 * current data, then replaces everything in one transaction, then resets device-local runtime state.
 */
class Backups(private val repository: Repository, private val preferences: Preferences, private val filesDir: File, private val appVersion: String, private val clock: Clock = Clock.systemUTC()) {
    val safetyCopy get() = File(filesDir, "before-restore.json")

    suspend fun export(output: OutputStream) {
        val text = BackupCodec.encode(repository.snapshot(), preferences.state.value, clock.millis(), appVersion)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        preferences.lastBackupAt = clock.millis()
    }

    fun inspect(input: InputStream): BackupContent {
        val bytes = input.use { it.readNBytesCompat(BackupCodec.MAX_BYTES + 1) }
        if (bytes.size > BackupCodec.MAX_BYTES) throw BackupException(BackupProblem.TOO_LARGE, "${bytes.size} bytes")
        return BackupCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    suspend fun restore(content: BackupContent) {
        safetyCopy.writeText(BackupCodec.encode(repository.snapshot(), preferences.state.value, clock.millis(), appVersion), Charsets.UTF_8)
        repository.replaceAll(content.snapshot)
        preferences.save(content.settings)
        preferences.snoozes = emptyMap()
        preferences.lowStockNotified = emptySet()
        preferences.nextAlarm = 0
        if (content.snapshot.people.none { it.id == preferences.lastPerson.value && !it.archived }) preferences.rememberPerson(null)
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < limit) { val n = read(buffer, 0, minOf(buffer.size, limit - out.size())); if (n < 0) break; out.write(buffer, 0, n) }
        return out.toByteArray()
    }
}
