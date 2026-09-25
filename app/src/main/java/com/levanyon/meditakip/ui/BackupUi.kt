package com.levanyon.meditakip.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.BackupSummary
import com.levanyon.meditakip.domain.*
import java.time.*

private class CreateLocalBackup : ActivityResultContracts.CreateDocument("application/json") {
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
    Column(verticalArrangement = Arrangement.spacedBy(MediSpace.regular)) {
        SectionHeader(stringResource(R.string.backup_section))
        Text(stringResource(R.string.backup_last, if (last == 0L) stringResource(R.string.backup_never) else backupDate(last)))
        Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (BackupReminder.due(hasPeople, last, vm.backupStartedAt, System.currentTimeMillis())) {
            Text(stringResource(if (last == 0L) R.string.backup_first_due else R.string.backup_due), color = MaterialTheme.colorScheme.primary)
        }
        PrimaryAction(stringResource(R.string.backup_create), Icons.Default.Save, !busy) {
            try { create.launch("MediTakip-${LocalDate.now()}.json") }
            catch (_: Exception) { vm.fileMessage.value = context.getString(R.string.backup_save_failed) }
        }
        SecondaryAction(stringResource(R.string.backup_restore), !busy) {
            try { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            catch (_: Exception) { vm.fileMessage.value = context.getString(R.string.backup_damaged) }
        }
        if (safety) SecondaryAction(stringResource(R.string.backup_safety), !busy, vm::inspectSafetyCopy)
    }
}

private fun backupDate(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).let {
    "${TurkishFormat.date(it.toLocalDate())} · ${TurkishFormat.time(at)}"
}

@Composable fun BackupPreview(summary: BackupSummary, dismiss: () -> Unit, confirm: () -> Unit) {
    MediDialog(stringResource(R.string.backup_restore_title), dismiss) {
        Text(stringResource(R.string.backup_restore_date, backupDate(summary.exportedAt)))
        Text(stringResource(R.string.backup_restore_counts, summary.people, summary.medications, summary.records))
        Text(if (summary.firstDate == null || summary.lastDate == null) stringResource(R.string.backup_no_records)
            else stringResource(R.string.backup_restore_range, TurkishFormat.date(summary.firstDate), TurkishFormat.date(summary.lastDate)))
        Text(stringResource(R.string.backup_restore_warning))
        PrimaryAction(stringResource(R.string.backup_restore_confirm), Icons.Default.Restore, action = confirm)
        CancelAction(dismiss)
    }
}

/** Hoisted above navigation: rotation keeps the preview; process death safely discards it. */
@Composable fun BackupFileDialogs(vm: AppViewModel) {
    val preview by vm.backupPreview.collectAsStateWithLifecycle()
    val busy by vm.fileBusy.collectAsStateWithLifecycle()
    val message by vm.fileMessage.collectAsStateWithLifecycle()
    val report by vm.reportToShare.collectAsStateWithLifecycle()
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
        preview != null -> BackupPreview(preview!!.summary, { vm.backupPreview.value = null }, vm::confirmRestore)
    }
}
