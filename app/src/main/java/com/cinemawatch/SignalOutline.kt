package com.cinemawatch

// Adapted from Fieldwatch v1.1.21, commit 5379a2049c351c2483f7f106ca68d5d6d5f00e64:
// ui/screen/LiveScreens.kt (OutlineGroupRow, DeviceRow) and ui/component/Widgets.kt (RssiBar).
// Copyright (c) 2026 Off Grid Pete LLC. MIT License: third_party/fieldwatch/LICENSE.

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.Rssi
import com.cinemawatch.data.CinemaAsset
import com.cinemawatch.domain.SignalGroup
import com.cinemawatch.domain.SignalGrouping
import com.cinemawatch.radio.LiveRadio
import kotlin.math.roundToInt

private val RadioSurface = Color(0xFF182A2B)
private val RadioText = Color(0xFFE1E5EA)
private val RadioMuted = Color(0xFF98A6B1)

internal fun LazyListScope.signalOutline(
    radios: List<LiveRadio>, assets: List<CinemaAsset>, hideEmpty: Boolean,
    expanded: Set<String>, now: Long, onToggle: (SignalGroup) -> Unit, onOpen: (LiveRadio) -> Unit,
) {
    listOf(R.string.device_classes to SignalGrouping.deviceTypes, R.string.ecosystem_classes to SignalGrouping.ecosystems).forEach { (title, categories) ->
        item(key = "outline-section-$title") { Text(stringResource(title), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
        categories.forEach { category ->
            val members = radios.filter { category in it.categories }.sortedByDescending { it.rssi }
            if (hideEmpty && members.isEmpty()) return@forEach
            item(key = "group-${category.name}") {
                val rules = members.flatMap { it.signatureHits }.filter { SignalGrouping.fromSignature(it.category) == category }.map { it.name }.distinct()
                OutlineGroupRow(category, members.size, when (rules.size) { 0 -> null; 1 -> rules.single(); else -> stringResource(R.string.signature_count, rules.size) }, category.name in expanded, { onToggle(category) })
            }
            if (category.name in expanded) {
                items(members, key = { "device-${category.name}-${it.key}" }) { radio ->
                    OutlineRadioRow(radio, category, assets.find { it.id == radio.assetId }?.name, now, { onOpen(radio) })
                }
            }
        }
    }
}

private fun groupAccent(group: SignalGroup) = when (group) {
    SignalGroup.FINDER, SignalGroup.APPLE, SignalGroup.IPHONE -> Color(0xFFB68AF8)
    SignalGroup.CAMERA, SignalGroup.SURVEILLANCE, SignalGroup.HIKVISION, SignalGroup.DAHUA -> Color(0xFF70C8E4)
    SignalGroup.ACCESS_POINT, SignalGroup.TP_LINK, SignalGroup.ZTE, SignalGroup.MESH -> Color(0xFF76D3A6)
    SignalGroup.HUAWEI, SignalGroup.HONOR, SignalGroup.XIAOMI -> Color(0xFFFFBC73)
    SignalGroup.UNKNOWN -> RadioMuted
    else -> Color(0xFF66E0CF)
}

private fun groupGlyph(group: SignalGroup): ImageVector = when (group) {
    SignalGroup.ACCESS_CONTROL -> Icons.Outlined.Lock
    SignalGroup.AUDIO -> Icons.Outlined.Headphones
    SignalGroup.CAMERA, SignalGroup.SURVEILLANCE, SignalGroup.HIKVISION, SignalGroup.DAHUA -> Icons.Outlined.CameraAlt
    SignalGroup.DRONE, SignalGroup.DJI -> Icons.Outlined.Flight
    SignalGroup.FINDER -> Icons.Outlined.LocalOffer
    SignalGroup.HEALTH -> Icons.Outlined.HealthAndSafety
    SignalGroup.ACCESS_POINT, SignalGroup.TP_LINK, SignalGroup.ZTE -> Icons.Outlined.Router
    SignalGroup.COMPUTER -> Icons.Outlined.Computer
    SignalGroup.PHONE, SignalGroup.IPHONE -> Icons.Outlined.Smartphone
    SignalGroup.IOT, SignalGroup.THERMOSTAT -> Icons.Outlined.Home
    SignalGroup.WEARABLE -> Icons.Outlined.Watch
    SignalGroup.VEHICLE -> Icons.Outlined.DirectionsCar
    SignalGroup.BEACON, SignalGroup.MESH -> Icons.Outlined.Sensors
    SignalGroup.UNKNOWN -> Icons.Outlined.HelpOutline
    else -> Icons.Outlined.Devices
}

@Composable private fun RadioBadge(glyph: ImageVector, accent: Color, empty: Boolean = false) {
    Surface(shape = CircleShape, color = accent.copy(alpha = if (empty) .12f else .18f), modifier = Modifier.size(30.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(glyph, null, modifier = Modifier.size(18.dp), tint = accent.copy(alpha = if (empty) .55f else 1f)) }
    }
}

/** Fieldwatch's filled outline row, adapted only for local labels and category data. */
@Composable private fun OutlineGroupRow(group: SignalGroup, count: Int, subtitle: String?, expanded: Boolean, onToggle: () -> Unit) {
    val empty = count == 0
    val accent = if (empty) RadioMuted else groupAccent(group)
    Surface(shape = RoundedCornerShape(12.dp), color = RadioSurface, tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("group-${group.name}").clickable(onClick = onToggle)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioBadge(groupGlyph(group), accent, empty)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(groupLabel(group)), fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = if (empty) RadioMuted else RadioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 11.sp, lineHeight = 13.sp, color = RadioMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "▾  $count" else "▸  $count", fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable private fun OutlineRadioRow(radio: LiveRadio, group: SignalGroup, assetName: String?, now: Long, onOpen: () -> Unit) {
    val accent = groupAccent(group)
    val measured = Rssi.measured(radio.rssi)
    val average = radio.history.filter(Rssi::measured).takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val previous = radio.history.dropLast(1).takeLast(5).filter(Rssi::measured).takeIf { it.isNotEmpty() }?.average()
    val trend = if (!measured || previous == null) "·" else when { radio.rssi - previous > 3 -> "↑"; radio.rssi - previous < -3 -> "↓"; else -> "=" }
    Surface(shape = RoundedCornerShape(12.dp), color = RadioSurface,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp).testTag("radio-${group.name}-${radio.key}").clickable(onClick = onOpen)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioBadge(groupGlyph(group), accent)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(assetName ?: radio.address, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = RadioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val label = radio.signatureHits.firstOrNull { SignalGrouping.fromSignature(it.category) == group }?.name ?: stringResource(groupLabel(group))
                    Surface(color = accent.copy(alpha = .16f), shape = RoundedCornerShape(5.dp)) {
                        Text(label, color = accent, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (radio.kind == "WIFI") Icons.Outlined.Wifi else Icons.Outlined.Bluetooth, null, tint = RadioMuted, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(listOfNotNull(radio.vendor, radio.name.takeIf { it.isNotBlank() }).distinct().joinToString(" · "), fontFamily = FontFamily.Monospace, color = RadioMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (measured) "$trend ${radio.rssi}" else "—", color = accent, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (radio.frequencyMhz > 0) Text("${radio.frequencyMhz} MHz", color = RadioMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    average?.let { Text(stringResource(R.string.signal_average_short, it), color = RadioMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                }
            }
            Spacer(Modifier.height(6.dp))
            RssiBar(if (measured) radio.rssi else -100, accent, Modifier.testTag("rssi-${group.name}-${radio.key}"))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.signal_seen_times, ((now - radio.firstAt) / 1000).coerceAtLeast(0), ((now - radio.lastAt) / 1000).coerceAtLeast(0)), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = RadioMuted)
        }
    }
}

/** Fieldwatch Widgets.kt RssiBar, same scaling and animation; not a distance estimate. */
@Composable private fun RssiBar(rssi: Int, color: Color, modifier: Modifier = Modifier) {
    val fraction = ((rssi + 100).coerceIn(0, 70) / 70f)
    val animated by animateFloatAsState(fraction, tween(350), label = "rssi")
    val track = Color(0xFF203438)
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        val minFill = if (animated > 0f) size.height else 0f
        val fillW = (size.width * animated).coerceAtLeast(minFill).coerceAtMost(size.width)
        if (fillW > 0f) drawRoundRect(color = color, size = Size(fillW, size.height), cornerRadius = radius)
    }
}

@Composable internal fun SignalDetailDialog(radio: LiveRadio, assetName: String?, canRegister: Boolean, now: Long, onDismiss: () -> Unit, onRegister: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(assetName ?: radio.name.ifBlank { stringResource(R.string.unknown_radio) }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${radio.kind} · ${radio.address}", fontFamily = FontFamily.Monospace)
            radio.vendor?.let { Text(it) }
            Text(if (Rssi.measured(radio.rssi)) "RSSI ${radio.rssi} dBm" else stringResource(R.string.no_signal))
            if (radio.frequencyMhz > 0) Text("${radio.frequencyMhz} MHz")
            Text(radio.categories.joinToString(" · ") { category -> context.getString(groupLabel(category)) })
            if (radio.signatureHits.isNotEmpty()) Text(stringResource(R.string.match_rules, radio.signatureHits.map { it.name }.distinct().joinToString(" · ")))
            Text(stringResource(evidenceLabel(radio.guess.evidence)))
            Text(stringResource(when (radio.guess.confidence) { "MEDIUM" -> R.string.confidence_medium; "LOW" -> R.string.confidence_low; else -> R.string.confidence_unknown }))
            Text(stringResource(R.string.signal_seen_times, ((now - radio.firstAt) / 1000).coerceAtLeast(0), ((now - radio.lastAt) / 1000).coerceAtLeast(0)))
            Text(stringResource(R.string.signal_group_note))
            Text(stringResource(R.string.signal_average_note))
            if (radio.assetId != null) Text(stringResource(R.string.registered_signal))
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }, dismissButton = { if (canRegister) TextButton(onClick = onRegister) { Text(stringResource(R.string.register)) } })
}
