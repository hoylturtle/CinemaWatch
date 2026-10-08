package com.cinemawatch.domain

enum class RadioType { WIFI, BLE }
enum class Criticality { LOW, MEDIUM, HIGH, CRITICAL }
enum class AssetStatus { NORMAL, MISSING, WEAK, NEW, MOVED, UNSTABLE, ROGUE, UNKNOWN }

data class RadioObservation(
    val timestampMs: Long,
    val cinemaId: String,
    val zoneId: String?,
    val probeId: String,
    val radioType: RadioType,
    val stableKey: String?,
    val rssi: Int,
    val vendor: String? = null,
    val signature: String? = null
)

data class RfDensityBucket(
    val cinemaId: String,
    val zoneId: String,
    val probeId: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val wifiCount: Int,
    val bleCount: Int,
    val effectiveRfDensity: Double,
    val confidence: Double
)
