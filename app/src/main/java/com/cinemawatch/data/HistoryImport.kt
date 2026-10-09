package com.cinemawatch.data

import com.cinemawatch.domain.SignalGroup
import java.util.UUID

object HistoryImport {
    fun read(stream: java.io.InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = stream.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
            if (n < 0) break
            output.write(buffer, 0, n)
            require(output.size() <= 2_000_000)
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
    }
    data class Record(val cinema: String, val zone: String, val session: Inspection, val groups: List<SignalGroupCount>)
    fun parse(text: String): List<Record> {
        require(text.toByteArray(Charsets.UTF_8).size <= 2_000_000)
        val rows = cells(text.removePrefix("\uFEFF"))
        val header = rows.firstOrNull() ?: error("Empty CSV")
        require(header.size in 25..31 && header.first() in setOf("Record ID", "记录编号", "纪录编号", "紀錄編號")) { "Not a CinemaWatch aggregate export" }
        require(rows.size <= 10_001)
        val records = LinkedHashMap<String, Record>()
        rows.drop(1).filter { it.any(String::isNotBlank) }.forEach { row ->
            require(row.size == header.size)
            val id = UUID.fromString(row[0]).toString()
            fun number(i: Int, max: Int = 1_000_000) = row[i].toInt().also { require(it in 0..max) }
            fun optional(i: Int) = row[i].takeIf(String::isNotBlank)?.toInt()?.also { require(it in 0..1_000_000) }
            fun flag(i: Int) = when (row[i]) { "true" -> true; "false" -> false; else -> error("Invalid flag") }
            val start = row[4].toLong(); val end = row[5].toLong(); require(start >= 0 && end >= start)
            val mode = when (row[3]) {
                "LAB", "RF 校准采样", "RF校准采样", "RF 校準採樣", "RF校準採樣", "RF calibration sampling" -> "LAB"
                "INSPECTION", "资产巡检", "資產巡檢", "Asset inspection" -> "INSPECTION"
                else -> error("Unknown mode")
            }
            require(row[1].isNotBlank() && row[2].isNotBlank() && row[1].length <= 80 && row[2].length <= 80 && row[20].length <= 80)
            val requested = if (row.size >= 27 && row[26].isNotBlank()) row[26].toInt().also { require(it in setOf(15, 120, 180)) } else null
            val session = Inspection(id, "", mode, start, end, number(6, 86400), number(7, 4096), number(8, 4096), number(9), number(10), number(11), number(12), number(13), flag(14), flag(15), flag(16), optional(17), optional(18), optional(19), row[20], imported = true, requestedSeconds = requested)
            require(session.wifiCount + session.bleCount <= 4096)
            val groups = if (row.size >= 26 && row[25].isNotBlank()) row[25].split(';').map { part ->
                val (key, value) = part.split('=', limit = 2).also { require(it.size == 2) }
                val (radio, category) = key.split('/', limit = 2).also { require(it.size == 2) }
                require(radio in setOf("WIFI", "BLE")); SignalGroup.valueOf(category)
                SignalGroupCount(id, category, radio, value.toInt().also { require(it in 0..4096) })
            } else emptyList()
            require(groups.map { it.radio to it.groupCode }.distinct().size == groups.size)
            if (groups.isNotEmpty()) {
                require(groups.filter { it.radio == "WIFI" }.sumOf { it.count } == session.wifiCount)
                require(groups.filter { it.radio == "BLE" }.sumOf { it.count } == session.bleCount)
            }
            val record = Record(row[1], row[2], session, groups)
            val old = records.putIfAbsent(id, record)
            require(old == null || old == record) { "Conflicting rows for a session" }
        }
        return records.values.toList()
    }
    /** RFC 4180 quoting including embedded commas/newlines and doubled quotation marks. */
    private fun cells(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var closed = false; var i = 0
        fun field() { row += cell.toString(); cell.clear(); closed = false }
        fun line() { field(); rows += row; row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else cell.append(c)
            } else when (c) {
                '"' -> { require(cell.isEmpty() && !closed); quoted = true }
                ',' -> field()
                '\n' -> line()
                '\r' -> { line(); if (i + 1 < text.length && text[i + 1] == '\n') i++ }
                else -> { require(!closed); cell.append(c) }
            }
            i++
        }
        require(!quoted)
        if (cell.isNotEmpty() || row.isNotEmpty() || closed) line()
        return rows
    }
}
