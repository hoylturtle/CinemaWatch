package com.cinemawatch

import kotlin.math.*

object PlanGeometry {
    fun outline(pin: PlanPin): List<PlanPoint> = if (pin.vertices.isNotEmpty()) pin.vertices else if (pin.width > 0 && pin.height > 0) listOf(
        PlanPoint(pin.x - pin.width / 2, pin.y - pin.height / 2), PlanPoint(pin.x + pin.width / 2, pin.y - pin.height / 2),
        PlanPoint(pin.x + pin.width / 2, pin.y + pin.height / 2), PlanPoint(pin.x - pin.width / 2, pin.y + pin.height / 2)) else emptyList()
    fun snap(point: PlanPoint, previous: PlanPoint?, enabled: Boolean): PlanPoint {
        if (!enabled) return point
        var x = (round(point.x * 50) / 50).coerceIn(0f, 1f); var y = (round(point.y * 50) / 50).coerceIn(0f, 1f)
        if (previous != null) { if (abs(x - previous.x) > abs(y - previous.y)) y = previous.y else x = previous.x }
        return PlanPoint(x, y)
    }
    fun area(points: List<PlanPoint>): Float = abs(points.indices.sumOf { i -> val next = points[(i + 1) % points.size]; (points[i].x * next.y - next.x * points[i].y).toDouble() }.toFloat()) / 2
    fun shape(target: String, points: List<PlanPoint>) = PlanPin(target, points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat(), vertices = points)
    fun move(pin: PlanPin, dx: Float, dy: Float): PlanPin {
        val points = outline(pin).ifEmpty { listOf(PlanPoint(pin.x, pin.y)) }
        val x = dx.coerceIn(-points.minOf { it.x }, 1 - points.maxOf { it.x })
        val y = dy.coerceIn(-points.minOf { it.y }, 1 - points.maxOf { it.y })
        return pin.copy(x = pin.x + x, y = pin.y + y, vertices = pin.vertices.map { PlanPoint(it.x + x, it.y + y) })
    }
    fun contains(points: List<PlanPoint>, p: PlanPoint): Boolean {
        if (points.size < 3) return false
        var inside = false; var j = points.lastIndex
        points.indices.forEach { i ->
            val a = points[i]; val b = points[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
