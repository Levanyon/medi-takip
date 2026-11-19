package com.levanyon.meditakip.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.BackupSummary
import com.levanyon.meditakip.data.EncryptedBackup
import com.levanyon.meditakip.domain.*
import java.time.*

private class CreateLocalBackup : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
private class OpenLocalBackup : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@Composable fun BackupSettings(vm: AppViewModel, hasPeople: Boolean) {
    val last by vm.lastBackup.collectAsStateWithLifecycle()
    val busy by vm.fileBusy.collectAsStateWithLifecycle()
    val safety by vm.safetyCopyAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val create = rememberLauncherForActivityResult(CreateLocalBackup()) { uri -> uri?.let(vm::exportBackup) }
    val open = rememberLauncherForActivityResult(OpenLocalBackup()) { uri -> uri?.let(vm::inspectBackup) }
    var details by rememberSaveable { mutableStateOf(false) }
    BackupRows(busy, { details = true }, {
        try { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        catch (_: Exception) { vm.fileMessage.value = context.getString(R.string.backup_damaged) }
    })
    if (details) MediDialog(stringResource(R.string.backup_section), { details = false }) {
        Text(stringResource(R.string.backup_last, if (last == 0L) stringResource(R.string.backup_never) else backupDate(last)))
        Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_privacy), style = MaterialTheme.typography.bodyMedium, color = careColors.secondaryInk)
        if (BackupReminder.due(hasPeople, last, vm.backupStartedAt, System.currentTimeMillis())) {
            Text(stringResource(if (last == 0L) R.string.backup_first_due else R.string.backup_due), color = MaterialTheme.colorScheme.primary)
        }
        PrimaryAction(stringResource(R.string.backup_create), Icons.Default.Save, !busy) {
            details = false
            try { create.launch("MediTakip-${LocalDate.now()}.medibackup") }
            catch (_: Exception) { vm.fileMessage.value = context.getString(R.string.backup_save_failed) }
        }
        if (safety) SecondaryAction(stringResource(R.string.backup_safety), !busy) { details = false; vm.inspectSafetyCopy() }
        CancelAction { details = false }
    }
}

@Composable internal fun BackupRows(busy: Boolean, create: () -> Unit, restore: () -> Unit) {
    SettingsSection(stringResource(R.string.backup_section)) {
        SettingsRow(stringResource(R.string.settings_backup_create), stringResource(R.string.settings_backup_create_help), R.drawable.ic_phase5_backup, enabled = !busy, onClick = create)
        SettingsDivider()
        SettingsRow(stringResource(R.string.backup_restore), stringResource(R.string.settings_backup_restore_help), R.drawable.ic_phase5_restore, enabled = !busy, onClick = restore)
    }
}

private fun backupDate(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).let {
    "${TurkishFormat.date(it.toLocalDate())} · ${TurkishFormat.time(at)}"
}

@Composable fun BackupPreview(summary: BackupSummary, dismiss: () -> Unit, confirm: () -> Unit, legacyUnencrypted: Boolean = false) {
    RestoreConfirmationDialog(dismiss, confirm) {
        Text(stringResource(R.string.backup_restore_date, backupDate(summary.exportedAt)))
        Text(stringResource(R.string.backup_restore_counts, summary.people, summary.medications, summary.records))
        Text(if (summary.firstDate == null || summary.lastDate == null) stringResource(R.string.backup_no_records)
            else stringResource(R.string.backup_restore_range, TurkishFormat.date(summary.firstDate), TurkishFormat.date(summary.lastDate)))
        Text(stringResource(R.string.backup_restore_warning))
        if (legacyUnencrypted) Text(stringResource(R.string.backup_legacy_warning), color = MaterialTheme.colorScheme.error)
    }
}

/** Password text is never saveable/persisted; disposal drops UI references after submit/cancel. */
@Composable internal fun BackupPasswordDialog(export: Boolean, dismiss: () -> Unit, submit: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { password = ""; confirmation = "" } }
    val chars = password.toCharArray()
    val confirmationChars = confirmation.toCharArray()
    val lengthValid = EncryptedBackup.validNewPassword(chars)
    val matches = EncryptedBackup.passwordsMatch(chars, confirmationChars)
    chars.fill('\u0000'); confirmationChars.fill('\u0000')
    BackupPasswordFrame(stringResource(if (export) R.string.backup_encrypt else R.string.backup_password), dismiss) {
        if (export) Surface(color = careColors.accentSoft, shape = MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().padding(MediSpace.regular), horizontalArrangement = Arrangement.spacedBy(MediSpace.medium)) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.v3_encrypted_backup), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(stringResource(if (export) R.string.backup_password_help else R.string.backup_unlock_help))
        OutlinedTextField(password, { if (it.length <= EncryptedBackup.MAX_PASSWORD_CHARS) password = it },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.backup_password)) },
            singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = attempted && export && !lengthValid)
        if (export) OutlinedTextField(confirmation, { if (it.length <= EncryptedBackup.MAX_PASSWORD_CHARS) confirmation = it },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.backup_password_confirm)) },
            singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = attempted && !matches)
        SecondaryAction(stringResource(if (visible) R.string.backup_password_hide else R.string.backup_password_show)) { visible = !visible }
        if (attempted && export && !matches) Text(stringResource(if (!lengthValid) R.string.backup_password_length else R.string.backup_password_mismatch), color = MaterialTheme.colorScheme.error)
        if (export) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.v3_password_reminder), Modifier.fillMaxWidth().padding(MediSpace.regular),
                color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryAction(stringResource(if (export) R.string.backup_encrypt else R.string.backup_unlock), Icons.Default.Lock) {
            attempted = true
            if ((!export && password.isNotEmpty()) || (export && matches)) {
                val result = password.toCharArray()
                password = ""; confirmation = ""
                submit(result)
            }
        }
        CancelAction(dismiss)
    }
}

/** Same bounded, inset-aware frame as the Phase 5 restore dialog, including at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun BackupPasswordFrame(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 30.dp, vertical = MediSpace.regular), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large,
                border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(MediSpace.page), verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    content()
                }
            }
        }
    }
}

/** Hoisted above navigation: rotation keeps the preview; process death safely discards it. */
@Composable fun BackupFileDialogs(vm: AppViewModel) {
    val preview by vm.backupPreview.collectAsStateWithLifecycle()
    val busy by vm.fileBusy.collectAsStateWithLifecycle()
    val message by vm.fileMessage.collectAsStateWithLifecycle()
    val report by vm.reportToShare.collectAsStateWithLifecycle()
    val exportDestination by vm.exportDestination.collectAsStateWithLifecycle()
    val encryptedImport by vm.encryptedImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(report) {
        report?.let { uri ->
            vm.reportToShare.value = null
            try {
                val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newRawUri("MediTakip", uri)
                context.startActivity(Intent.createChooser(send, context.getString(R.string.report_title)))
            } catch (_: Exception) { vm.fileMessage.value = context.getString(R.string.report_failed) }
        }
    }
    when {
        busy -> MediDialog(stringResource(R.string.backup_working), {}) { CircularProgressIndicator(); Text(stringResource(R.string.backup_busy)) }
        message != null -> MediDialog(stringResource(R.string.app_name), { vm.fileMessage.value = null }) {
            Text(message!!); SecondaryAction(stringResource(R.string.ok)) { vm.fileMessage.value = null }
        }
        exportDestination != null -> BackupPasswordDialog(true, vm::cancelExport, vm::encryptAndExport)
        encryptedImport != null -> BackupPasswordDialog(false, { vm.encryptedImport.value = null }, vm::unlockBackup)
        preview != null -> BackupPreview(preview!!.summary, { vm.backupPreview.value = null }, vm::confirmRestore, preview!!.legacyUnencrypted)
    }
}
