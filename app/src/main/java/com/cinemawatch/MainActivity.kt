package com.cinemawatch

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemawatch.data.*
import com.cinemawatch.domain.InspectionPolicy
import com.cinemawatch.domain.SignalGroup
import com.cinemawatch.domain.SignalGrouping
import com.cinemawatch.radio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CinemaTheme { CinemaScreen(application as CinemaApp) } }
    }
}

private val Ink = Color(0xFF0A1421)
private val Surface = Color(0xFF142334)
private val Mint = Color(0xFF66E0CF)
private val Muted = Color(0xFF99AFBF)

@Composable private fun CinemaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, onPrimary = Ink, background = Ink,
        surface = Surface, surfaceVariant = Color(0xFF203247), onSurface = Color(0xFFE7F0F4),
        onSurfaceVariant = Muted, error = Color(0xFFFFB69E)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CinemaScreen(app: CinemaApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = app.repository.dao
    val cinemas by remember(dao) { dao.cinemas() }.collectAsStateWithLifecycle(emptyList())
    val zones by remember(dao) { dao.zones() }.collectAsStateWithLifecycle(emptyList())
    val assets by remember(dao) { dao.assets() }.collectAsStateWithLifecycle(emptyList())
    val sessions by remember(dao) { dao.sessions() }.collectAsStateWithLifecycle(emptyList())
    val results by remember(dao) { dao.results() }.collectAsStateWithLifecycle(emptyList())
    val bindings by remember(dao) { dao.bindings() }.collectAsStateWithLifecycle(emptyList())
    val groups by remember(dao) { dao.groups() }.collectAsStateWithLifecycle(emptyList())
    val scan by app.scanner.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val preferences = remember(context) { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var zoneId by rememberSaveable { mutableStateOf(preferences.getString("zone", "").orEmpty()) }
    LaunchedEffect(zoneId) { preferences.edit().putString("zone", zoneId).apply() }
    var reportId by rememberSaveable { mutableStateOf<String?>(null) }
    var zoneReports by rememberSaveable { mutableStateOf(false) }
    var collapseEmpty by rememberSaveable { mutableStateOf(true) }
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var signalSearch by remember { mutableStateOf("") }
    var createAsset by remember { mutableStateOf(false) }
    var bindAsset by remember { mutableStateOf<CinemaAsset?>(null) }
    var renameAsset by remember { mutableStateOf<CinemaAsset?>(null) }
    var assetSaving by remember { mutableStateOf(false) }
    var cinemaSaving by remember { mutableStateOf(false) }
    var bulkHalls by remember { mutableStateOf(false) }
    var logic by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val zone = zones.find { it.id == zoneId } ?: zones.firstOrNull()
    val cinema = cinemas.find { it.id == zone?.cinemaId }
    val commonZoneNames = listOf(R.string.zone_lobby, R.string.zone_corridor, R.string.zone_office, R.string.zone_projection).map { stringResource(it) }
    var createCinema by remember { mutableStateOf(false) }
    var createZone by remember { mutableStateOf(false) }
    var chooseZone by remember { mutableStateOf(false) }
    var setupMode by remember { mutableStateOf<String?>(null) }
    var register by remember { mutableStateOf<LiveRadio?>(null) }
    var delete by remember { mutableStateOf<CinemaAsset?>(null) }
    var hunt by remember { mutableStateOf<CinemaAsset?>(null) }
    var reset by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var request by remember { mutableStateOf<SampleRequest?>(null) }
    val busy = scan.running || scan.saving || scan.saveFailed
    val snackbar = remember { SnackbarHostState() }
    val exportCsv = remember(sessions, zones, cinemas, results, assets, groups, context) { ReportExport.csv(sessions, zones, cinemas, results, assets, context, groups) }
    LaunchedEffect(scan.running) { if (!scan.running) { register = null; signalSearch = "" } }
    fun assetError(t: Throwable) { error = when (t) { is InvalidRadioAddressException -> R.string.invalid_address; is DuplicateRadioException -> R.string.duplicate_asset; else -> R.string.error_save } }

    fun launchSample(r: SampleRequest) {
        if (r.demo) { scope.launch { app.scanner.start(r) }; return }
        if (!ScanPermissions.granted(context)) { error = R.string.permissions_denied; return }
        if (context.getSystemService(LocationManager::class.java)?.isLocationEnabled != true) { error = R.string.location_required; return }
        val intent = Intent(context, InspectionService::class.java).putExtra("zone", r.zoneId).putExtra("mode", r.mode)
            .putExtra("seconds", r.seconds).putExtra("planned", r.planned ?: -1).putExtra("actual", r.actual ?: -1)
            .putExtra("gate", r.gate ?: -1).putExtra("point", r.point)
        runCatching { ContextCompat.startForegroundService(context, intent) }.onFailure { error = R.string.scanner_failed }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        request?.let(::launchSample); request = null
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(exportCsv.toByteArray(Charsets.UTF_8)) } ?: error("No output") } }
                .onFailure { error = R.string.error_save }
        }
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing && !busy) scope.launch {
            importing = true
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        HistoryImport.read(stream)
                    } ?: error("No input")
                }
                app.repository.importHistory(text)
            }.onSuccess { snackbar.showSnackbar(context.getString(R.string.import_done, it)) }.onFailure { error = R.string.import_failed }
            importing = false
        }
    }
    sessions.find { it.id == reportId }?.let { session ->
        val reportZone = zones.find { it.id == session.zoneId }
        ReportPreview(session, cinemas.find { it.id == reportZone?.cinemaId }?.name.orEmpty(), reportZone?.name.orEmpty(), results, assets, groups) { reportId = null }
        return
    }
    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                val labels = listOf(R.string.dashboard, R.string.assets, R.string.reports, R.string.settings)
                val icons = listOf(Icons.Outlined.Radar, Icons.Outlined.Inventory2, Icons.Outlined.Assignment, Icons.Outlined.Tune)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(icons[index], stringResource(label)) }, label = { Text(stringResource(label)) })
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).testTag("main-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Theaters, null, tint = Mint, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("CinemaWatch", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); Text(BuildConfig.VERSION_NAME, color = Muted, fontSize = 13.sp)
                }
                Text(stringResource(R.string.camera_subtitle), color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
            if (zones.isEmpty()) item {
                Panel {
                    Icon(Icons.Outlined.AddBusiness, null, tint = Mint, modifier = Modifier.size(38.dp))
                    Text(stringResource(R.string.setup_first), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { createCinema = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup)) }
                }
            } else item {
                OutlinedButton(onClick = { chooseZone = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.LocationOn, null); Spacer(Modifier.width(8.dp))
                    Text("${cinema?.name.orEmpty()} · ${zone?.name.orEmpty()}", modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
            }
            when (tab) {
                0 -> {
                    item {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (scan.running) Mint else Muted, RoundedCornerShape(4.dp)))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(if (scan.saving) R.string.saving else if (scan.running) R.string.running else R.string.idle), color = Mint)
                                Spacer(Modifier.weight(1f))
                                if (scan.running) Text("${scan.remaining / 60}:${(scan.remaining % 60).toString().padStart(2, '0')}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            if (scan.request?.demo == true) Text(stringResource(R.string.demo_note), color = Color(0xFFFFD48D))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Stat(scan.wifiCount, R.string.wifi_aps, Modifier.weight(1f))
                                Stat(scan.bleCount, R.string.ble_radios, Modifier.weight(1f))
                            }
                            Text(stringResource(R.string.authorized_assets) + "  ${scan.assetCount}", color = Muted)
                            if (scan.running) {
                                LinearProgressIndicator(progress = { 1f - scan.remaining.toFloat() / (scan.request?.seconds ?: 120) }, modifier = Modifier.fillMaxWidth(), color = Mint)
                                Text(stringResource(if (scan.wifiHealthy) R.string.scan_ready else R.string.wifi_waiting), color = Muted, fontSize = 12.sp)
                                Text(stringResource(if (scan.bleHealthy) R.string.scan_ready else R.string.ble_waiting), color = Muted, fontSize = 12.sp)
                                Button(onClick = { app.scanner.stop() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop)) }
                            } else if (scan.saveFailed) {
                                Text(stringResource(R.string.error_save), color = MaterialTheme.colorScheme.error)
                                Button(onClick = { app.scanner.retrySave() }) { Text(stringResource(R.string.retry_save)) }
                            } else {
                                Button(onClick = { setupMode = "INSPECTION" }, enabled = zone != null && !busy, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.PlayArrow, null); Text(stringResource(R.string.start_inspection))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { setupMode = "LAB" }, enabled = zone != null && !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.start_lab)) }
                                    TextButton(onClick = { zone?.let { launchSample(SampleRequest(it.id, seconds = 15, demo = true)) } }, enabled = zone != null && !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.demo)) }
                                }
                            }
                            if (scan.dropped > 0) Text(stringResource(R.string.capacity_note), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    item { Text(stringResource(R.string.rf_note), color = Muted, fontSize = 13.sp) }
                    item { SectionTitle(R.string.new_radios); Text(stringResource(R.string.transient_note), color = Muted, fontSize = 12.sp) }
                    if (scan.live.isEmpty()) item { EmptyCard(R.string.empty_radios, Icons.Outlined.Sensors) }
                    item {
                        Text(stringResource(R.string.signal_group_note), color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { groupFilter = null }) { Text(stringResource(R.string.all_groups)) }
                            OutlinedButton(onClick = { collapseEmpty = !collapseEmpty }) { Text(stringResource(if (collapseEmpty) R.string.show_empty else R.string.collapse_empty)) }
                        }
                        Text(stringResource(R.string.group_overview, scan.live.size,
                            scan.live.count { SignalGroup.UNKNOWN in it.categories },
                            scan.live.count { radio -> radio.categories.count { it in SignalGrouping.deviceTypes && it != SignalGroup.UNKNOWN } > 1 }), color = Muted, fontSize = 12.sp)
                        Text(stringResource(R.string.group_multi_note), color = Muted, fontSize = 12.sp)
                        listOf(R.string.device_classes to SignalGrouping.deviceTypes, R.string.ecosystem_classes to SignalGrouping.ecosystems).forEach { (title, categories) ->
                            SectionTitle(title)
                            categories.forEach { category ->
                                val members = scan.live.filter { category in it.categories }
                                if (!collapseEmpty || members.isNotEmpty()) {
                                    OutlinedButton(onClick = { groupFilter = if (groupFilter == category.name) null else category.name }, modifier = Modifier.fillMaxWidth().testTag("group-${category.name}")) {
                                        Column(Modifier.weight(1f)) {
                                            Text((if (groupFilter == category.name) "▾ " else "▸ ") + stringResource(groupLabel(category)))
                                            val rules = members.flatMap { it.signatureHits }.filter { SignalGrouping.fromSignature(it.category) == category }.map { it.name }.distinct()
                                            if (rules.isNotEmpty()) Text(rules.take(3).joinToString(" · "), color = Muted, fontSize = 11.sp)
                                        }
                                        Text(members.size.toString())
                                    }
                                }
                            }
                        }
                        OutlinedTextField(signalSearch, { signalSearch = it.take(80) }, label = { Text(stringResource(R.string.signal_search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    val visibleRadios = scan.live.filter { (groupFilter == null || it.categories.any { category -> category.name == groupFilter }) && (signalSearch.isBlank() || it.name.contains(signalSearch, true) || it.vendor.orEmpty().contains(signalSearch, true)) }
                    if (scan.live.isNotEmpty() && visibleRadios.isEmpty()) item { Text(stringResource(R.string.no_matching_signals)) }
                    items(visibleRadios, key = { it.key }) { radio ->
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (radio.kind == "WIFI") Icons.Outlined.Wifi else Icons.Outlined.Bluetooth, null, tint = Mint)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(assets.find { it.id == radio.assetId }?.name ?: radio.name.ifBlank { stringResource(R.string.unknown_radio) }, fontWeight = FontWeight.SemiBold)
                                    Text("${radio.kind} · …${radio.address.takeLast(5)}", color = Muted, fontSize = 12.sp)
                                    radio.vendor?.let { Text(it, color = Muted, fontSize = 12.sp) }
                                }
                                Text("${radio.rssi} dBm", color = Mint)
                            }
                            Text(radio.categories.joinToString(" · ") { context.getString(groupLabel(it)) }, color = Mint)
                            if (radio.signatureHits.isNotEmpty()) Text(stringResource(R.string.match_rules, radio.signatureHits.map { it.name }.distinct().joinToString(" · ")), color = Muted, fontSize = 12.sp)
                            Text(stringResource(evidenceLabel(radio.guess.evidence)), color = Muted, fontSize = 12.sp)
                            Text(stringResource(when (radio.guess.confidence) { "MEDIUM" -> R.string.confidence_medium; "LOW" -> R.string.confidence_low; else -> R.string.confidence_unknown }), color = Muted, fontSize = 12.sp)
                            Text(stringResource(R.string.signal_age, ((System.currentTimeMillis() - radio.lastAt) / 1000).toInt().coerceAtLeast(0)), color = Muted, fontSize = 12.sp)
                            if (radio.assetId == null && scan.running && scan.request?.demo != true) TextButton(onClick = { register = radio }) { Text(stringResource(R.string.register)) }
                            else if (radio.assetId != null) Text(stringResource(R.string.registered_signal), color = Mint)
                        }
                    }
                }
                1 -> {
                    item {
                        SectionTitle(R.string.authorized_assets)
                        Text(stringResource(R.string.baseline_note), color = Muted, fontSize = 13.sp)
                        Button(onClick = { createAsset = true }, enabled = zone != null && !assetSaving && !scan.saving && !scan.saveFailed, modifier = Modifier.testTag("create-asset")) { Text(stringResource(R.string.create_asset)) }
                        Text(stringResource(R.string.asset_help), color = Muted, fontSize = 13.sp)
                    }
                    val currentAssets = assets.filter { it.zoneId == zone?.id }
                    if (currentAssets.isEmpty()) item { EmptyCard(R.string.no_assets, Icons.Outlined.Inventory2); Text(stringResource(R.string.asset_help), color = Muted, fontSize = 13.sp) }
                    items(currentAssets, key = { it.id }) { asset ->
                        val last = results.firstOrNull { it.assetId == asset.id }
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(asset.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                StatusChip(if (bindings.none { it.assetId == asset.id }) "UNBOUND" else last?.status ?: "PENDING")
                            }
                            Text(last?.medianRssi?.let { "$it dBm" } ?: stringResource(R.string.no_signal), color = Muted)
                            bindings.filter { it.assetId == asset.id }.forEach { b -> Text("${b.radio} · …${b.address.takeLast(5)}", color = Muted, fontSize = 12.sp) }
                            TextButton(onClick = { renameAsset = asset }, enabled = !busy) { Text(stringResource(R.string.rename_asset)) }
                            Row {
                                TextButton(onClick = { hunt = asset }, enabled = scan.running && bindings.any { it.assetId == asset.id }) { Text(stringResource(R.string.hunt)) }
                                TextButton(onClick = { bindAsset = asset }, enabled = !assetSaving && !scan.saving && !scan.saveFailed) { Text(stringResource(R.string.bind_signal)) }
                                Spacer(Modifier.weight(1f)); TextButton(onClick = { delete = asset }, enabled = !busy) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        SectionTitle(R.string.reports)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!zoneReports, { zoneReports = false }, label = { Text(stringResource(R.string.all_records)) })
                            FilterChip(zoneReports, { zoneReports = true }, label = { Text(stringResource(R.string.zone_records)) })
                        }
                        if (sessions.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { export.launch("CinemaWatch-${System.currentTimeMillis()}.csv") }) { Icon(Icons.Outlined.Download, null); Text(stringResource(R.string.export)) }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        val file = withContext(Dispatchers.IO) {
                                            File(context.cacheDir, "reports/inspection.csv").apply { parentFile?.mkdirs(); writeText(exportCsv) }
                                        }
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), context.getString(R.string.share)))
                                    }.onFailure { error = R.string.error_save }
                                }
                            }) { Text(stringResource(R.string.share)) }
                        }
                    }
                    if (sessions.isEmpty()) item { EmptyCard(R.string.no_reports, Icons.Outlined.Assignment) }
                    items(sessions.filter { !zoneReports || it.zoneId == zone?.id }, key = { it.id }) { session ->
                        val z = zones.find { it.id == session.zoneId }
                        Panel(onClick = { reportId = session.id }) {
                            Row {
                                Text("${cinemas.find { it.id == z?.cinemaId }?.name.orEmpty()} · ${z?.name.orEmpty()}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(stringResource(if (session.mode == "LAB") R.string.lab_mode else R.string.inspection_mode), color = Mint, fontSize = 12.sp)
                            }
                            Text(SimpleDateFormat("yyyy/MM/dd HH:mm", context.resources.configuration.locales[0]).format(Date(session.startMs)) + " · ${session.durationSeconds}s", color = Muted, fontSize = 12.sp)
                            OutlinedButton(onClick = { reportId = session.id }, modifier = Modifier.testTag("preview-${session.id}")) { Text(stringResource(R.string.preview_report)) }
                            if (session.imported) Text(stringResource(R.string.imported_record), color = Muted)
                            if (session.overran()) Text(stringResource(R.string.late_sample), color = MaterialTheme.colorScheme.error)
                            if (session.demo) Text(stringResource(R.string.demo_note), color = Color(0xFFFFD48D))
                            Text("Wi-Fi AP  ${session.wifiCount}    BLE  ${session.bleCount}", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.calibration_pending), color = Muted)
                            Text(stringResource(if (session.wifiHealthy && session.bleHealthy && !session.overran()) R.string.healthy_sample else R.string.partial_sample), color = Muted, fontSize = 12.sp)
                            results.filter { it.sessionId == session.id }.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(assets.find { it.id == r.assetId }?.name.orEmpty(), modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    StatusChip(r.status)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    item { UpdateCard(app, busy) }
                    item {
                        Panel {
                            SectionTitle(R.string.language)
                            val selectedLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                            listOf("" to R.string.follow_system, "zh-Hans" to R.string.language_simplified,
                                "zh-Hant" to R.string.language_traditional, "en" to R.string.language_english).forEach { (tag, label) ->
                                TextButton(onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }) {
                                    RadioButton(selected = selectedLanguage == tag, onClick = null)
                                    Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    item {
                        Panel {
                            SectionTitle(R.string.privacy_title)
                            Text(stringResource(R.string.privacy_description), color = Muted)
                            Text(stringResource(R.string.permissions_note), color = Muted, fontSize = 13.sp)
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.app_settings)) }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.location_settings)) }
                        }
                    }
                    item {
                        Panel {
                            if (cinemas.isEmpty()) TextButton(onClick = { createCinema = true }) { Text(stringResource(R.string.setup)) }
                            else TextButton(onClick = { createZone = true }, enabled = !busy) { Text(stringResource(R.string.add_zone)) }
                            TextButton(onClick = { createCinema = true }, enabled = !busy) { Text(stringResource(R.string.setup)) }
                            if (cinema != null) TextButton(onClick = { bulkHalls = true }, enabled = !busy && !cinemaSaving) { Text(stringResource(R.string.batch_halls)) }
                            if (cinema != null) TextButton(onClick = {
                                val selectedCinema = cinema
                                if (selectedCinema != null && !cinemaSaving) scope.launch {
                                    cinemaSaving = true
                                    runCatching { app.repository.addCommonZones(selectedCinema.id, commonZoneNames) }.onFailure { error = R.string.error_save }
                                    cinemaSaving = false
                                }
                            }, enabled = !busy && !cinemaSaving) { Text(stringResource(R.string.add_common_zones)) }
                            TextButton(onClick = { logic = true }) { Text(stringResource(R.string.signal_logic)) }
                            TextButton(onClick = { importCsv.launch(arrayOf("*/*")) }, enabled = !busy && !importing) { Text(stringResource(R.string.import_history)) }
                            Text(stringResource(R.string.import_note), color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { license = true }) { Text(stringResource(R.string.licenses)) }
                            Text(stringResource(R.string.version), color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { reset = true }, enabled = !busy) { Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (createCinema) CinemaDialog(saving = cinemaSaving, onDismiss = { createCinema = false }) { name, count, prefix ->
        if (!cinemaSaving) scope.launch {
            cinemaSaving = true
            runCatching { app.repository.createCinemaWithHalls(name, count, prefix, commonZoneNames) }.onSuccess { zoneId = it; createCinema = false }.onFailure { error = R.string.error_save }
            cinemaSaving = false
        }
    }
    if (bulkHalls) HallsDialog(cinemaSaving, { bulkHalls = false }) { count, prefix ->
        cinema?.let { selectedCinema -> if (!cinemaSaving) scope.launch {
            cinemaSaving = true
            runCatching { app.repository.addHalls(selectedCinema.id, count, prefix) }.onSuccess { bulkHalls = false }.onFailure { error = R.string.error_save }
            cinemaSaving = false
        } }
    }
    if (createZone) NameDialog(R.string.add_zone, R.string.zone_name, { createZone = false }) { name ->
        cinema?.let { scope.launch { runCatching { app.repository.createZone(it.id, name) }.onSuccess { createZone = false }.onFailure { error = R.string.error_save } } }
    }
    if (chooseZone) AlertDialog(onDismissRequest = { chooseZone = false }, title = { Text(stringResource(R.string.choose_zone)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { zones.forEach { z -> TextButton(onClick = { zoneId = z.id; chooseZone = false }) { Text("${cinemas.find { it.id == z.cinemaId }?.name.orEmpty()} · ${z.name}") } } }
    }, confirmButton = { TextButton(onClick = { chooseZone = false }) { Text(stringResource(R.string.close)) } })
    setupMode?.let { mode -> SampleDialog(mode, { setupMode = null }) { seconds, planned, actual, gate, point ->
        zone?.let { z ->
            val r = SampleRequest(z.id, mode, seconds, planned, actual, gate, point)
            setupMode = null
            if (ScanPermissions.granted(context)) launchSample(r)
            else {
                request = r
                permissions.launch(ScanPermissions.required() + if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
            }
        }
    } }
    fun savedAsset() { createAsset = false; bindAsset = null; register = null }
    if (createAsset || bindAsset != null) ManualAssetDialog(bindAsset, assetSaving, { createAsset = false; bindAsset = null }) { name, kind, address ->
        zone?.let { z -> if (!assetSaving) scope.launch {
            assetSaving = true
            runCatching {
                val existing = bindAsset
                if (existing == null) app.repository.createAsset(z.id, name, true, kind, address)
                else app.repository.addBinding(existing.id, kind, address, true)
                app.scanner.refreshAssets()
            }.onSuccess { savedAsset(); assetSaving = false; scope.launch { snackbar.showSnackbar(context.getString(if (address.isBlank()) R.string.asset_created else R.string.binding_saved)) } }.onFailure(::assetError)
            assetSaving = false
        } }
    }
    register?.let { radio -> SignalBindingDialog(radio, assets.filter { it.zoneId == zone?.id }, assetSaving, { register = null }) { name, existing ->
        zone?.let { z -> if (!assetSaving) scope.launch {
            assetSaving = true
            runCatching {
                if (existing == null) app.repository.register(z.id, name, radio.kind, radio.address, true)
                else app.repository.addBinding(existing, radio.kind, radio.address, true)
                app.scanner.refreshAssets()
            }.onSuccess { savedAsset(); assetSaving = false; scope.launch { snackbar.showSnackbar(context.getString(R.string.binding_saved)) } }.onFailure(::assetError)
            assetSaving = false
        } }
    } }
    renameAsset?.let { asset -> NameDialog(R.string.rename_asset, R.string.asset_name, { renameAsset = null }, initial = asset.name) { name ->
        scope.launch { runCatching { app.repository.renameAsset(asset.id, name) }.onSuccess { renameAsset = null }.onFailure(::assetError) }
    } }
    delete?.let { asset -> ConfirmDialog(R.string.delete_asset_title, R.string.delete_asset_note, { delete = null }) {
        scope.launch { runCatching { dao.deleteAsset(asset.id) }.onSuccess { delete = null }.onFailure { error = R.string.error_save } }
    } }
    hunt?.let { asset ->
        val radio = scan.live.find { it.assetId == asset.id }?.takeIf { System.currentTimeMillis() - it.lastAt <= 15_000 }
        AlertDialog(onDismissRequest = { hunt = null }, title = { Text(asset.name) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.hunt_note))
                Text(radio?.rssi?.let { "$it dBm" } ?: stringResource(R.string.no_signal), fontSize = 34.sp, color = Mint)
                if (radio != null) {
                    val hist = radio.history
                    val delta = (InspectionPolicy.median(hist.takeLast(5)) ?: 0) - (InspectionPolicy.median(hist.take(5)) ?: 0)
                    Text(stringResource(if (delta >= 4) R.string.trend_up else if (delta <= -4) R.string.trend_down else R.string.trend_flat))
                }
            }
        }, confirmButton = { TextButton(onClick = { hunt = null }) { Text(stringResource(R.string.close)) } })
    }
    if (reset) ConfirmDialog(R.string.reset_title, R.string.reset_note, { reset = false }) {
        scope.launch { runCatching { dao.clear() }.onSuccess { reset = false; zoneId = "" }.onFailure { error = R.string.error_save } }
    }
    if (logic) AlertDialog(onDismissRequest = { logic = false }, title = { Text(stringResource(R.string.signal_logic)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.signal_logic_summary)) }
    }, confirmButton = { TextButton(onClick = { logic = false }) { Text(stringResource(R.string.close)) } })
    if (license) {
        val text = remember { context.assets.open("attribution/LICENSE").bufferedReader().use { it.readText() } + "\n" + context.assets.open("attribution/NOTICE").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { license = false }, title = { Text(stringResource(R.string.licenses)) }, text = {
            LazyColumn { item { Text(stringResource(R.string.license_intro), color = Mint) }; item { Text(text, fontSize = 12.sp) } }
        }, confirmButton = { TextButton(onClick = { license = false }) { Text(stringResource(R.string.close)) } })
    }
    error?.let { id -> AlertDialog(onDismissRequest = { error = null }, text = { Text(stringResource(id)) }, confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(R.string.close)) } }) }
}

@Composable internal fun Panel(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun Stat(count: Int, label: Int, modifier: Modifier) {
    Column(modifier) { Text(count.toString(), fontSize = 42.sp, fontWeight = FontWeight.Bold); Text(stringResource(label), color = Muted, fontSize = 12.sp) }
}
@Composable internal fun SectionTitle(label: Int) { Text(stringResource(label), fontWeight = FontWeight.Bold, fontSize = 19.sp) }
@Composable private fun EmptyCard(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Panel { Icon(icon, null, tint = Muted, modifier = Modifier.size(30.dp)); Text(stringResource(label), color = Muted) }
}
@Composable internal fun StatusChip(status: String) {
    val id = statusLabel(status)
    val color = if (status == "NORMAL") Mint else if (status in setOf("MISSING", "WEAK")) Color(0xFFFFB69E) else Muted
    Text(stringResource(id), color = color, fontSize = 12.sp, modifier = Modifier.background(color.copy(alpha = .1f), RoundedCornerShape(8.dp)).padding(8.dp))
}
@Composable private fun CinemaDialog(saving: Boolean, onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var count by remember { mutableStateOf("1") }
    val defaultPrefix = stringResource(R.string.hall_prefix_default)
    var prefix by remember { mutableStateOf(defaultPrefix) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.setup)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, modifier = Modifier.testTag("cinema-name"), label = { Text(stringResource(R.string.cinema_name)) }, singleLine = true)
            OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3) }, modifier = Modifier.testTag("hall-count"), label = { Text(stringResource(R.string.hall_count)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(prefix, { prefix = it.take(60) }, modifier = Modifier.testTag("hall-prefix"), label = { Text(stringResource(R.string.hall_prefix)) }, singleLine = true)
            Text(stringResource(R.string.common_zones_note))
            if (count.toIntOrNull() in 1..100 && prefix.isNotBlank()) Text(stringResource(R.string.hall_preview, prefix, count.toInt()))
        }
    }, confirmButton = { Button(onClick = { onSave(name, count.toInt(), prefix) }, enabled = !saving && name.isNotBlank() && prefix.isNotBlank() && count.toIntOrNull() in 1..100) { Text(stringResource(if (saving) R.string.saving else R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun HallsDialog(saving: Boolean, onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
    var count by remember { mutableStateOf("1") }
    val defaultPrefix = stringResource(R.string.hall_prefix_default); var prefix by remember { mutableStateOf(defaultPrefix) }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(stringResource(R.string.batch_halls)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.batch_halls_note))
            OutlinedTextField(count, { count = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.hall_count)) }, modifier = Modifier.testTag("hall-count"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(prefix, { prefix = it.take(60) }, label = { Text(stringResource(R.string.hall_prefix)) }, modifier = Modifier.testTag("hall-prefix"))
        }
    }, confirmButton = { Button(onClick = { onSave(count.toInt(), prefix) }, enabled = !saving && prefix.isNotBlank() && count.toIntOrNull() in 1..100) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun NameDialog(title: Int, label: Int, onDismiss: () -> Unit, initial: String = "", onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(label)) }) },
        confirmButton = { Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun ConfirmDialog(title: Int, body: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { Text(stringResource(body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun SampleDialog(mode: String, onDismiss: () -> Unit, onStart: (Int, Int?, Int?, Int?, String) -> Unit) {
    var seconds by remember { mutableIntStateOf(120) }
    var planned by remember { mutableStateOf("") }; var actual by remember { mutableStateOf("") }; var gate by remember { mutableStateOf("") }; var point by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (mode == "LAB") R.string.lab_mode else R.string.inspection_mode)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.duration))
            Row { FilterChip(selected = seconds == 120, onClick = { seconds = 120 }, label = { Text(stringResource(R.string.two_minutes)) }); Spacer(Modifier.width(8.dp)); FilterChip(selected = seconds == 180, onClick = { seconds = 180 }, label = { Text(stringResource(R.string.three_minutes)) }) }
            if (mode == "LAB") {
                CountField(planned, { planned = it }, R.string.plan_count)
                CountField(actual, { actual = it }, R.string.actual_count)
                CountField(gate, { gate = it }, R.string.gate_count)
                OutlinedTextField(point, { point = it.take(80) }, label = { Text(stringResource(R.string.point)) }, singleLine = true)
            }
            Text(stringResource(R.string.permissions_note), color = Muted, fontSize = 12.sp)
        }
    }, confirmButton = { Button(onClick = { onStart(seconds, planned.toIntOrNull(), actual.toIntOrNull(), gate.toIntOrNull(), point) }) { Text(stringResource(R.string.sample_setup)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun CountField(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(5)) }, label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
}
