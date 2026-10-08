package com.cinemawatch.domain

import kotlin.math.roundToInt

enum class AssetStatus { LEARNING, NORMAL, WEAK, UNSTABLE, MISSING, REVIEW, UNAVAILABLE }
data class Evaluation(val status: AssetStatus, val median: Int?, val spread: Int, val misses: Int, val baseline: Int?)

object InspectionPolicy {
    fun median(values: List<Int>): Int? = values.sorted().takeIf { it.isNotEmpty() }?.let { v ->
        if (v.size % 2 == 1) v[v.size / 2] else ((v[v.size / 2 - 1] + v[v.size / 2]) / 2.0).roundToInt()
    }
    /** Valid receiving RSSI only. Never count positive/sentinel values as evidence. */
    fun validRssi(value: Int) = value in -120..-1
    fun evaluate(samples: List<Int>, priorHealthyMedians: List<Int>, previousMisses: Int, healthy: Boolean): Evaluation {
        val valid = samples.filter(::validRssi)
        val m = median(valid)
        val spread = if (valid.isEmpty()) 0 else valid.max() - valid.min()
        val baseline = if (priorHealthyMedians.size >= 3) median(priorHealthyMedians.take(5)) else null
        if (!healthy) return Evaluation(AssetStatus.UNAVAILABLE, m, spread, previousMisses, baseline)
        if (m == null) {
            val misses = previousMisses + 1
            return Evaluation(if (misses >= 2) AssetStatus.MISSING else AssetStatus.REVIEW, null, 0, misses, baseline)
        }
        val status = when {
            baseline == null -> AssetStatus.LEARNING
            baseline - m >= 12 -> AssetStatus.WEAK
            valid.size >= 5 && spread >= 18 -> AssetStatus.UNSTABLE
            else -> AssetStatus.NORMAL
        }
        return Evaluation(status, m, spread, 0, baseline)
    }
}

/** Transient per-session keys; nothing in this class is serialized or written to disk. */
class PrivacyWindow(private val maxKeys: Int = 4096) {
    private val wifi = HashSet<String>()
    private val ble = HashSet<String>()
    private val groups = HashMap<String, String>()
    var dropped = 0; private set
    var discarded = 0; private set
    fun accept(kind: String, address: String, rssi: Int, fresh: Boolean, registered: Boolean): Boolean {
        if (!InspectionPolicy.validRssi(rssi) || !fresh || address.isBlank()) { discarded++; return false }
        if (registered) return true
        val set = if (kind == "WIFI") wifi else ble
        if (address !in set && wifi.size + ble.size >= maxKeys) { dropped++; return false }
        set += address
        return true
    }
    fun classify(kind: String, address: String, group: String) {
        if (address in (if (kind == "WIFI") wifi else ble)) groups["$kind:$address"] = group
    }
    fun exclude(kind: String, address: String) {
        (if (kind == "WIFI") wifi else ble).remove(address)
        groups.remove("$kind:$address")
    }
    fun groupCounts(): Map<Pair<String, String>, Int> = groups.entries.groupingBy { (key, group) -> key.substringBefore(":") to group }.eachCount()
    fun discard() { discarded++ }
    fun counts() = wifi.size to ble.size
    fun clear() { wifi.clear(); ble.clear(); groups.clear(); dropped = 0; discarded = 0 }
}
