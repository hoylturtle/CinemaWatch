package com.cinemawatch

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cinemawatch.data.*
import com.cinemawatch.domain.*
import java.text.SimpleDateFormat
import java.util.Date

internal fun groupLabel(group: SignalGroup) = when (group) {
    SignalGroup.ACCESS_POINT -> R.string.group_access_point; SignalGroup.IPHONE -> R.string.group_iphone
    SignalGroup.APPLE -> R.string.group_apple; SignalGroup.HUAWEI -> R.string.group_huawei; SignalGroup.XIAOMI -> R.string.group_xiaomi
    SignalGroup.COMPUTER -> R.string.group_computer; SignalGroup.PHONE -> R.string.group_phone; SignalGroup.AUDIO -> R.string.group_audio
    SignalGroup.HONOR -> R.string.group_honor;
    SignalGroup.OPPO -> R.string.group_oppo;
    SignalGroup.VIVO -> R.string.group_vivo;
    SignalGroup.REALME -> R.string.group_realme;
    SignalGroup.ONEPLUS -> R.string.group_oneplus;
    SignalGroup.DJI -> R.string.group_dji;
    SignalGroup.HIKVISION -> R.string.group_hikvision;
    SignalGroup.DAHUA -> R.string.group_dahua;
    SignalGroup.TP_LINK -> R.string.group_tp_link;
    SignalGroup.ZTE -> R.string.group_zte;
    SignalGroup.ACCESS_CONTROL -> R.string.group_access_control;
    SignalGroup.CAMERA -> R.string.group_camera;
    SignalGroup.DRONE -> R.string.group_drone;
    SignalGroup.FINDER -> R.string.group_finder;
    SignalGroup.GLASSES -> R.string.group_glasses;
    SignalGroup.HEALTH -> R.string.group_health;
    SignalGroup.MESH -> R.string.group_mesh;
    SignalGroup.BEACON -> R.string.group_beacon;
    SignalGroup.SIGNAGE -> R.string.group_signage;
    SignalGroup.SURVEILLANCE -> R.string.group_surveillance;
    SignalGroup.PENTEST -> R.string.group_pentest;
    SignalGroup.BODYWORN -> R.string.group_bodyworn;
    SignalGroup.PUBLIC_SAFETY -> R.string.group_public_safety;
    SignalGroup.VEHICLE -> R.string.group_vehicle;
    SignalGroup.THERMOSTAT -> R.string.group_thermostat;
    SignalGroup.OTHER -> R.string.group_other;
    SignalGroup.WEARABLE -> R.string.group_wearable; SignalGroup.IOT -> R.string.group_iot; SignalGroup.UNKNOWN -> R.string.group_unknown
}
internal fun evidenceLabel(e: GuessEvidence) = when (e) {
    GuessEvidence.RADIO_TYPE -> R.string.evidence_radio_type; GuessEvidence.ADVERTISED_NAME -> R.string.evidence_advertised_name
    GuessEvidence.COMPANY_ID -> R.string.evidence_company_id; GuessEvidence.OUI -> R.string.evidence_oui
    GuessEvidence.SIGNATURE -> R.string.evidence_signature; GuessEvidence.NONE -> R.string.evidence_none
}
internal fun statusLabel(status: String) = when (status) {
    "NORMAL" -> R.string.normal; "WEAK" -> R.string.weak; "UNSTABLE" -> R.string.unstable; "MISSING" -> R.string.missing
    "REVIEW" -> R.string.review; "UNAVAILABLE" -> R.string.unavailable; "UNBOUND" -> R.string.awaiting_binding
    "PENDING" -> R.string.awaiting_inspection; else -> R.string.baseline_learning
}
internal fun Inspection.overran(): Boolean = durationSeconds > (requestedSeconds?.plus(5) ?: 185)

/** Text shares exclusively the same aggregate and authorized-result projections as the preview. */
internal fun reportText(c: Context, s: Inspection, cinema: String, zone: String, results: List<AssetResult>, assets: List<CinemaAsset>, groups: List<SignalGroupCount>): String = buildString {
    appendLine(c.getString(R.string.report_title)); appendLine("$cinema · $zone")
    appendLine(SimpleDateFormat("yyyy/MM/dd HH:mm", c.resources.configuration.locales[0]).format(Date(s.startMs)))
    if (s.demo) appendLine(c.getString(R.string.demo_note))
    if (s.imported) appendLine(c.getString(R.string.imported_record))
    appendLine("${c.getString(R.string.actual_duration)}: ${c.getString(R.string.seconds_format, s.durationSeconds)}")
    appendLine("${c.getString(R.string.wifi_aps)}: ${s.wifiCount}"); appendLine("${c.getString(R.string.ble_radios)}: ${s.bleCount}")
    appendLine(c.getString(R.string.calibration_pending)); appendLine(c.getString(R.string.signal_group_note))
    groups.filter { it.sessionId == s.id }.forEach { g ->
        val category = SignalGroup.entries.find { it.name == g.groupCode } ?: SignalGroup.UNKNOWN
        appendLine("${c.getString(groupLabel(category))} · ${g.radio}: ${g.count}")
    }
    appendLine("${c.getString(R.string.wifi_batches)}: ${s.wifiBatches}"); appendLine("${c.getString(R.string.ble_events)}: ${s.bleEvents}")
    appendLine("${c.getString(R.string.discarded_count)}: ${s.discarded}"); appendLine("${c.getString(R.string.dropped_count)}: ${s.dropped}")
    if (s.overran()) appendLine(c.getString(R.string.late_sample))
    appendLine(c.getString(if (s.wifiHealthy && s.bleHealthy && !s.overran()) R.string.healthy_sample else R.string.partial_sample))
    results.filter { it.sessionId == s.id }.forEach { r -> appendLine("${assets.find { it.id == r.assetId }?.name.orEmpty()}: ${c.getString(statusLabel(r.status))}, RSSI ${r.medianRssi ?: "—"} dBm") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReportPreview(s: Inspection, cinema: String, zone: String, results: List<AssetResult>, assets: List<CinemaAsset>, groups: List<SignalGroupCount>, onClose: () -> Unit) {
    val c = LocalContext.current
    BackHandler(onBack = onClose)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.report_title)) }, navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } }) }, modifier = Modifier.testTag("report-preview")) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("report-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("$cinema · $zone", style = MaterialTheme.typography.titleLarge)
                Text(SimpleDateFormat("yyyy/MM/dd HH:mm", c.resources.configuration.locales[0]).format(Date(s.startMs)))
                if (s.demo) Text(stringResource(R.string.demo_note), color = MaterialTheme.colorScheme.primary)
                if (s.imported) Text(stringResource(R.string.imported_record))
                TextButton(onClick = { c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, reportText(c, s, cinema, zone, results, assets, groups)), c.getString(R.string.share_preview))) }) { Text(stringResource(R.string.share_preview)) }
            }
            item { Panel {
                SectionTitle(R.string.report_summary)
                Metric(R.string.requested_duration, s.requestedSeconds?.let { stringResource(R.string.seconds_format, it) } ?: stringResource(R.string.legacy_duration))
                Metric(R.string.actual_duration, stringResource(R.string.seconds_format, s.durationSeconds))
                Metric(R.string.wifi_aps, s.wifiCount.toString()); Metric(R.string.ble_radios, s.bleCount.toString()); Metric(R.string.authorized_assets, s.assetCount.toString())
                Text(stringResource(R.string.calibration_pending))
                s.planned?.let { Metric(R.string.plan_count, it.toString()) }; s.actual?.let { Metric(R.string.actual_count, it.toString()) }; s.gate?.let { Metric(R.string.gate_count, it.toString()) }
                if (s.point.isNotBlank()) Metric(R.string.point, s.point)
            } }
            item { Panel {
                SectionTitle(R.string.report_quality)
                if (s.overran()) Text(stringResource(R.string.late_sample), color = MaterialTheme.colorScheme.error)
                Text("Wi-Fi · " + stringResource(if (s.wifiHealthy && !s.overran()) R.string.coverage_ok else R.string.coverage_not_ok))
                Text("BLE · " + stringResource(if (s.bleHealthy && !s.overran()) R.string.coverage_ok else R.string.coverage_not_ok))
                Metric(R.string.wifi_batches, s.wifiBatches.toString()); Metric(R.string.ble_events, s.bleEvents.toString())
                Metric(R.string.discarded_count, s.discarded.toString()); Metric(R.string.dropped_count, s.dropped.toString())
            } }
            item { Panel {
                SectionTitle(R.string.report_groups); Text(stringResource(R.string.signal_group_note))
                val rows = groups.filter { it.sessionId == s.id }
                if (rows.isEmpty()) Text(stringResource(R.string.report_old_groups))
                rows.sortedWith(compareBy({ it.groupCode }, { it.radio })).forEach { g ->
                    val category = SignalGroup.entries.find { it.name == g.groupCode } ?: SignalGroup.UNKNOWN
                    Text("${stringResource(groupLabel(category))} · ${g.radio}: ${g.count}")
                }
            } }
            item { SectionTitle(R.string.report_assets) }
            val rows = results.filter { it.sessionId == s.id }
            if (rows.isEmpty()) item { Text(stringResource(R.string.report_no_assets)) }
            items(rows, key = { it.assetId }) { r -> Panel {
                Text(assets.find { it.id == r.assetId }?.name.orEmpty(), fontWeight = FontWeight.Bold); StatusChip(r.status)
                Metric(R.string.baseline_rssi, r.baselineRssi?.let { "$it dBm" } ?: stringResource(R.string.no_baseline))
                Text("RSSI: ${r.medianRssi ?: "—"} dBm")
                Metric(R.string.rssi_spread, "${r.spread} dBm"); Metric(R.string.miss_count, r.consecutiveMisses.toString())
            } }
        }
    }
}
@Composable private fun Metric(label: Int, value: String) { Column { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium) } }
