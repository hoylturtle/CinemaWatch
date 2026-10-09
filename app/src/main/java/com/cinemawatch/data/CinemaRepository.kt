package com.cinemawatch.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import java.util.UUID

class DuplicateRadioException : IllegalArgumentException("Radio already registered")
class InvalidRadioAddressException : IllegalArgumentException("Invalid radio address")

object RadioAddress {
    fun normalize(raw: String, radio: String = "WIFI"): String {
        require(radio in setOf("WIFI", "BLE"))
        val hex = raw.trim().replace(":", "").replace("-", "").uppercase(java.util.Locale.ROOT)
        if (!Regex("[0-9A-F]{12}").matches(hex) || hex in setOf("000000000000", "FFFFFFFFFFFF", "020000000000") || (radio == "WIFI" && hex.take(2).toInt(16) and 1 != 0)) throw InvalidRadioAddressException()
        return hex.chunked(2).joinToString(":")
    }
}

class CinemaRepository(val database: CinemaDatabase) {
    val dao = database.dao()
    suspend fun createCinema(name: String, zoneName: String) = database.withTransaction {
        require(name.isNotBlank() && zoneName.isNotBlank())
        val cinema = Cinema(UUID.randomUUID().toString(), name.trim().take(80))
        dao.insertCinema(cinema)
        dao.insertZone(Zone(UUID.randomUUID().toString(), cinema.id, zoneName.trim().take(80)))
    }
    suspend fun createCinemaWithHalls(name: String, count: Int, prefix: String, commonZones: List<String> = emptyList()): String = database.withTransaction {
        require(name.isNotBlank() && prefix.isNotBlank() && count in 1..100)
        val cinema = Cinema(UUID.randomUUID().toString(), name.trim().take(80))
        dao.insertCinema(cinema)
        val firstHall = addHalls(cinema.id, count, prefix).first().id
        if (commonZones.isNotEmpty()) addCommonZones(cinema.id, commonZones)
        firstHall
    }
    suspend fun addHalls(cinemaId: String, count: Int, prefix: String): List<Zone> = database.withTransaction {
        require(count in 1..100 && prefix.isNotBlank())
        val label = prefix.trim().take(60)
        val existing = dao.zones().first().filter { it.cinemaId == cinemaId }
        val last = existing.mapNotNull { it.name.takeIf { name -> name.startsWith("$label ") }?.removePrefix("$label ")?.toIntOrNull() }.maxOrNull() ?: 0
        (1..count).map { Zone(UUID.randomUUID().toString(), cinemaId, "$label ${last + it}").also { z -> dao.insertZone(z) } }
    }
    /** Add missing standard areas; translated names share one identity for duplicate checks. */
    suspend fun addCommonZones(cinemaId: String, names: List<String>) = database.withTransaction {
        require(names.size == 4 && names.all { it.isNotBlank() && it.length <= 80 })
        require(dao.cinemas().first().any { it.id == cinemaId })
        val aliases = listOf(
            setOf("大堂", "Lobby"), setOf("通道", "Corridor"),
            setOf("办公区", "辦公區", "Office"), setOf("放映层", "放映層", "Projection floor")
        )
        val existing = dao.zones().first().filter { it.cinemaId == cinemaId }.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.toMutableSet()
        names.mapIndexedNotNull { index, name ->
            val alternatives = (aliases[index] + name.trim()).map { it.lowercase(java.util.Locale.ROOT) }
            if (alternatives.any { it in existing }) null else {
                val zone = Zone(UUID.randomUUID().toString(), cinemaId, name.trim())
                dao.insertZone(zone)
                existing.add(name.trim().lowercase(java.util.Locale.ROOT))
                zone
            }
        }
    }
    suspend fun createZone(cinemaId: String, name: String): Zone {
        require(name.isNotBlank())
        val zone = Zone(UUID.randomUUID().toString(), cinemaId, name.trim().take(80))
        dao.insertZone(zone)
        return zone
    }
    suspend fun createAsset(zoneId: String, name: String, authorized: Boolean, radio: String = "WIFI", address: String = "", location: String = "", notes: String = ""): CinemaAsset = database.withTransaction {
        require(authorized && name.isNotBlank()) { "Explicit asset authorization required" }
        val asset = CinemaAsset(UUID.randomUUID().toString(), zoneId, name.trim().take(80), true, location.trim().take(160), notes.trim().take(1000))
        dao.insertAsset(asset)
        if (address.isNotBlank()) addBinding(asset.id, radio, address, authorized)
        asset
    }
    suspend fun addBinding(assetId: String, radio: String, address: String, authorized: Boolean) = database.withTransaction {
        require(authorized && dao.asset(assetId)?.authorized == true && radio in setOf("WIFI", "BLE"))
        val normalized = RadioAddress.normalize(address, radio)
        if (dao.allBindings().any { it.radio == radio && it.address == normalized }) throw DuplicateRadioException()
        dao.insertBinding(RadioBinding(UUID.randomUUID().toString(), assetId, radio, normalized))
    }
    suspend fun register(zoneId: String, name: String, radio: String, address: String, authorized: Boolean) = createAsset(zoneId, name, authorized, radio, address)
    suspend fun renameAsset(id: String, name: String) {
        require(name.isNotBlank()); dao.renameAsset(id, name.trim().take(80))
    }
    suspend fun updateAssetDetails(id: String, name: String, location: String, notes: String) {
        require(name.isNotBlank() && dao.asset(id) != null)
        dao.updateAssetDetails(id, name.trim().take(80), location.trim().take(160), notes.trim().take(1000))
    }
    suspend fun save(session: Inspection, results: List<AssetResult>, groups: List<SignalGroupCount> = emptyList()) = database.withTransaction {
        dao.insertSession(session); dao.insertResults(results); dao.insertGroups(groups)
        updateMaintenance(session, results)
    }
    /** Import aggregate history only; never fabricate assets or wireless bindings from names. */
    suspend fun importHistory(text: String): Int = database.withTransaction {
        val parsed = HistoryImport.parse(text)
        var imported = 0
        parsed.forEach { row ->
            if (dao.session(row.session.id) == null) {
                val cinema = dao.cinemas().first().find { it.name == row.cinema } ?: Cinema(UUID.randomUUID().toString(), row.cinema).also { dao.insertCinema(it) }
                val zone = dao.zones().first().find { it.cinemaId == cinema.id && it.name == row.zone } ?: Zone(UUID.randomUUID().toString(), cinema.id, row.zone).also { dao.insertZone(it) }
                save(row.session.copy(zoneId = zone.id, imported = true), emptyList(), row.groups)
                imported++
            }
        }
        imported
    }
    suspend fun confirmBaseline(assetId: String, sessionId: String) = database.withTransaction {
        val session = requireNotNull(dao.session(sessionId))
        val result = dao.history(assetId).firstOrNull { it.sessionId == sessionId }
        require(result != null && result.status in setOf("LEARNING", "NORMAL") && result.medianRssi != null && result.spread < 18)
        val radios = dao.allBindings().filter { it.assetId == assetId }.map { it.radio }
        require(dao.asset(assetId)?.authorized == true && dao.asset(assetId)?.zoneId == session.zoneId && validInspection(session) && radios.isNotEmpty() && radios.all { if (it == "WIFI") session.wifiHealthy else session.bleHealthy })
        require(com.cinemawatch.domain.InspectionPolicy.validRssi(result.medianRssi))
        dao.putBaseline(AssetBaseline(assetId, result.medianRssi, sessionId, System.currentTimeMillis()))
    }
    suspend fun updateIssue(id: String, action: String, note: String, photo: String = "") = database.withTransaction {
        require(action in setOf("NOTE", "IN_PROGRESS", "WAITING_RECHECK", "OPEN") && note.trim().length <= 1000)
        require(photo.isBlank() || Regex("[a-zA-Z0-9-]+[.]jpg").matches(photo))
        val issue = requireNotNull(dao.issue(id)); require(issue.state != "CLOSED")
        require(action == "NOTE" || note.isNotBlank() || photo.isNotBlank())
        val at = System.currentTimeMillis()
        dao.updateIssue(issue.copy(state = if (action == "NOTE") issue.state else action, updatedAt = at))
        dao.insertEvent(IssueEvent(UUID.randomUUID().toString(), id, at, action, note.trim(), photo))
    }
    private fun validInspection(s: Inspection) = !s.demo && !s.imported && s.mode == "INSPECTION" && s.durationSeconds <= (s.requestedSeconds?.plus(5) ?: 185)
    private suspend fun updateMaintenance(s: Inspection, results: List<AssetResult>) {
        if (!validInspection(s)) return
        for (result in results) {
            val radios = dao.allBindings().filter { it.assetId == result.assetId }.map { it.radio }
            if (dao.asset(result.assetId)?.authorized != true || dao.asset(result.assetId)?.zoneId != s.zoneId || radios.isEmpty() || !radios.all { if (it == "WIFI") s.wifiHealthy else s.bleHealthy }) continue
            val existing = dao.activeIssue(result.assetId)
            if (existing != null && s.endMs <= existing.createdAt) continue
            if (result.status in setOf("WEAK", "UNSTABLE", "MISSING", "REVIEW")) {
                val issue = existing ?: MaintenanceIssue(UUID.randomUUID().toString(), result.assetId, s.id, result.status, "OPEN", s.endMs, s.endMs).also { dao.insertIssue(it) }
                if (existing != null) dao.updateIssue(issue.copy(sourceSessionId = s.id, reason = result.status, state = if (issue.state == "WAITING_RECHECK" && s.startMs > issue.updatedAt) "OPEN" else issue.state))
                dao.insertEvent(IssueEvent(UUID.randomUUID().toString(), issue.id, s.endMs, if (existing == null) "DETECTED" else "RECHECK_ABNORMAL", "", sessionId = s.id))
            } else if (existing?.state == "WAITING_RECHECK" && result.status == "NORMAL" && dao.baseline(result.assetId) != null && s.startMs > existing.updatedAt) {
                dao.updateIssue(existing.copy(state = "CLOSED", updatedAt = s.endMs))
                dao.insertEvent(IssueEvent(UUID.randomUUID().toString(), existing.id, s.endMs, "RECHECK_PASSED", "", sessionId = s.id))
            }
        }
    }
}
