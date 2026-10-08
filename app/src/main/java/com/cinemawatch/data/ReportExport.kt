package com.cinemawatch.data

import app.fieldwatch.domain.CsvCells

/** Export exclusively from the aggregate/authorized-result database projections. Never accept raw radio objects. */
object ReportExport {
    fun csv(sessions: List<Inspection>, zones: List<Zone>, cinemas: List<Cinema>, results: List<AssetResult>, assets: List<CinemaAsset>, context: android.content.Context? = null, groups: List<SignalGroupCount> = emptyList()): String = buildString {
        append('\uFEFF')
        append(context?.getString(com.cinemawatch.R.string.csv_header) ?: "Record ID,Cinema,Zone,Mode,Start milliseconds,End milliseconds,Seconds,Unregistered WiFi APs,Unregistered BLE addresses,Seen authorized assets,Fresh WiFi batches,BLE advertisements,Discarded,Dropped,WiFi coverage,BLE coverage,Demo,Planned attendance,Manual count,Gate count,Measurement location,Asset,Status,Median RSSI,Baseline RSSI,Signal groups (aggregate only),Requested seconds").append('\n')
        sessions.forEach { s ->
            val zone = zones.find { it.id == s.zoneId }
            val base = listOf(s.id, cinemas.find { it.id == zone?.cinemaId }?.name.orEmpty(), zone?.name.orEmpty(),
                context?.getString(if (s.mode == "LAB") com.cinemawatch.R.string.lab_mode else com.cinemawatch.R.string.inspection_mode) ?: s.mode, s.startMs, s.endMs, s.durationSeconds, s.wifiCount,
                s.bleCount, s.assetCount, s.wifiBatches, s.bleEvents, s.discarded, s.dropped, s.wifiHealthy, s.bleHealthy,
                s.demo, s.planned ?: "", s.actual ?: "", s.gate ?: "", s.point)
            val extra = listOf(groups.filter { it.sessionId == s.id }.sortedWith(compareBy({ it.radio }, { it.groupCode })).joinToString(";") { "${it.radio}/${it.groupCode}=${it.count}" }, s.requestedSeconds ?: "")
            val rows = results.filter { it.sessionId == s.id }
            if (rows.isEmpty()) append((base + listOf("", "", "", "") + extra).joinToString(",") { CsvCells.quote(it.toString()) }).append('\n')
            else rows.forEach { r ->
                append((base + listOf(assets.find { it.id == r.assetId }?.name.orEmpty(), context?.getString(statusResource(r.status)) ?: r.status, r.medianRssi ?: "", r.baselineRssi ?: "") + extra)
                    .joinToString(",") { CsvCells.quote(it.toString()) }).append('\n')
            }
        }
    }
    private fun statusResource(status: String) = when (status) {
        "LEARNING" -> com.cinemawatch.R.string.baseline_learning
        "NORMAL" -> com.cinemawatch.R.string.normal
        "WEAK" -> com.cinemawatch.R.string.weak
        "UNSTABLE" -> com.cinemawatch.R.string.unstable
        "MISSING" -> com.cinemawatch.R.string.missing
        "REVIEW" -> com.cinemawatch.R.string.review
        else -> com.cinemawatch.R.string.unavailable
    }
}
