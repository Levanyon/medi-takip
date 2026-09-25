package com.levanyon.meditakip.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "people")
data class PersonEntity(@PrimaryKey val id: String, val name: String, val note: String = "", val color: Int = 0, val archived: Boolean = false)

/**
 * A medication's identity and current state. Stock (v2, optional) is current state, so it lives here
 * and is never versioned with schedule revisions (docs/DECISIONS.md D3.7).
 */
@Entity(tableName = "medications", foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"])], indices = [Index("personId")])
data class MedicationEntity(
    @PrimaryKey val id: String, val personId: String, val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val stockEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val stockQuantity: Double = 0.0,
    @ColumnInfo(defaultValue = "1") val stockPerDose: Double = 1.0,
    @ColumnInfo(defaultValue = "0") val stockThreshold: Double = 0.0,
)

/** A schedule revision. [sequence] orders revisions of the database permanently (v2, AUDIT R12). */
@Entity(tableName = "schedules", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medicationId"])], indices = [Index("medicationId")])
data class MedicationScheduleEntity(
    @PrimaryKey val id: String, val medicationId: String, val personName: String,
    val name: String, val dose: String, val note: String, val times: String, val days: Int,
    val start: Long, val end: Long?, val active: Boolean,
    val effectiveFrom: Long, val effectiveTo: Long? = null,
    @ColumnInfo(defaultValue = "0") val sequence: Long = 0,
)

enum class LogStatus { TAKEN, SKIPPED }

/**
 * One recorded dose. [takenAt] is the actual time taken (for SKIPPED: the moment of skipping, never
 * shown); [recordedAt] is when the entry was made, so late marking is distinguishable (v2).
 */
@Entity(tableName = "logs", foreignKeys = [ForeignKey(entity = MedicationScheduleEntity::class, parentColumns = ["id"], childColumns = ["scheduleId"])], indices = [Index("scheduleId"), Index(value = ["scheduleId", "date", "minute"], unique = true)])
data class MedicationLogEntity(
    @PrimaryKey val key: String, val scheduleId: String, val date: Long, val minute: Int, val takenAt: Long,
    @ColumnInfo(defaultValue = "TAKEN") val status: LogStatus = LogStatus.TAKEN,
    @ColumnInfo(defaultValue = "0") val recordedAt: Long = takenAt,
    val note: String? = null,
)

@Entity(tableName = "dispatches")
data class DispatchEntity(@PrimaryKey val key: String, val sentAt: Long)

@Dao
interface AppDao {
    @Query("SELECT * FROM people") fun observePeople(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM medications") fun observeMedications(): Flow<List<MedicationEntity>>
    // Revision order is data (sequence, v2); rowid only breaks ties for rows written before v2 existed.
    @Query("SELECT * FROM schedules ORDER BY sequence, rowid") fun observeSchedules(): Flow<List<MedicationScheduleEntity>>
    @Query("SELECT * FROM logs") fun observeLogs(): Flow<List<MedicationLogEntity>>
    @Query("SELECT * FROM people") suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM medications") suspend fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM schedules ORDER BY sequence, rowid") suspend fun schedules(): List<MedicationScheduleEntity>
    @Query("SELECT * FROM logs") suspend fun logs(): List<MedicationLogEntity>
    @Query("SELECT * FROM logs WHERE `key` = :key") suspend fun log(key: String): MedicationLogEntity?
    @Query("SELECT COALESCE(MAX(sequence), 0) FROM schedules") suspend fun maxSequence(): Long
    @Upsert suspend fun savePerson(person: PersonEntity)
    @Upsert suspend fun saveMedication(medication: MedicationEntity)
    @Upsert suspend fun saveSchedule(schedule: MedicationScheduleEntity)
    /** Returns -1 when a record for this dose already exists (nothing inserted). */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLog(log: MedicationLogEntity): Long
    @Update suspend fun updateLog(log: MedicationLogEntity)
    @Query("DELETE FROM logs WHERE `key` = :key") suspend fun undo(key: String)
    @Query("UPDATE medications SET stockQuantity = stockQuantity + :delta WHERE id = :id AND stockEnabled = 1") suspend fun adjustStock(id: String, delta: Double)
    @Query("SELECT * FROM dispatches") suspend fun dispatches(): List<DispatchEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun dispatched(value: DispatchEntity)
    // AUDIT R10: dispatch records only prevent duplicate delivery within 24 h; older ones are pruned.
    @Query("DELETE FROM dispatches WHERE sentAt < :cutoff") suspend fun pruneDispatches(cutoff: Long)
    // Restore from a backup (Phase 4): replace everything inside one transaction (Repository.replaceAll).
    @Query("DELETE FROM logs") suspend fun clearLogs()
    @Query("DELETE FROM schedules") suspend fun clearSchedules()
    @Query("DELETE FROM medications") suspend fun clearMedications()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM dispatches") suspend fun clearDispatches()
    @Insert suspend fun insertPeople(values: List<PersonEntity>)
    @Insert suspend fun insertMedications(values: List<MedicationEntity>)
    @Insert suspend fun insertSchedules(values: List<MedicationScheduleEntity>)
    @Insert suspend fun insertLogs(values: List<MedicationLogEntity>)
}

@Database(entities = [PersonEntity::class, MedicationEntity::class, MedicationScheduleEntity::class, MedicationLogEntity::class, DispatchEntity::class], version = 2, exportSchema = true)
abstract class MediDatabase : RoomDatabase() { abstract fun dao(): AppDao }

/**
 * v1 → v2 (docs/DECISIONS.md D3.1). Only adds columns; existing rows and values are never rewritten
 * except to fill the new columns from data that already exists (recordedAt ← takenAt, sequence ← rowid).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `logs` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'TAKEN'")
        db.execSQL("ALTER TABLE `logs` ADD COLUMN `recordedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `logs` ADD COLUMN `note` TEXT")
        db.execSQL("UPDATE `logs` SET `recordedAt` = `takenAt`")
        db.execSQL("ALTER TABLE `schedules` ADD COLUMN `sequence` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `schedules` SET `sequence` = `rowid`")
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `stockEnabled` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `stockQuantity` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `stockPerDose` REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `stockThreshold` REAL NOT NULL DEFAULT 0")
    }
}

/** Every migration, in order. The database builder must never fall back to a destructive reset. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
