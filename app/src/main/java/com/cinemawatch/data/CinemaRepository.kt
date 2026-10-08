package com.cinemawatch.data

import androidx.room.withTransaction
import app.fieldwatch.domain.MacUtil
import java.util.UUID

class CinemaRepository(val database: CinemaDatabase) {
    val dao = database.dao()
    suspend fun createCinema(name: String, zoneName: String) = database.withTransaction {
        require(name.isNotBlank() && zoneName.isNotBlank())
        val cinema = Cinema(UUID.randomUUID().toString(), name.trim().take(80))
        dao.insertCinema(cinema)
        dao.insertZone(Zone(UUID.randomUUID().toString(), cinema.id, zoneName.trim().take(80)))
    }
    suspend fun createZone(cinemaId: String, name: String) {
        require(name.isNotBlank())
        dao.insertZone(Zone(UUID.randomUUID().toString(), cinemaId, name.trim().take(80)))
    }
    suspend fun register(zoneId: String, name: String, radio: String, address: String, authorized: Boolean) = database.withTransaction {
        require(authorized) { "Only authorized assets may persist radio identifiers" }
        require(name.isNotBlank() && radio in setOf("WIFI", "BLE"))
        val normalized = MacUtil.normalize(address)
        require(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(normalized))
        val asset = CinemaAsset(UUID.randomUUID().toString(), zoneId, name.trim().take(80), true)
        dao.insertAsset(asset)
        dao.insertBinding(RadioBinding(UUID.randomUUID().toString(), asset.id, radio, normalized))
    }
    suspend fun save(session: Inspection, results: List<AssetResult>) = database.withTransaction {
        dao.insertSession(session)
        dao.insertResults(results)
    }
}
