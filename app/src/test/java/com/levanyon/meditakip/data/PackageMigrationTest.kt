package com.levanyon.meditakip.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PackageMigrationTest {
    @Test fun productionV3EveryColumnAndPreferencesPreserved() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "phase8-migration.db"
        context.deleteDatabase(name)
        val preferences = Preferences(context)
        preferences.save(AppSettings(theme = "Dark", retry = false)); preferences.completeOnboarding(true)
        preferences.nextAlarm = 123L; preferences.snoozes = mapOf("s:20000:480" to 123L)
        val prefs = context.getSharedPreferences("settings", 0).all.toMap()
        val schema = JSONObject(File("schemas/com.levanyon.meditakip.data.MediDatabase/3.json").readText()).getJSONObject("database")
        val old = context.openOrCreateDatabase(name, 0, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
            old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.getJSONArray("indices")
            for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
        old.execSQL("INSERT INTO people VALUES ('p','Anne','Not',2,0),('a','Dede','',0,1)")
        old.execSQL("INSERT INTO medications VALUES ('m','p',0,1,12.5,0.5,3,20000,20100,1,7),('n','p',0,0,0,1,0,NULL,NULL,0,0),('a','a',1,1,4,1,2,NULL,20100,1,2)")
        old.execSQL("INSERT INTO schedules VALUES ('s','m','Anne','İlaç','1 tablet','','08:00',127,20000,20100,1,20000,NULL,1),('n','n','Anne','Vitamin','1 damla','','09:00',127,20000,NULL,1,20000,NULL,2),('a','a','Dede','Eski','1 tablet','','10:00',127,20000,NULL,0,20000,20010,3)")
        old.execSQL("INSERT INTO logs VALUES ('s:20000:480','s',20000,480,1728000000000,'TAKEN',1728000001000,NULL),('n:20000:540','n',20000,540,1728000000000,'SKIPPED',1728000001000,'Dışarıdaydık')")
        old.execSQL("INSERT INTO dispatches VALUES ('renewal:m:20100:7',1728000000000)")
        val tables = listOf("people", "medications", "schedules", "logs", "dispatches")
        val before = tables.associateWith { table -> old.rawQuery("SELECT * FROM $table ORDER BY rowid", null).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if(c.isNull(it)) null else c.getString(it) }) }
        } }
        old.version = 3; old.close()
        val room = Room.databaseBuilder(context, MediDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.readableDatabase
            assertEquals(4, db.version)
            before.forEach { (table, expected) ->
                val actual = db.query("SELECT * FROM $table ORDER BY rowid").use { c -> buildList {
                    while(c.moveToNext()) add((0 until expected.first().size).map { if(c.isNull(it)) null else c.getString(it) })
                } }
                assertEquals(table, expected, actual)
            }
            assertTrue(room.dao().packages().isEmpty()); assertTrue(room.dao().medications().all { it.gtin == null })
            assertEquals(prefs, context.getSharedPreferences("settings", 0).all)
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
