package com.levanyon.meditakip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.levanyon.meditakip.R
import com.levanyon.meditakip.data.*
import com.levanyon.meditakip.domain.*
import com.levanyon.meditakip.scanner.CameraScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** Preview is injectable so UI tests never open a device camera. */
typealias ScannerPreview = @Composable (Modifier, Boolean, (String) -> Unit, (Boolean) -> Unit, () -> Unit, () -> Unit) -> Unit

@Composable internal fun PersonMedicationActions(add: () -> Unit, scan: (() -> Unit)?) {
    if (scan == null) { PersonAddMedication(add); return }
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || largeText) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonAddMedication(add)
            ScanButton(stringResource(R.string.scan_title), R.drawable.ic_scan_barcode, secondary = true, action = scan)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { PersonAddMedication(add) }
            Box(Modifier.weight(1f)) { ScanButton(stringResource(R.string.scan_title), R.drawable.ic_scan_barcode, secondary = true, action = scan) }
        }
    }
}

@Composable
fun ScannerFlow(snapshot: Snapshot, person: PersonEntity, catalog: MedicationCatalog, access: CameraPermissionAccess,
    dismiss: () -> Unit,
    create: (MedicationScheduleEntity, StockSettings, Prescription, ScannedProduct, String, Boolean) -> Unit,
    add: (String, ScannedProduct, Double, String, Boolean) -> Unit,
    preview: ScannerPreview = { modifier, torch, decoded, flash, ready, failed -> CameraScanner(modifier, torch, decoded, flash, ready, failed) }
) {
    var raw by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var anchor by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var noSerialConfirmed by rememberSaveable { mutableStateOf(false) }
    val scan = remember(raw, anchor) { raw?.let { MedicineBarcode.parse(it, LocalDate.ofEpochDay(anchor)) } }
    var lookup by remember { mutableStateOf<CatalogLookup?>(null) }
    LaunchedEffect(scan?.gtin, catalog) { lookup = null; scan?.let { lookup = withContext(Dispatchers.IO) { catalog.lookup(it.gtin) } } }
    val gate = remember(receiptId) { ScanGate() }
    fun decode(value: String) {
        if (raw != null) return
        if (MedicineBarcode.parse(value, LocalDate.ofEpochDay(anchor)) == null) invalid = true
        else if (gate.accept()) { raw = value; invalid = false; manual = false }
    }
    fun again() {
        raw = null; lookup = null; editing = false; selected = null; amount = ""; acknowledged = false
        noSerialConfirmed = false; invalid = false; receiptId = UUID.randomUUID().toString(); anchor = LocalDate.now().toEpochDay()
    }
    val product = (lookup as? CatalogLookup.Found)?.product
    if (editing && scan != null) {
        MedicationEditor(person, null, { editing = false }, scannedName = product?.name, scannedGtin = scan.gtin) { schedule, stock, rx ->
            create(schedule, stock, rx, scan, receiptId, acknowledged)
        }
        return
    }
    FormScreen(stringResource(R.string.scan_title), dismiss, header = { ScanHeader(dismiss) }) {
        if (scan != null) {
            val duplicate = snapshot.packages.any { it.id == receiptId || (scan.serial != null && it.gtin == scan.gtin && it.serial == scan.serial) }
            val matches = snapshot.medications.filter { it.personId == person.id && it.gtin == scan.gtin }
            val expired = scan.expiry?.expired(LocalDate.now()) == true
            val confirmed = (!expired || acknowledged) && (scan.serial != null || noSerialConfirmed)
            Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(painterResource(R.drawable.ic_scan_check), null, Modifier.padding(18.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(if (lookup == null) R.string.scan_loading else if (product != null) R.string.scan_found else R.string.scan_unknown), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.scan_decoded), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MedicationInfoGroup {
                if (product != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text("Rx", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.weight(1f)) { Text(product.name, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.scan_reference), style = MaterialTheme.typography.labelMedium) }
                }
                Text(stringResource(R.string.scan_read), color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.scan_gtin, scan.gtin))
                scan.serial?.let { Text(stringResource(R.string.scan_serial, it)) }
                scan.batch?.let { Text(stringResource(R.string.scan_batch, it)) }
                ExpiryLabel(scan.expiry)
                if (product != null) Text(stringResource(R.string.scan_reference_limit), style = MaterialTheme.typography.bodySmall)
                if (lookup == CatalogLookup.Unavailable) Text(stringResource(R.string.scan_catalog_unavailable))
            }
            ScanSafety()
            if (duplicate) Text(stringResource(R.string.scan_duplicate), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            else {
                if (expired) {
                    Text(stringResource(R.string.scan_expired), color = MaterialTheme.colorScheme.error)
                    SettingToggle(stringResource(R.string.scan_expired_confirm), acknowledged) { acknowledged = it }
                }
                if (scan.serial == null) {
                    Text(stringResource(R.string.scan_ean_warning))
                    SettingToggle(stringResource(R.string.scan_ean_confirm), noSerialConfirmed) { noSerialConfirmed = it }
                }
                if (matches.isEmpty()) ScanButton(stringResource(if (product != null) R.string.scan_add else R.string.scan_manual_continue), enabled = confirmed && lookup != null) { editing = true }
                else {
                    Text(stringResource(R.string.scan_existing))
                    matches.forEach { m ->
                        val name = MedicationViews.latestRevision(snapshot, m.id)?.name ?: m.id
                        if (m.archived) Text("$name · ${stringResource(R.string.scan_existing_disabled)}")
                        else SettingToggle(name, selected == m.id) { selected = if (it) m.id else null; amount = "" }
                    }
                    matches.find { it.id == selected && !it.archived }?.let { m ->
                        if (m.stockEnabled) {
                            NumberField(amount, { amount = it }, stringResource(R.string.scan_package_quantity))
                            Text(stringResource(R.string.scan_package_quantity_help))
                        } else Text(stringResource(R.string.scan_stock_off))
                        val quantity = if (m.stockEnabled) StockPlan.parse(amount) else 0.0
                        ScanButton(stringResource(R.string.scan_package_add), enabled = confirmed && quantity != null && quantity.isFinite() && quantity >= 0) {
                            add(m.id, scan, quantity!!, receiptId, acknowledged)
                        }
                    }
                }
            }
            ScanButton(stringResource(R.string.scan_again), R.drawable.ic_scan_barcode, secondary = true, action = ::again)
        } else if (manual) {
            OutlinedTextField(input, { input = it.take(256) }, label = { Text(stringResource(R.string.scan_code)) }, modifier = Modifier.fillMaxWidth())
            if (invalid) Text(stringResource(R.string.scan_invalid), color = MaterialTheme.colorScheme.error)
            ScanButton(stringResource(R.string.scan_lookup), enabled = input.isNotBlank()) { decode(input) }
            QuietAction(stringResource(R.string.scan_again)) { manual = false; invalid = false }
        } else if (access.state != CameraAccess.READY) {
            Spacer(Modifier.height(64.dp))
            Surface(shape = RoundedCornerShape(24.dp), border = mediSurfaceBorder(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Icon(painterResource(R.drawable.ic_scan_camera), null, Modifier.padding(22.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                    Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(stringResource(when(access.state) { CameraAccess.UNAVAILABLE -> R.string.scan_unavailable; CameraAccess.DENIED, CameraAccess.SETTINGS -> R.string.scan_denied; else -> R.string.scan_permission_body }), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.scan_no_recording), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    if (access.state != CameraAccess.UNAVAILABLE) ScanButton(stringResource(if (access.state == CameraAccess.SETTINGS) R.string.scan_settings else R.string.scan_enable), R.drawable.ic_scan_camera, enabled = !access.busy, fullWidth = false, action = access.request)
                }
            }
            QuietAction(stringResource(R.string.scan_manual_want)) { manual = true }
        } else {
            Text(stringResource(R.string.scan_frame), color = MaterialTheme.colorScheme.onSurfaceVariant)
            var torch by remember { mutableStateOf(false) }
            var flash by remember { mutableStateOf(false) }
            var ready by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            val previewHeight = 470.dp + 180.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
            Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0B120F))) {
                if (!failed) preview(Modifier.matchParentSize(), torch, ::decode, { flash = it }, { ready = true }, { failed = true })
                Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(68.dp))
                    Box(Modifier.align(Alignment.Start).width(230.dp).height(2.dp).background(Color(0xCC8FD6BD)))
                    Spacer(Modifier.height(28.dp))
                    Icon(painterResource(R.drawable.ic_scan_corners), null, Modifier.widthIn(max = 270.dp).fillMaxWidth().height(180.dp), tint = Color.Unspecified)
                    Spacer(Modifier.height(30.dp))
                    Text(stringResource(if (failed) R.string.scan_unavailable else if (!ready) R.string.scan_starting else R.string.scan_distance), color = Color(0xFFB8C7BF), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    IconButton(onClick = { torch = !torch }, enabled = ready && flash && !failed, modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = .1f), CircleShape)) {
                        Icon(painterResource(R.drawable.ic_scan_flash), stringResource(if (flash) R.string.scan_flash else R.string.scan_no_flash), Modifier.size(22.dp), tint = Color.White)
                    }
                }
            }
            if (invalid) Text(stringResource(R.string.scan_invalid), color = MaterialTheme.colorScheme.error)
            ScanButton(stringResource(R.string.scan_manual), secondary = true, grouped = true) { manual = true }
            Text(stringResource(R.string.scan_camera_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun ScanHeader(back: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(back, Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))) {
            Icon(painterResource(R.drawable.ic_scan_back), stringResource(R.string.back), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    }
}
@Composable internal fun ScanButton(label: String, icon: Int? = null, secondary: Boolean = false, enabled: Boolean = true, grouped: Boolean = false, fullWidth: Boolean = true, action: () -> Unit) {
    Button(action, (if (fullWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = if (grouped) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface)
            else if (secondary) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, textAlign = TextAlign.Center)
    }
}
@Composable internal fun ScanSafety() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.scan_safety), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.scan_safety_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun ExpiryLabel(expiry: PackageExpiry?) {
    Text(if (expiry == null) stringResource(R.string.scan_expiry_missing)
        else stringResource(if (expiry.day == null) R.string.scan_expiry_month else R.string.scan_expiry_day, expiry.toString()))
}
