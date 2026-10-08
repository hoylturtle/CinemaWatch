package com.cinemawatch.privacy

import com.cinemawatch.domain.RadioObservation
import com.cinemawatch.domain.RadioType
import com.cinemawatch.domain.RfDensityBucket

/**
 * Prototype only. Must be adapted to real Fieldwatch observations after P0 audit.
 * Raw customer radio identifiers must not be persisted by the Flow layer.
 */
class PrivacyFilter(private val bucketMs: Long = 60_000L) {
    fun aggregate(observations: List<RadioObservation>): List<RfDensityBucket> =
        observations
            .filter { it.zoneId != null && it.rssi in -120..0 }
            .groupBy { Triple(it.zoneId!!, it.probeId, (it.timestampMs / bucketMs) * bucketMs) }
            .map { (key, rows) ->
                val wifi = rows.filter { it.radioType == RadioType.WIFI }
                    .mapNotNull { it.stableKey }.toSet().size
                val ble = rows.filter { it.radioType == RadioType.BLE }
                    .mapNotNull { it.stableKey }.toSet().size
                RfDensityBucket(
                    cinemaId = rows.first().cinemaId,
                    zoneId = key.first,
                    probeId = key.second,
                    windowStartMs = key.third,
                    windowEndMs = key.third + bucketMs,
                    wifiCount = wifi,
                    bleCount = ble,
                    effectiveRfDensity = wifi + ble.toDouble(),
                    confidence = if (rows.size >= 20) 0.8 else 0.5
                )
            }
}
