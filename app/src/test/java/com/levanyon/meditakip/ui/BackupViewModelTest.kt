package com.levanyon.meditakip.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.levanyon.meditakip.MediApp
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.Snapshot
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = MediApp::class)
class BackupViewModelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication() as MediApp
    private lateinit var vm: AppViewModel
    private val password get() = "portable test password".toCharArray()
    @Before fun setup() {
        vm = AppViewModel(app)
        compose.setContent { MediTheme("Light") { BackupFileDialogs(vm) } }
        runBlocking { app.repository.replaceAll(Snapshot(people = listOf(PersonEntity("p", "Private name")))) }
    }
    @After fun close() = runBlocking { app.reminders.scope.coroutineContext[Job]?.cancelAndJoin(); app.database.close() }
    private fun awaitFile() = compose.waitUntil(30_000) {
        // IO completions post back to Robolectric's paused Android main looper.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        !vm.fileBusy.value
    }

    @Test fun realExportWritesEncryptedDocumentAndErasesSubmittedPassword() {
        val file = File(app.cacheDir, "new-backup.json")
        val pass = password
        compose.runOnIdle { vm.exportBackup(Uri.fromFile(file)); vm.encryptAndExport(pass) }
        awaitFile()
        assertTrue(pass.all { it == '\u0000' }); assertNull(vm.exportDestination.value)
        assertTrue(app.backups.open(file.inputStream()) is BackupImport.Encrypted)
        assertFalse(file.readText().contains("Private name"))
    }
    @Test fun encryptedImportRequiresPasswordAndDoesNotMutateBeforeConfirmation() {
        val bytes = runBlocking { app.backups.encryptedExport(password) }
        val file = File(app.cacheDir, "import.json").apply { writeBytes(bytes) }
        val before = runBlocking { app.repository.snapshot() }
        compose.runOnIdle { vm.inspectBackup(Uri.fromFile(file)) }; awaitFile()
        assertNotNull(vm.encryptedImport.value); assertNull(vm.backupPreview.value)
        val pass = password
        compose.runOnIdle { vm.unlockBackup(pass) }; awaitFile()
        assertTrue(pass.all { it == '\u0000' }); assertNull(vm.encryptedImport.value)
        assertFalse(vm.backupPreview.value!!.legacyUnencrypted)
        assertEquals(before, runBlocking { app.repository.snapshot() })
        compose.runOnIdle { vm.backupPreview.value = null }
    }
    @Test fun wrongPasswordUsesGenericMessageClearsPasswordAndNeverCreatesPreview() {
        val file = File(app.cacheDir, "wrong.json").apply { writeBytes(runBlocking { app.backups.encryptedExport(password) }) }
        val before = runBlocking { app.repository.snapshot() }
        compose.runOnIdle { vm.inspectBackup(Uri.fromFile(file)) }; awaitFile()
        val wrong = "another password".toCharArray()
        compose.runOnIdle { vm.unlockBackup(wrong) }; awaitFile()
        assertTrue(wrong.all { it == '\u0000' }); assertNull(vm.backupPreview.value)
        assertEquals("Yedek açılamadı. Parola yanlış veya dosya bozuk olabilir.", vm.fileMessage.value)
        assertEquals(before, runBlocking { app.repository.snapshot() })
    }
    @Test fun failedDestinationDoesNotAdvanceBackupTimestampAndClearsPassword() {
        app.preferences.lastBackupAt = 123L
        val directory = File(app.cacheDir, "directory-target").apply { mkdirs() }
        val pass = password
        compose.runOnIdle { vm.exportBackup(Uri.fromFile(directory)); vm.encryptAndExport(pass) }; awaitFile()
        assertTrue(pass.all { it == '\u0000' }); assertEquals(123L, app.preferences.lastBackupAt)
        assertNotNull(vm.fileMessage.value)
    }
    @Test fun cancelledExportClearsDestinationWithoutWritingABackup() {
        val file = File(app.cacheDir, "cancelled-backup.json").apply { writeText("incomplete destination") }
        app.preferences.lastBackupAt = 123L
        compose.runOnIdle { vm.exportBackup(Uri.fromFile(file)); vm.cancelExport() }
        compose.waitUntil(30_000) { !file.exists() || file.length() == 0L }
        assertNull(vm.exportDestination.value)
        assertTrue(!file.exists() || file.length() == 0L)
        assertEquals(123L, app.preferences.lastBackupAt)
    }
}
