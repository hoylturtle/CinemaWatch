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
import com.cinemawatch.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SampleRequest(val zoneId: String, val mode: String = "INSPECTION", val seconds: Int = 120, val planned: Int? = null, val actual: Int? = null, val gate: Int? = null, val point: String = "", val demo: Boolean = false)
data class SignatureHit(val name: String, val category: SignatureClass)

data class LiveRadio(val kind: String, val address: String, val name: String, val rssi: Int, val vendor: String?, val signatureClass: SignatureClass?, val lastAt: Long, val assetId: String?, val history: List<Int>, val guess: SignalGuess = SignalGuess(SignalGroup.UNKNOWN, GuessEvidence.NONE, "UNKNOWN"), val signatureHits: List<SignatureHit> = emptyList(), val ecosystem: SignalGroup? = null, val firstAt: Long = lastAt, val frequencyMhz: Int = 0) {
    val key get() = "$kind:$address"
    val categories = SignalGrouping.memberships(kind, guess, signatureHits.map { it.category }.toSet()) + setOfNotNull(ecosystem)
}
data class ScanUi(val running: Boolean = false, val saving: Boolean = false, val saveFailed: Boolean = false,
    val request: SampleRequest? = null, val remaining: Int = 0, val wifiCount: Int = 0, val bleCount: Int = 0,
    val assetCount: Int = 0, val wifiBatches: Int = 0, val bleEvents: Int = 0, val discarded: Int = 0, val dropped: Int = 0,
    val wifiHealthy: Boolean = false, val bleHealthy: Boolean = false, val bleError: Boolean = false,
    val groups: Map<Pair<String, String>, Int> = emptyMap(),
    val live: List<LiveRadio> = emptyList(), val latestSession: String? = null)

/** One session, one zone. Raw observations stay only in a bounded in-memory channel/map. */
class ScanEngine(private val context: Context, private val repository: CinemaRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ScanUi())
    val state = mutable.asStateFlow()
    private var wifi: WifiRadio? = null
    private var ble: BleRadio? = null
    private var loop: Job? = null
    private var radioLoop: Job? = null
    private var expectedAssetIds = emptySet<String>()
    private var wifiBatchTracker = WifiBatchTracker()
    private var wifiCacheAt = Long.MIN_VALUE
    private var wifiTimes = emptyMap<String, Long>()
    private var firstBleSecond: Int? = null
    private var lastBleSecond: Int? = null
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
    private data class PendingSave(val session: Inspection, val assets: List<CinemaAsset>, val bindings: List<RadioBinding>, val samples: Map<String, List<Int>>, val groups: List<SignalGroupCount>)
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
            expectedAssetIds = assets.map { it.id }.toSet()
        } catch (e: Exception) {
            mutable.value = state.value.copy(saving = false)
            throw e
        } finally { starting = false }
        window = PrivacyWindow(); live.clear(); assetSamples.clear()
        wifiBatchTracker = WifiBatchTracker(); wifiCacheAt = Long.MIN_VALUE; wifiTimes = emptyMap()
        firstBleSecond = null; lastBleSecond = null
        queueDrops = 0; wifiBatches = 0; bleEvents = 0; bleError = false
        startedWall = System.currentTimeMillis(); startedElapsed = SystemClock.elapsedRealtime()
        mutable.value = ScanUi(running = true, request = request, remaining = request.seconds)
        if (!request.demo) {
            withContext(Dispatchers.IO) { runCatching { RadioDb.init(context) } }
            val channel = Channel<Observation>(512)
            inbound = channel
            pump = scope.launch { for (observation in channel) accept(observation) }
            val offer: (Observation) -> Unit = { observation ->
                // Capture the specific channel; late old callbacks cannot enter a new session.
                if (channel.trySend(observation).isFailure && inbound === channel) synchronized(this) { queueDrops++ }
            }
            wifi = WifiRadio(context, offer, { _, _ -> }, { batch ->
                scope.launch {
                    if (inbound === channel && state.value.running) {
                        refreshWifiTimes()
                        if (inbound === channel && wifiBatchTracker.accept(batch.filter { freshWifi(it) }.mapNotNull { wifiTimes[MacUtil.normalize(it.mac)] })) wifiBatches++
                    }
                }
            })
            ble = BleRadio(context, offer, {
                scope.launch { if (inbound === channel) bleError = true }
            })
            runCatching { wifi?.start() }.onFailure { wifiBatches = 0 }
            runCatching { ble?.start(ScanIntensity.BALANCED) }.onFailure { bleError = true }
        }
        if (!request.demo) radioLoop = scope.launch {
            while (isActive && state.value.running) {
                runCatching { wifi?.requestScan(30_000) }
                if (ble?.needsRestart() == true) {
                    runCatching { ble?.stop() }
                    delay(ble?.restartBackoffMs() ?: 2500)
                    if (state.value.running) runCatching { ble?.start(ScanIntensity.BALANCED) }
                }
                delay(1000)
            }
        }
        loop = scope.launch {
            while (isActive && state.value.running) {
                val elapsed = ((SystemClock.elapsedRealtime() - startedElapsed) / 1000).toInt()
                if (elapsed >= request.seconds) { finish(); break }
                if (request.demo) generateDemo(elapsed)
                publish(request.seconds - elapsed)
                delay(1000)
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun refreshWifiTimes() {
        val times = withContext(Dispatchers.IO) {
            runCatching { (context.getSystemService(Context.WIFI_SERVICE) as WifiManager).scanResults }
                .getOrDefault(emptyList()).associate { MacUtil.normalize(it.BSSID.orEmpty()) to it.timestamp }
        }
        wifiTimes = times; wifiCacheAt = SystemClock.elapsedRealtime()
    }
    private fun freshWifi(observation: Observation): Boolean {
        if (!observation.fresh) return false
        val stamp = wifiTimes[MacUtil.normalize(observation.mac)] ?: return false
        return stamp >= startedElapsed * 1000 && SystemClock.elapsedRealtime() * 1000 - stamp in 0..15_000_000
    }

    private suspend fun accept(observation: Observation) {
        val request = state.value.request ?: return
        if (!state.value.running || SystemClock.elapsedRealtime() - startedElapsed >= request.seconds * 1000L) return
        val kind = observation.kind.name
        val address = runCatching { RadioAddress.normalize(observation.mac, kind) }.getOrNull() ?: run { window.discard(); return }
        if (kind == "WIFI" && observation.fresh && (wifiCacheAt == Long.MIN_VALUE || SystemClock.elapsedRealtime() - wifiCacheAt > 1000)) refreshWifiTimes()
        val binding = bindings.firstOrNull { it.radio == kind && it.address == address }
        val fresh = if (kind == "WIFI") freshWifi(observation) else true
        if (!window.accept(kind, address, observation.rssi, fresh, registered = binding != null)) return
        if (kind == "BLE") {
            bleEvents++; bleError = false
            val second = ((SystemClock.elapsedRealtime() - startedElapsed) / 1000).toInt()
            if (firstBleSecond == null) firstBleSecond = second
            lastBleSecond = second
        }
        window.classify(kind, address, if (kind == "WIFI") "ACCESS_POINT" else "UNKNOWN")
        val assetId = binding?.assetId
        if (assetId != null && assets.any { it.id == assetId }) assetSamples.getOrPut(assetId) { mutableListOf() }.let { samples ->
            if (samples.size >= 240) samples.removeAt(0)
            samples += observation.rssi
        }
        val key = "$kind:$address"
        val old = live[key]
        val vendor = if (kind == "BLE" && observation.facts.addressType != "Public") null else RadioDb.vendorForMac(address)
        val company = observation.manufacturerId?.let { RadioDb.company(it) }
        // A cached null is still a completed classification. Do not rerun catalog matching on every packet.
        val hits = if (old != null) old.signatureHits else if (live.size >= 512) emptyList() else withContext(Dispatchers.Default) { classify(observation, vendor) }
        if (!state.value.running || SystemClock.elapsedRealtime() - startedElapsed >= request.seconds * 1000L) return
        val currentAssetId = bindings.firstOrNull { it.radio == kind && it.address == address }?.assetId
        if (currentAssetId != null) window.exclude(kind, address)
        if (currentAssetId != null && assetId == null && assets.any { it.id == currentAssetId }) assetSamples.getOrPut(currentAssetId) { mutableListOf() }.add(observation.rssi)
        val guess = SignalGrouping.guess(kind, observation.name, vendor, company, hits.firstOrNull()?.category)
        window.classify(kind, address, guess.group.name)
        if (old == null && live.size >= 512) return
        live[key] = LiveRadio(kind, address, observation.name.take(48), observation.rssi, vendor ?: company, hits.firstOrNull()?.category,
            System.currentTimeMillis(), currentAssetId, (old?.history.orEmpty() + observation.rssi).takeLast(20), guess, hits, SignalGrouping.guess(kind, "", vendor, company, null).group.takeIf { it in SignalGrouping.ecosystems }, firstAt = old?.firstAt ?: System.currentTimeMillis(), frequencyMhz = observation.frequencyMhz)
    }

    /** Registration changes are explicit authorization; apply them immediately to live labels/counts. */
    suspend fun refreshAssets() {
        val request = state.value.request ?: return
        if (!state.value.running) return
        val latestBindings = repository.dao.allBindings()
        val latestAssets = repository.dao.assetsIn(request.zoneId)
        if (!state.value.running || state.value.request != request) return
        bindings = latestBindings; assets = latestAssets
        bindings.forEach { window.exclude(it.radio, it.address) }
        live.entries.forEach { (key, radio) ->
            val binding = bindings.firstOrNull { it.radio == radio.kind && it.address == radio.address }
            if (binding != null) {
                window.exclude(radio.kind, radio.address)
                if (assets.any { it.id == binding.assetId } && radio.assetId == null) assetSamples[binding.assetId] = radio.history.toMutableList()
            }
            live[key] = radio.copy(assetId = binding?.assetId)
        }
        publish(state.value.remaining)
    }

    private fun classify(o: Observation, vendor: String?): List<SignatureHit> {
        val sighting = Sighting(key = "${o.kind}:${o.mac}", kind = o.kind, mac = o.mac, name = o.name,
            rssi = o.rssi, rssiMin = o.rssi, rssiMax = o.rssi, channel = o.channel, frequencyMhz = o.frequencyMhz,
            vendor = vendor, randomized = if (o.kind.name == "BLE") o.facts.addressType != "Public" else MacUtil.isRandomized(o.mac), hiddenSsid = o.hiddenSsid,
            serviceUuids = o.serviceUuids, manufacturerId = o.manufacturerId, manufacturerDataHex = o.manufacturerDataHex,
            rawHex = o.rawHex, extras = "", firstSeen = o.at, lastSeen = o.at, hitCount = 1, fleetIds = emptySet(),
            rssiHistory = emptyList(), presence = emptyList(), vendorIeOuis = o.vendorIeOuis, facts = o.facts)
        val ids = engine.match(listOf(sighting), catalog)[sighting.key].orEmpty()
        return catalog.filter { it.id in ids }.map { SignatureHit(it.name, it.kind) }
    }

    private fun generateDemo(tick: Int) {
        wifiBatches = tick / 4 + 1; bleEvents += 2
        if (firstBleSecond == null) firstBleSecond = tick
        lastBleSecond = tick
        listOf("WIFI" to "02:00:00:00:00:01", "BLE" to "02:00:00:00:00:02", "BLE" to "02:00:00:00:00:03").forEachIndexed { i, (kind, address) ->
            val rssi = -48 - i * 11 + tick % 5
            window.accept(kind, address, rssi, true, false)
            val old = live["$kind:$address"]
            val guess = SignalGrouping.guess(kind, "", null, null, null)
            window.classify(kind, address, guess.group.name)
            live["$kind:$address"] = LiveRadio(kind, address, listOf(com.cinemawatch.R.string.demo_ap, com.cinemawatch.R.string.demo_sensor, com.cinemawatch.R.string.demo_broadcast).map { com.cinemawatch.AppLanguage.context(context).getString(it) }[i], rssi, null, null,
                System.currentTimeMillis(), null, (old?.history.orEmpty() + rssi).takeLast(20), guess, firstAt = old?.firstAt ?: System.currentTimeMillis())
        }
    }

    private fun publish(remaining: Int) {
        val (w, b) = window.counts()
        mutable.value = state.value.copy(remaining = remaining.coerceAtLeast(0), wifiCount = w, bleCount = b,
            assetCount = assetSamples.size, wifiBatches = wifiBatches, bleEvents = bleEvents,
            discarded = window.discarded, dropped = window.dropped + synchronized(this) { queueDrops },
            wifiHealthy = wifiBatches > 0, bleHealthy = bleEvents > 0 && !bleError, bleError = bleError,
            groups = window.groupCounts(), live = live.values.sortedByDescending { it.rssi })
    }

    fun stop() { if (state.value.running) scope.launch { finish() } }

    private suspend fun finish() {
        if (!state.value.running) return
        loop?.cancel(); loop = null
        radioLoop?.cancel(); radioLoop = null
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
        val complete = SamplingQuality.complete(elapsed, request.seconds, snap.dropped)
        val wifiHealthy = complete && wifiBatches >= 2
        val bleHealthy = SamplingQuality.bleHealthy(complete, bleEvents, firstBleSecond, lastBleSecond, request.seconds, bleError)
        val session = Inspection(UUID.randomUUID().toString(), request.zoneId, request.mode, startedWall,
            System.currentTimeMillis(), elapsed, snap.wifiCount, snap.bleCount, snap.assetCount,
            wifiBatches, bleEvents, snap.discarded, snap.dropped, wifiHealthy, bleHealthy, request.demo,
            request.planned, request.actual, request.gate, request.point.trim().take(80), requestedSeconds = request.seconds)
        val samples = assetSamples.mapValues { it.value.toList() }
        val grouped = snap.groups.map { (key, count) -> SignalGroupCount(session.id, key.second, key.first, count) }
        // Clear all unregistered addresses/names/payloads before any persistence call.
        live.clear(); window.clear(); assetSamples.clear(); bindings = bindings.toList()
        mutable.value = snap.copy(running = false, saving = true, live = emptyList(), remaining = 0)
        ContextCompat.getMainExecutor(context).execute {
            context.stopService(android.content.Intent(context, InspectionService::class.java))
        }
        pending = PendingSave(session, assets.filter { it.id in expectedAssetIds }, bindings.toList(), samples, grouped)
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
            val confirmed = repository.dao.baseline(asset.id)?.rssi
            val e = InspectionPolicy.evaluate(p.samples[asset.id].orEmpty(), emptyList(), history.firstOrNull()?.consecutiveMisses ?: 0, healthy, confirmed)
            AssetResult(s.id, asset.id, s.endMs, e.status.name, e.median, e.spread, e.misses, e.baseline)
        }
        repository.save(s, results, p.groups)
    }
}
