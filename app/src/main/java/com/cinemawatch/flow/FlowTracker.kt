package com.cinemawatch.flow

import com.cinemawatch.domain.InspectionPolicy

data class FlowTarget(val id: String, val label: String, val address: String)
data class FlowNode(val id: String, val name: String, val zone: String, val offset: Int, val at: Long, val healthy: Boolean, val sequence: Long)
data class FlowReading(val target: String, val rssi: Int, val at: Long)
data class FlowPresence(val target: String, val label: String, val zone: String?, val margin: Int, val receivers: Int)
data class FlowSignal(val label: String, val node: String, val zone: String, val median: Int, val samples: Int)
data class FlowTransition(val label: String, val from: String, val to: String, val at: Long)
data class FlowTruth(val label: String, val expected: String, val observed: String?, val at: Long)
data class FlowSnapshot(val nodes: List<FlowNode> = emptyList(), val presence: List<FlowPresence> = emptyList(), val signals: List<FlowSignal> = emptyList(), val transitions: List<FlowTransition> = emptyList(), val dwellSeconds: Map<String, Long> = emptyMap(), val truths: List<FlowTruth> = emptyList())

/** Authorized experiment tags only. No unknown addresses, people inference, or cross-session identity. */
class FlowTracker(private val targets: List<FlowTarget>, private val zones: Set<String>) {
    private val nodes = LinkedHashMap<String, FlowNode>()
    private val samples = HashMap<Pair<String, String>, MutableList<FlowReading>>()
    private data class State(var zone: String? = null, var pending: String? = null, var wins: Int = 0)
    private val states = targets.associate { it.id to State() }
    private val transitions = ArrayList<FlowTransition>()
    private val truths = ArrayList<FlowTruth>()
    private val dwell = HashMap<String, Long>()
    private var evaluatedAt = 0L
    private var last = FlowSnapshot()
    init { require(targets.size in 1..16 && targets.map { it.id }.distinct().size == targets.size && zones.size in 2..100) }
    @Synchronized fun ingest(node: FlowNode, readings: List<FlowReading>, now: Long) {
        require(node.zone in zones && node.name.length in 1..60 && node.id.length in 1..80 && node.offset in -20..20 && node.sequence >= 0 && readings.size <= 512)
        nodes.entries.filter { now - it.value.at > 60000 }.map { it.key }.forEach { id -> nodes.remove(id); samples.keys.filter { it.second == id }.forEach(samples::remove) }
        val prior = nodes[node.id]
        require(prior == null || node.sequence > prior.sequence) { "Replay or duplicate sequence" }
        require(prior != null || nodes.size < 16)
        if (prior != null && (prior.zone != node.zone || prior.offset != node.offset)) samples.keys.filter { it.second == node.id }.forEach(samples::remove)
        nodes[node.id] = node.copy(at = now)
        readings.forEach { r ->
            if (r.target !in states || !InspectionPolicy.validRssi(r.rssi) || now - r.at !in -1500L..6000L) return@forEach
            val buffer = samples.getOrPut(r.target to node.id) { ArrayList() }
            // One observation per timestamp prevents retransmitted packets from inflating coverage.
            if (buffer.none { it.at == r.at }) buffer += r
            buffer.removeAll { now - it.at > 6000 }
            while (buffer.size > 32) buffer.removeAt(0)
        }
    }
    @Synchronized fun evaluate(now: Long): FlowSnapshot {
        if (evaluatedAt != 0L && now < evaluatedAt) { states.values.forEach { it.zone=null;it.pending=null;it.wins=0 }; samples.clear() }
        val elapsed = if (evaluatedAt == 0L) 0L else (now - evaluatedAt).coerceIn(0, 2500)
        evaluatedAt = now
        val healthy = nodes.values.filter { it.healthy && now - it.at in 0..10000 }
        val signals = ArrayList<FlowSignal>()
        val presence = targets.map { target ->
            val evidence = healthy.mapNotNull { node ->
                val valid = samples[target.id to node.id].orEmpty().filter { now - it.at in 0..6000 }
                if (valid.size < 3) null else {
                    val m = requireNotNull(InspectionPolicy.median(valid.map { it.rssi })) + node.offset
                    signals += FlowSignal(target.label, node.name, node.zone, m, valid.size)
                    node.zone to m
                }
            }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }.entries.sortedByDescending { it.value }
            val margin = if (evidence.size >= 2) evidence[0].value - evidence[1].value else 0
            val winner = if (evidence.size >= 2 && margin >= 6) evidence.first().key else null
            val state = states.getValue(target.id)
            if (winner == null) { state.zone=null;state.pending=null;state.wins=0 }
            else {
                if (winner == state.zone) { dwell[winner] = (dwell[winner] ?: 0) + elapsed;state.pending=null;state.wins=0 }
                else {
                    if (state.pending == winner) state.wins++ else { state.pending=winner;state.wins=1 }
                    if (state.wins >= 2) {
                        state.zone?.let { from -> transitions += FlowTransition(target.label, from, winner, now); if (transitions.size > 1000) transitions.removeAt(0) }
                        state.zone=winner;state.pending=null;state.wins=0
                    }
                }
            }
            FlowPresence(target.id, target.label, state.zone.takeIf { winner != null }, margin, evidence.size)
        }
        last = FlowSnapshot(nodes.values.map { it.copy(healthy=it.healthy && now-it.at in 0..10000) },presence,signals,transitions.toList(),dwell.mapValues { it.value/1000 },truths.toList())
        return last
    }
    @Synchronized fun snapshot() = last.copy(truths=truths.toList())
    @Synchronized fun markTruth(target: String, expected: String, now: Long) {
        require(expected in zones && target in states && truths.size < 500)
        val presence = last.presence.find { it.target == target }
        val label = targets.first { it.id == target }.label
        truths += FlowTruth(label, expected, presence?.zone, now)
    }
}
