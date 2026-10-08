package com.cinemawatch.radio

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.fieldwatch.domain.*
import app.fieldwatch.radio.BleRadio
import app.fieldwatch.radio.WifiRadio
import com.cinemawatch.data.*
import com.cinemawatch.domain.InspectionPolicy
import com.cinemawatch.domain.PrivacyWindow
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SampleRequest(val zoneId: String, val mode: String = "INSPECTION", val seconds: Int = 120, val planned: Int? = null, val actual: Int? = null, val gate: Int? = null, val point: String = "", val demo: Boolean = false)
data class LiveRadio(val kind: String, val address: String, val name: String, val rssi: Int, val vendor: String?, val signatureClass: SignatureClass?, val lastAt: Long, val assetId: String?, val history: List<Int>) {
    val key get() = "$kind:$address"
}
data class ScanUi(val running: Boolean = false, val saving: Boolean = false, val saveFailed: Boolean = false,
    val request: SampleRequest? = null, val remaining: Int = 0, val wifiCount: Int = 0, val bleCount: Int = 0,
    val assetCount: Int = 0, val wifiBatches: Int = 0, val bleEvents: Int = 0, val discarded: Int = 0, val dropped: Int = 0,
    val wifiHealthy: Boolean = false, val bleHealthy: Boolean = false, val bleError: Boolean = false,
    val live: List<LiveRadio> = emptyList(), val latestSession: String? = null)

/** One session, one zone. Raw observations stay only in a bounded in-memory channel/map. */
class ScanEngine(private val context: Context, private val repository: CinemaRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ScanUi())
    val state = mutable.asStateFlow()
    private var wifi: WifiRadio? = null
    private var ble: BleRadio? = null
    private var loop: Job? = null
    private var pump: Job? = null
    private var inbound: Channel<Observation>? = null
    private var window = PrivacyWindow()
    private val live = LinkedHashMap<String, LiveRadio>()
    private val assetSamples = HashMap<String, MutableList<Int>>()
    private var bindings = emptyList<RadioBinding>()
    private var assets = emptyList<CinemaAsset>()
    private var startedWall = 0L
    private var startedElapsed = 0L
    private var queueDrops = 0
    private var wifiBatches = 0
    private var bleEvents = 0
    private var bleError = false
    private val engine = SignatureEngine()
    private val catalog by lazy { DefaultCatalog.fleets().filter { it.enabled } }
    private data class PendingSave(val session: Inspection, val assets: List<CinemaAsset>, val bindings: List<RadioBinding>, val samples: Map<String, List<Int>>)
    private var pending: PendingSave? = null
    private var starting = false

    suspend fun start(request: SampleRequest) {
        if (starting || state.value.running || state.value.saving || pending != null) return
        require(request.mode in setOf("INSPECTION", "LAB"))
        require(request.seconds in setOf(120, 180) || request.demo && request.seconds == 15)
        require(request.zoneId.isNotBlank())
        starting = true
        mutable.value = state.value.copy(saving = true)
        try {
            bindings = repository.dao.allBindings()
            assets = repository.dao.assetsIn(request.zoneId)
        } catch (e: Exception) {
            mutable.value = state.value.copy(saving = false)
            throw e
        } finally { starting = false }
        window = PrivacyWindow(); live.clear(); assetSamples.clear()
        queueDrops = 0; wifiBatches = 0; bleEvents = 0; bleError = false
        startedWall = System.currentTimeMillis(); startedElapsed = SystemClock.elapsedRealtime()
        mutable.value = ScanUi(running = true, request = request, remaining = request.seconds)
        if (!request.demo) {
            runCatching { RadioDb.init(context) }
            val channel = Channel<Observation>(512)
            inbound = channel
            pump = scope.launch { for (observation in channel) accept(observation) }
            val offer: (Observation) -> Unit = { observation ->
                // Capture the specific channel; late old callbacks cannot enter a new session.
                if (channel.trySend(observation).isFailure && inbound === channel) synchronized(this) { queueDrops++ }
            }
            wifi = WifiRadio(context, offer, { _, _ -> }, { batch ->
                scope.launch {
                    if (inbound === channel && state.value.running && batch.any { freshWifi(it) }) wifiBatches++
                }
            })
            ble = BleRadio(context, offer, {
                scope.launch { if (inbound === channel) bleError = true }
            })
            runCatching { wifi?.start() }.onFailure { wifiBatches = 0 }
            runCatching { ble?.start(ScanIntensity.BALANCED) }.onFailure { bleError = true }
        }
        loop = scope.launch {
            while (isActive && state.value.running) {
                val elapsed = ((SystemClock.elapsedRealtime() - startedElapsed) / 1000).toInt()
                if (elapsed >= request.seconds) { finish(); break }
                if (request.demo) generateDemo(elapsed)
                else {
                    runCatching { wifi?.requestScan(30_000) }
                    if (ble?.needsRestart() == true) {
                        runCatching { ble?.stop() }
                        delay(ble?.restartBackoffMs() ?: 2500)
                        if (state.value.running) runCatching { ble?.start(ScanIntensity.BALANCED) }
                    }
                }
                publish(request.seconds - elapsed)
                delay(1000)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun freshWifi(observation: Observation): Boolean {
        if (!observation.fresh) return false
        val nowMicros = SystemClock.elapsedRealtime() * 1000
        val results = runCatching { (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).scanResults }.getOrDefault(emptyList())
        val result = results.firstOrNull { MacUtil.normalize(it.BSSID.orEmpty()) == MacUtil.normalize(observation.mac) } ?: return false
        return result.timestamp >= startedElapsed * 1000 && nowMicros - result.timestamp in 0..15_000_000
    }

    private fun accept(observation: Observation) {
        if (!state.value.running) return
        val kind = observation.kind.name
        val address = MacUtil.normalize(observation.mac)
        val binding = bindings.firstOrNull { it.radio == kind && it.address == address }
        val fresh = if (kind == "WIFI") freshWifi(observation) else true
        if (!window.accept(kind, address, observation.rssi, fresh, registered = binding != null)) return
        if (kind == "BLE") { bleEvents++; bleError = false }
        // Zone-specific expected assets; radios registered in other zones are excluded from Flow.
        val assetId = binding?.assetId?.takeIf { id -> assets.any { it.id == id } }
        if (assetId != null) assetSamples.getOrPut(assetId) { mutableListOf() }.let { samples ->
            if (samples.size >= 240) samples.removeAt(0)
            samples += observation.rssi
        }
        val key = "$kind:$address"
        val old = live[key]
        if (old == null && live.size >= 512) return
        val vendor = if (kind == "BLE" && MacUtil.isLocallyAdministered(address)) null else RadioDb.vendorForMac(address)
        val sig = old?.signatureClass ?: classify(observation, vendor)
        live[key] = LiveRadio(kind, address, observation.name.take(48), observation.rssi, vendor, sig,
            System.currentTimeMillis(), assetId, (old?.history.orEmpty() + observation.rssi).takeLast(20))
    }

    private fun classify(o: Observation, vendor: String?): SignatureClass? {
        val sighting = Sighting(key = "${o.kind}:${o.mac}", kind = o.kind, mac = o.mac, name = o.name,
            rssi = o.rssi, rssiMin = o.rssi, rssiMax = o.rssi, channel = o.channel, frequencyMhz = o.frequencyMhz,
            vendor = vendor, randomized = MacUtil.isRandomized(o.mac), hiddenSsid = o.hiddenSsid,
            serviceUuids = o.serviceUuids, manufacturerId = o.manufacturerId, manufacturerDataHex = o.manufacturerDataHex,
            rawHex = o.rawHex, extras = "", firstSeen = o.at, lastSeen = o.at, hitCount = 1, fleetIds = emptySet(),
            rssiHistory = emptyList(), presence = emptyList(), vendorIeOuis = o.vendorIeOuis, facts = o.facts)
        val ids = engine.match(listOf(sighting), catalog)[sighting.key].orEmpty()
        return catalog.firstOrNull { it.id in ids }?.kind
    }

    private fun generateDemo(tick: Int) {
        wifiBatches = tick / 4 + 1; bleEvents += 2
        listOf("WIFI" to "02:00:00:00:00:01", "BLE" to "02:00:00:00:00:02", "BLE" to "02:00:00:00:00:03").forEachIndexed { i, (kind, address) ->
            val rssi = -48 - i * 11 + tick % 5
            window.accept(kind, address, rssi, true, false)
            val old = live["$kind:$address"]
            live["$kind:$address"] = LiveRadio(kind, address, listOf(com.cinemawatch.R.string.demo_ap, com.cinemawatch.R.string.demo_sensor, com.cinemawatch.R.string.demo_broadcast).map { com.cinemawatch.AppLanguage.context(context).getString(it) }[i], rssi, null, null,
                System.currentTimeMillis(), null, (old?.history.orEmpty() + rssi).takeLast(20))
        }
    }

    private fun publish(remaining: Int) {
        val (w, b) = window.counts()
        mutable.value = state.value.copy(remaining = remaining.coerceAtLeast(0), wifiCount = w, bleCount = b,
            assetCount = assetSamples.size, wifiBatches = wifiBatches, bleEvents = bleEvents,
            discarded = window.discarded, dropped = window.dropped + synchronized(this) { queueDrops },
            wifiHealthy = wifiBatches > 0, bleHealthy = bleEvents > 0 && !bleError, bleError = bleError,
            live = live.values.sortedByDescending { it.rssi })
    }

    fun stop() { if (state.value.running) scope.launch { finish() } }

    private suspend fun finish() {
        if (!state.value.running) return
        loop?.cancel(); loop = null
        runCatching { wifi?.stop() }; runCatching { ble?.stop() }
        wifi = null; ble = null
        inbound?.close(); pump?.cancel()
        while (inbound?.tryReceive()?.isSuccess == true) queueDrops++
        inbound = null; pump = null
        val elapsed = ((SystemClock.elapsedRealtime() - startedElapsed) / 1000).toInt()
        publish(0)
        val snap = state.value
        val request = snap.request ?: return
        // Healthy absence requires a full sample, no drops, at least two fresh Wi-Fi batches,
        // or sustained BLE evidence. A broken/silent scanner must never increment asset misses.
        val complete = elapsed >= request.seconds && snap.dropped == 0
        val wifiHealthy = complete && wifiBatches >= 2
        val bleHealthy = complete && bleEvents >= 10 && !bleError
        val session = Inspection(UUID.randomUUID().toString(), request.zoneId, request.mode, startedWall,
            System.currentTimeMillis(), elapsed, snap.wifiCount, snap.bleCount, snap.assetCount,
            wifiBatches, bleEvents, snap.discarded, snap.dropped, wifiHealthy, bleHealthy, request.demo,
            request.planned, request.actual, request.gate, request.point.trim().take(80))
        val samples = assetSamples.mapValues { it.value.toList() }
        // Clear all unregistered addresses/names/payloads before any persistence call.
        live.clear(); window.clear(); assetSamples.clear(); bindings = bindings.toList()
        mutable.value = snap.copy(running = false, saving = true, live = emptyList(), remaining = 0)
        ContextCompat.getMainExecutor(context).execute {
            context.stopService(android.content.Intent(context, InspectionService::class.java))
        }
        pending = PendingSave(session, assets.toList(), bindings.toList(), samples)
        withContext(NonCancellable) {
            try {
                persistPending()
                pending = null
                mutable.value = state.value.copy(saving = false, latestSession = session.id)
            } catch (_: Exception) {
                mutable.value = state.value.copy(saving = false, saveFailed = true)
            } finally { bindings = emptyList(); assets = emptyList() }
        }
    }
    fun retrySave() = scope.launch {
        val values = pending ?: return@launch
        mutable.value = state.value.copy(saving = true)
        runCatching { persistPending() }.onSuccess {
            pending = null; mutable.value = state.value.copy(saving = false, saveFailed = false, latestSession = values.session.id)
        }.onFailure { mutable.value = state.value.copy(saving = false, saveFailed = true) }
    }
    private suspend fun persistPending() {
        val p = pending ?: return
        val s = p.session
        val results = if (s.demo || s.mode == "LAB") emptyList() else p.assets.map { asset ->
            val history = repository.dao.history(asset.id)
            val radios = p.bindings.filter { it.assetId == asset.id }.map { it.radio }
            val healthy = radios.isNotEmpty() && radios.all { if (it == "WIFI") s.wifiHealthy else s.bleHealthy }
            val past = history.filter { it.status in setOf("NORMAL", "LEARNING") }.mapNotNull { it.medianRssi }
            val e = InspectionPolicy.evaluate(p.samples[asset.id].orEmpty(), past, history.firstOrNull()?.consecutiveMisses ?: 0, healthy)
            AssetResult(s.id, asset.id, s.endMs, e.status.name, e.median, e.spread, e.misses, e.baseline)
        }
        repository.save(s, results)
    }
}
