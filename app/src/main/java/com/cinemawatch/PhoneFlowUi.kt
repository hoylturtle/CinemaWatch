package com.cinemawatch

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemawatch.data.*
import com.cinemawatch.flow.*
import com.cinemawatch.radio.ScanPermissions
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable internal fun PhoneFlowDialog(app:CinemaApp,cinema:Cinema?,zones:List<Zone>,assets:List<CinemaAsset>,bindings:List<RadioBinding>,inspectionBusy:Boolean,onDismiss:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val state by app.phoneFlow.state.collectAsStateWithLifecycle()
    var role by remember { mutableStateOf("HOST") }
    var name by remember { mutableStateOf("") }
    var localZone by remember { mutableStateOf(zones.firstOrNull()?.id.orEmpty()) }
    var nodeZone by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf("0") }
    var host by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var approved by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }
    var truthTarget by remember(state.config?.id) { mutableStateOf(state.config?.targets?.firstOrNull()?.id.orEmpty()) }
    var truthZone by remember(state.config?.id) { mutableStateOf(state.config?.zones?.keys?.firstOrNull().orEmpty()) }
    var reports by remember { mutableStateOf(app.phoneFlow.reports()) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    val candidates=bindings.filter { b -> b.radio=="BLE" && assets.any { a -> a.id==b.assetId && a.authorized && zones.any { it.id==a.zoneId } } }
    LaunchedEffect(state.latestReport) { reports=withContext(Dispatchers.IO) { app.phoneFlow.reports() } }
    fun start() {
        if(inspectionBusy || state.active)return
        error=false
        runCatching {
            check(approved && ScanPermissions.granted(context))
            check(context.getSystemService(LocationManager::class.java)?.isLocationEnabled==true)
            val adjustment=offset.toInt();check(adjustment in -20..20)
            if(role=="HOST") {
                check(cinema!=null)
                app.phoneFlow.prepareHost(cinema!!.name,zones.associate { it.id to it.name },candidates.filter { it.id in selected }.map { b -> assets.first { it.id==b.assetId }.name to b.address },route,name.trim(),localZone,adjustment)
            } else app.phoneFlow.prepareNode(host.trim(),key.trim(),name.trim(),nodeZone.trim(),adjustment)
            ContextCompat.startForegroundService(context,Intent(context,PhoneFlowService::class.java))
            key=""
        }.onFailure { error=true }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(pendingStart) { pendingStart=false;start() }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val file=exportFile
        if(uri!=null && file!=null)scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("No output") } }.onFailure { error=true }
        }
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.flow_title),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text(stringResource(R.string.close)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.flow_scope))
                    if(!state.active) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(role=="HOST",{ role="HOST" },label={ Text(stringResource(R.string.flow_host)) })
                            FilterChip(role=="NODE",{ role="NODE" },label={ Text(stringResource(R.string.flow_node)) })
                        }
                        Text(stringResource(R.string.flow_setup_hint))
                        OutlinedTextField(name,{ name=it.take(60) },label={ Text(stringResource(R.string.flow_node_name)) },modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(offset,{ offset=it.take(3) },label={ Text(stringResource(R.string.flow_offset)) },modifier=Modifier.fillMaxWidth())
                        if(role=="HOST") {
                            Text(cinema?.name ?: stringResource(R.string.flow_need_cinema))
                            zones.forEach { zone -> FilterChip(localZone==zone.id,{ localZone=zone.id },label={ Text(zone.name) }) }
                            OutlinedTextField(route,{ route=it.take(200) },label={ Text(stringResource(R.string.flow_route)) },modifier=Modifier.fillMaxWidth())
                            Text(stringResource(R.string.flow_select_tags))
                            if(candidates.isEmpty())Text(stringResource(R.string.flow_no_tags))
                            candidates.forEach { binding ->
                                Row { Checkbox(binding.id in selected,{ checked -> selected=if(checked)selected+binding.id else selected-binding.id }); Text(assets.first { it.id==binding.assetId }.name,modifier=Modifier.padding(top=12.dp)) }
                            }
                        } else {
                            OutlinedTextField(host,{ host=it.take(15) },label={ Text(stringResource(R.string.flow_ip)) },modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(key,{ key=it.take(64) },label={ Text(stringResource(R.string.flow_key)) },modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(nodeZone,{ nodeZone=it.take(80) },label={ Text(stringResource(R.string.flow_exact_zone)) },modifier=Modifier.fillMaxWidth())
                        }
                        Row { Checkbox(approved,{ approved=it });Text(stringResource(R.string.flow_consent),modifier=Modifier.weight(1f).padding(top=12.dp)) }
                        Button(onClick={
                            if(ScanPermissions.granted(context))start() else {
                                pendingStart=true
                                permission.launch(ScanPermissions.required()+if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
                            }
                        },enabled=approved && !inspectionBusy && name.isNotBlank() && (role=="NODE" || (zones.size>=2 && selected.size in 1..16)),modifier=Modifier.fillMaxWidth().testTag("flow-start")) { Text(stringResource(R.string.flow_start)) }
                    } else {
                        Text(stringResource(if(state.role=="HOST")R.string.flow_host else R.string.flow_node),style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(if(state.connected)R.string.flow_connected else R.string.flow_disconnected))
                        if(state.role=="HOST") {
                            Text(stringResource(R.string.flow_ip)+": "+state.host)
                            Text(stringResource(R.string.flow_key)+": "+state.key.take(8)+"…")
                            TextButton(onClick={ context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(context.getString(R.string.flow_key),state.key)) }) { Text(stringResource(R.string.flow_copy_key)) }
                        } else Text(stringResource(R.string.flow_rtt,state.rtt))
                        OutlinedButton(onClick={ app.phoneFlow.stop() },modifier=Modifier.fillMaxWidth().testTag("flow-stop")) { Text(stringResource(R.string.flow_stop)) }
                    }
                    if(error || state.error.isNotBlank())Text(stringResource(when(state.error) { "SCAN" -> R.string.flow_scan_error;"SAVE" -> R.string.error_save;else -> R.string.flow_error }),color=MaterialTheme.colorScheme.error)
                    if(state.config!=null) {
                        val cfg=state.config!!
                        val snapshot=state.snapshot
                        Panel {
                            Text(stringResource(R.string.flow_nodes),style=MaterialTheme.typography.titleMedium)
                            snapshot.nodes.forEach { node ->
                                val online=node.healthy && (state.role!="HOST" || System.currentTimeMillis()-node.at in 0..10000)
                                Text(node.name+" · "+cfg.zones[node.zone]+" · "+stringResource(if(online)R.string.flow_ready else R.string.flow_unready))
                            }
                            Text(stringResource(R.string.flow_algorithm),style=MaterialTheme.typography.bodySmall)
                        }
                        Panel {
                            Text(stringResource(R.string.flow_presence),style=MaterialTheme.typography.titleMedium)
                            snapshot.presence.forEach { tag -> Text(tag.label+" → "+(cfg.zones[tag.zone] ?: stringResource(R.string.flow_unknown))+" · "+stringResource(R.string.flow_evidence,tag.margin,tag.receivers)) }
                            snapshot.signals.forEach { s -> Text(s.label+" · "+s.node+" · ${s.median} dBm · "+stringResource(R.string.flow_samples,s.samples),style=MaterialTheme.typography.bodySmall) }
                        }
                        Panel {
                            Text(stringResource(R.string.flow_transitions),style=MaterialTheme.typography.titleMedium)
                            snapshot.transitions.groupBy { it.from to it.to }.forEach { (path,items) -> Text("${cfg.zones[path.first]} → ${cfg.zones[path.second]} · ${items.size}") }
                            Text(stringResource(R.string.flow_dwell_hint),style=MaterialTheme.typography.bodySmall)
                            snapshot.dwellSeconds.forEach { (z,seconds) -> Text("${cfg.zones[z]} · ${seconds}s") }
                        }
                        if(state.role=="HOST")Panel {
                            Text(stringResource(R.string.flow_truth),style=MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.flow_truth_hint))
                            if(state.active) {
                                cfg.targets.forEach { tag -> FilterChip(truthTarget==tag.id,{ truthTarget=tag.id },label={ Text(tag.label) }) }
                                cfg.zones.forEach { (id,label) -> FilterChip(truthZone==id,{ truthZone=id },label={ Text(label) },modifier=Modifier.testTag("truth-zone-$id")) }
                                Button(onClick={ runCatching { app.phoneFlow.markTruth(truthTarget,truthZone) }.onFailure { error=true } },enabled=truthTarget.isNotBlank() && truthZone.isNotBlank(),modifier=Modifier.testTag("flow-mark-truth")) { Text(stringResource(R.string.flow_mark_truth)) }
                            }
                            Text(stringResource(R.string.flow_score,snapshot.truths.count { it.expected==it.observed },snapshot.truths.size,snapshot.truths.count { it.observed==null }))
                        }
                    }
                    if(reports.isNotEmpty())Panel {
                        Text(stringResource(R.string.flow_reports),style=MaterialTheme.typography.titleMedium)
                        reports.forEach { file ->
                            Text(DateFormat.getDateTimeInstance().format(Date(file.lastModified())))
                            Row {
                                TextButton(onClick={ scope.launch { runCatching { withContext(Dispatchers.IO) { JSONObject(file.readText()) } }.onSuccess { preview=it }.onFailure { error=true } } }) { Text(stringResource(R.string.flow_preview)) }
                                TextButton(onClick={ exportFile=file;export.launch("CinemaWatch-test-${file.name}") }) { Text(stringResource(R.string.flow_export)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    preview?.let { report ->
        val data=report.getJSONObject("data");val names=report.getJSONObject("zones")
        val observations=data.getJSONArray("truths")
        AlertDialog(onDismissRequest={ preview=null },title={ Text(stringResource(R.string.flow_reports)) },text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(report.getString("cinema"));Text(report.getString("plannedRoute"));Text(stringResource(R.string.flow_scope))
                Text(stringResource(R.string.flow_archive_summary,data.getJSONArray("nodes").length(),data.getJSONArray("transitions").length(),observations.length()))
                val transitions=data.getJSONArray("transitions")
                for(i in 0 until transitions.length())transitions.getJSONObject(i).let { Text(it.getString("label")+" · "+names.optString(it.getString("from"))+" → "+names.optString(it.getString("to"))) }
                for(i in 0 until observations.length())observations.getJSONObject(i).let { Text(it.getString("label")+" · "+names.optString(it.getString("expected"))+" / "+if(it.isNull("observed"))context.getString(R.string.flow_unknown) else names.optString(it.getString("observed"))) }
            }
        },confirmButton={ TextButton(onClick={ preview=null }) { Text(stringResource(R.string.close)) } })
    }
}
