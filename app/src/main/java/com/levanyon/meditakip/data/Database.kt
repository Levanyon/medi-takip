package com.levanyon.meditakip.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "people")
data class PersonEntity(@PrimaryKey val id: String, val name: String, val note: String = "", val color: Int = 0, val archived: Boolean = false)

@Entity(tableName = "medications", foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"])], indices = [Index("personId")])
data class MedicationEntity(@PrimaryKey val id: String, val personId: String, val archived: Boolean = false)

@Entity(tableName = "schedules", foreignKeys = [ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"], childColumns = ["medicationId"])], indices = [Index("medicationId")])
data class MedicationScheduleEntity(
    @PrimaryKey val id: String, val medicationId: String, val personName: String,
    val name: String, val dose: String, val note: String, val times: String, val days: Int,
    val start: Long, val end: Long?, val active: Boolean,
    val effectiveFrom: Long, val effectiveTo: Long? = null
)

@Entity(tableName = "logs", foreignKeys = [ForeignKey(entity = MedicationScheduleEntity::class, parentColumns = ["id"], childColumns = ["scheduleId"])], indices = [Index("scheduleId"), Index(value = ["scheduleId", "date", "minute"], unique = true)])
data class MedicationLogEntity(@PrimaryKey val key: String, val scheduleId: String, val date: Long, val minute: Int, val takenAt: Long)

@Entity(tableName = "dispatches")
data class DispatchEntity(@PrimaryKey val key: String, val sentAt: Long)

@Dao
interface AppDao {
    @Query("SELECT * FROM people") fun observePeople(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM medications") fun observeMedications(): Flow<List<MedicationEntity>>
    // ORDER BY rowid = insertion order; revisions are never re-inserted (@Upsert updates in place),
    // so the last revision in the list is the newest one (see MedicationViews.latestRevision).
    @Query("SELECT * FROM schedules ORDER BY rowid") fun observeSchedules(): Flow<List<MedicationScheduleEntity>>
    @Query("SELECT * FROM logs") fun observeLogs(): Flow<List<MedicationLogEntity>>
    @Query("SELECT * FROM people") suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM medications") suspend fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM schedules ORDER BY rowid") suspend fun schedules(): List<MedicationScheduleEntity>
    @Query("SELECT * FROM logs") suspend fun logs(): List<MedicationLogEntity>
    @Upsert suspend fun savePerson(person: PersonEntity)
    @Upsert suspend fun saveMedication(medication: MedicationEntity)
    @Upsert suspend fun saveSchedule(schedule: MedicationScheduleEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLog(log: MedicationLogEntity)
    @Query("DELETE FROM logs WHERE `key` = :key") suspend fun undo(key: String)
    @Query("SELECT * FROM dispatches") suspend fun dispatches(): List<DispatchEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun dispatched(value: DispatchEntity)
}

@Database(entities = [PersonEntity::class, MedicationEntity::class, MedicationScheduleEntity::class, MedicationLogEntity::class, DispatchEntity::class], version = 1, exportSchema = true)
abstract class MediDatabase : RoomDatabase() { abstract fun dao(): AppDao }
