package com.cinemawatch.domain

/** Monotonic microsecond timestamps prevent duplicate OS callbacks from counting as new scans. */
class WifiBatchTracker {
    private var latest = Long.MIN_VALUE
    fun accept(timestamps: Collection<Long>): Boolean {
        val timestamp = timestamps.maxOrNull() ?: return false
        if (timestamp <= latest) return false
        latest = timestamp
        return true
    }
}

object SamplingQuality {
    fun complete(elapsed: Int, requested: Int, drops: Int) = elapsed in requested..requested + 5 && drops == 0
    fun bleHealthy(complete: Boolean, events: Int, firstSecond: Int?, lastSecond: Int?, requested: Int, failed: Boolean) =
        complete && !failed && events >= 10 && firstSecond != null && lastSecond != null &&
            lastSecond - firstSecond >= requested / 2 && lastSecond >= requested - 15
}
