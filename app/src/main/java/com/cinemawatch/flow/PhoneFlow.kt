package com.cinemawatch.flow

import android.content.Context
import android.os.SystemClock
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.radio.BleRadio
import com.cinemawatch.domain.InspectionPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

internal data class FlowConfig(val id:String,val cinema:String,val zones:Map<String,String>,val targets:List<FlowTarget>,val route:String,val expires:Long) {
    fun json() = JSONObject().put("session",id).put("cinema",cinema).put("expires",expires).put("route",route)
        .put("zones",JSONObject(zones)).put("targets",JSONArray().apply { targets.forEach { put(JSONObject().put("id",it.id).put("label",it.label).put("address",it.address)) } })
    companion object { fun parse(j:JSONObject):FlowConfig {
        val z=j.getJSONObject("zones");val zones=z.keys().asSequence().associateWith { z.getString(it).take(80) };require(zones.size in 2..100)
        val ts=j.getJSONArray("targets");require(ts.length() in 1..16)
        val targets=(0 until ts.length()).map { ts.getJSONObject(it).let { o -> FlowTarget(o.getString("id"),o.getString("label").take(80),com.cinemawatch.data.RadioAddress.normalize(o.getString("address"),"BLE")) } }
        return FlowConfig(j.getString("session"),j.getString("cinema").take(80),zones,targets,j.getString("route").take(200),j.getLong("expires"))
    } }
}
internal data class PhoneFlowState(val active:Boolean=false,val role:String="",val config:FlowConfig?=null,val snapshot:FlowSnapshot=FlowSnapshot(),val host:String="",val key:String="",val connected:Boolean=false,val rtt:Long=0,val error:String="",val latestReport:String="",val startedAt:Long=0)
internal data class FlowSetup(val role:String,val name:String,val zone:String,val offset:Int,val host:String,val key:String,val config:FlowConfig?)

/** Experiment lifetime is bounded to 15 minutes. Only explicit authorized test tags leave a node. */
internal class PhoneFlow(private val context:Context) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(PhoneFlowState());val state=mutable.asStateFlow()
    private var setup:FlowSetup?=null
    private var server:FlowServer?=null
    private var radio:BleRadio?=null
    private var job:Job?=null
    private val reportWrites=ArrayList<Job>()
    @Volatile private var tracker:FlowTracker?=null
    @Volatile private var config:FlowConfig?=null
    private val samples=ArrayList<FlowReading>()
    private var heardAt=0L
    private var bleFailed=false
    private var nodeId=""
    private var sequence=0L
    private var clockOffset=0L
    private var lastConnected=0L
    private var startedElapsed=0L
    fun prepareHost(cinema:String,zones:Map<String,String>,targets:List<Pair<String,String>>,route:String,name:String,zone:String,offset:Int) {
        require(!state.value.active && zone in zones && zones.size in 2..100 && name.isNotBlank() && name.length<=60 && offset in -20..20 && targets.size in 1..16)
        require(zones.values.distinct().size==zones.size) { "Area names must be unique" }
        val approved=targets.map { (label,address) -> FlowTarget(UUID.randomUUID().toString(),label.take(80),com.cinemawatch.data.RadioAddress.normalize(address,"BLE")) }
        require(approved.map { it.address }.distinct().size==approved.size)
        val cfg=FlowConfig(UUID.randomUUID().toString(),cinema,zones,approved,route.take(200),System.currentTimeMillis()+900000)
        val key=FlowCipher.newKey();setup=FlowSetup("HOST",name,zone,offset,"",key,cfg)
    }
    fun prepareNode(host:String,key:String,name:String,zoneName:String,offset:Int) {
        require(!state.value.active && FlowWire.privateHost(host) && Regex("[0-9a-fA-F]{64}").matches(key) && name.isNotBlank() && name.length<=60 && zoneName.isNotBlank() && offset in -20..20)
        setup=FlowSetup("NODE",name,zoneName,offset,host,key,null)
    }
    suspend fun begin() {
        val settings=requireNotNull(setup);require(!state.value.active);setup=null
        nodeId=UUID.randomUUID().toString();sequence=0;clockOffset=0;samples.clear();heardAt=0;bleFailed=false
        startedElapsed=SystemClock.elapsedRealtime();lastConnected=0
        mutable.value=PhoneFlowState(active=true,role=settings.role,key=if(settings.role=="HOST")settings.key else "",startedAt=System.currentTimeMillis())
        try {
            if(settings.role=="HOST") {
                config=requireNotNull(settings.config);tracker=FlowTracker(config!!.targets,config!!.zones.keys)
                val listener=withContext(Dispatchers.IO) { FlowServer(settings.key,::reply) };server=listener;scope.launch(Dispatchers.IO) { listener.serve() }
                mutable.value=state.value.copy(config=config,host=localIp(),connected=true)
            } else {
                val start=System.currentTimeMillis();val elapsed=SystemClock.elapsedRealtime()
                val response=withContext(Dispatchers.IO) { FlowWire.exchange(settings.host,JSONObject().put("action","join"),settings.key) }
                val rtt=SystemClock.elapsedRealtime()-elapsed;require(rtt<=1500);clockOffset=response.getLong("time")-(start+rtt/2)
                config=FlowConfig.parse(response.getJSONObject("config"));require(System.currentTimeMillis()+clockOffset<config!!.expires)
                require(settings.zone in config!!.zones.values) { "Select the exact coordinator area name" }
                lastConnected=SystemClock.elapsedRealtime();mutable.value=state.value.copy(config=config,host=settings.host,connected=true,rtt=rtt)
            }
            val cfg=requireNotNull(config)
            val zone=if(settings.role=="HOST")settings.zone else cfg.zones.entries.first { it.value==settings.zone }.key
            radio=BleRadio(context,{ observation -> scope.launch { accept(observation,cfg) } },{ scope.launch { if(config?.id==cfg.id)bleFailed=true } })
            runCatching { radio?.start(ScanIntensity.BALANCED) }.onFailure { bleFailed=true }
            job=scope.launch {
                while(isActive && state.value.active) {
                    if(SystemClock.elapsedRealtime()-startedElapsed>=900000 || System.currentTimeMillis()+clockOffset>=cfg.expires) { stop();break }
                    if(radio?.needsRestart()==true) { runCatching { radio?.stop() };delay(radio?.restartBackoffMs() ?: 2500);if(state.value.active) { bleFailed=false;runCatching { radio?.start(ScanIntensity.BALANCED) }.onFailure { bleFailed=true } } }
                    val now=System.currentTimeMillis()+clockOffset
                    samples.removeAll { now-it.at>6000 };sequence++
                    val healthy=!bleFailed && System.currentTimeMillis()-heardAt in 0..10000
                    val node=FlowNode(nodeId,settings.name,zone,settings.offset,now,healthy,sequence)
                    if(settings.role=="HOST") {
                        tracker?.ingest(node,samples.toList(),now)
                        mutable.value=state.value.copy(snapshot=tracker!!.evaluate(now),error=if(bleFailed)"SCAN" else "")
                    } else {
                        val request=JSONObject().put("action","push").put("session",cfg.id).put("time",now)
                            .put("node",nodeJson(node)).put("readings",JSONArray().apply { samples.forEach { put(JSONObject().put("target",it.target).put("rssi",it.rssi).put("at",it.at)) } })
                        val start=System.currentTimeMillis();val elapsed=SystemClock.elapsedRealtime()
                        runCatching { withContext(Dispatchers.IO) { FlowWire.exchange(settings.host,request,settings.key) } }.onSuccess { response ->
                            val rtt=SystemClock.elapsedRealtime()-elapsed
                            if(rtt<=1500) {
                                clockOffset=response.getLong("time")-(start+rtt/2);lastConnected=SystemClock.elapsedRealtime()
                                mutable.value=state.value.copy(connected=true,rtt=rtt,snapshot=snapshotParse(response.getJSONObject("snapshot")),error=if(bleFailed)"SCAN" else "")
                            } else offline(rtt)
                        }.onFailure { offline() }
                        if(SystemClock.elapsedRealtime()-lastConnected>30000) { stop();break }
                    }
                    delay(2000)
                }
            }
        } catch (_:Exception) { stop();mutable.value=state.value.copy(error="START") }
    }
    private fun offline(rtt:Long=state.value.rtt) {
        if(!state.value.active)return
        val prior=state.value
        mutable.value=prior.copy(connected=false,rtt=rtt,error="NETWORK",snapshot=prior.snapshot.copy(nodes=prior.snapshot.nodes.map { it.copy(healthy=false) },presence=prior.snapshot.presence.map { it.copy(zone=null) },signals=emptyList()))
    }
    private fun accept(o:Observation,cfg:FlowConfig) {
        if(!state.value.active || config?.id!=cfg.id)return
        heardAt=System.currentTimeMillis()
        if(!InspectionPolicy.validRssi(o.rssi))return
        val address=runCatching { com.cinemawatch.data.RadioAddress.normalize(o.mac,"BLE") }.getOrNull() ?: return
        val target=cfg.targets.firstOrNull { it.address==address } ?: return
        samples+=FlowReading(target.id,o.rssi,System.currentTimeMillis()+clockOffset)
        while(samples.size>512)samples.removeAt(0)
    }
    private fun reply(body:JSONObject):JSONObject {
        val cfg=requireNotNull(config);val now=System.currentTimeMillis();require(state.value.active && now<cfg.expires)
        when(body.getString("action")) {
            "join" -> return JSONObject().put("time",now).put("config",cfg.json())
            "push" -> {
                require(body.getString("session")==cfg.id && now-body.getLong("time") in -1500L..6000L)
                val n=body.getJSONObject("node");val node=FlowNode(n.getString("id"),n.getString("name"),n.getString("zone"),n.getInt("offset"),now,n.getBoolean("healthy"),n.getLong("sequence"))
                val rs=body.getJSONArray("readings");require(rs.length()<=512)
                val values=(0 until rs.length()).map { rs.getJSONObject(it).let { r -> FlowReading(r.getString("target"),r.getInt("rssi"),r.getLong("at")) } }
                tracker!!.ingest(node,values,now)
                return JSONObject().put("time",System.currentTimeMillis()).put("snapshot",wireSnapshotJson(tracker!!.snapshot()))
            }
            else -> error("Unknown operation")
        }
    }
    fun markTruth(target:String,zone:String) { tracker?.markTruth(target,zone,System.currentTimeMillis());tracker?.snapshot()?.let { mutable.value=state.value.copy(snapshot=it) } }
    fun stop() {
        val prior=state.value;if(!prior.active) { setup=null;return }
        job?.cancel();job=null;runCatching { server?.close() };server=null;runCatching { radio?.stop() };radio=null
        val snapshot=tracker?.snapshot() ?: prior.snapshot
        val cfg=config
        mutable.value=prior.copy(active=false,connected=false,key="",snapshot=snapshot,config=cfg?.copy(targets=cfg.targets.map { it.copy(address="") }))
        tracker=null;config=null;samples.clear();setup=null
        reportWrites.removeAll { it.isCompleted }
        if(prior.role=="HOST" && cfg!=null)reportWrites+=scope.launch {
            val report=runCatching { withContext(Dispatchers.IO) {
                val json=reportJson(cfg,snapshot)
                File(context.filesDir,"phone-flow/${cfg.id}.json").apply { parentFile?.mkdirs();writeText(json.toString(2)) }.name
            } }.getOrNull()
            mutable.value=state.value.copy(latestReport=report.orEmpty(),error=if(report==null)"SAVE" else state.value.error)
        }
    }
    suspend fun clearReports() {
        check(!state.value.active)
        reportWrites.toList().joinAll();reportWrites.clear()
        withContext(Dispatchers.IO) { val folder=File(context.filesDir,"phone-flow");if(folder.exists())check(folder.deleteRecursively()) }
        mutable.value=PhoneFlowState();setup=null
    }
    fun failStart() { mutable.value=state.value.copy(error="START") }
    fun reports()=File(context.filesDir,"phone-flow").listFiles()?.filter { it.extension=="json" }?.sortedByDescending { it.lastModified() }.orEmpty()
    companion object {
        fun localIp():String=runCatching {
            NetworkInterface.getNetworkInterfaces().toList().sortedBy { if(it.name.contains("wlan") || it.name.contains("eth"))0 else 1 }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress && FlowWire.privateHost(it.hostAddress.orEmpty()) }?.hostAddress.orEmpty()
        }.getOrDefault("")
    }
}
internal fun nodeJson(n:FlowNode)=JSONObject().put("id",n.id).put("name",n.name).put("zone",n.zone).put("offset",n.offset).put("at",n.at).put("healthy",n.healthy).put("sequence",n.sequence)
internal fun wireSnapshotJson(s:FlowSnapshot)=snapshotJson(s.copy(signals=s.signals.take(48),transitions=s.transitions.takeLast(20),truths=s.truths.takeLast(20)))
internal fun snapshotJson(s:FlowSnapshot)=JSONObject().put("nodes",JSONArray().apply { s.nodes.forEach { put(nodeJson(it)) } })
    .put("presence",JSONArray().apply { s.presence.forEach { put(JSONObject().put("target",it.target).put("label",it.label).put("zone",it.zone ?: JSONObject.NULL).put("margin",it.margin).put("receivers",it.receivers)) } })
    .put("signals",JSONArray().apply { s.signals.forEach { put(JSONObject().put("label",it.label).put("node",it.node).put("zone",it.zone).put("median",it.median).put("samples",it.samples)) } })
    .put("transitions",JSONArray().apply { s.transitions.forEach { put(JSONObject().put("label",it.label).put("from",it.from).put("to",it.to).put("at",it.at)) } })
    .put("dwell",JSONObject(s.dwellSeconds)).put("truths",JSONArray().apply { s.truths.forEach { put(JSONObject().put("label",it.label).put("expected",it.expected).put("observed",it.observed ?: JSONObject.NULL).put("at",it.at)) } })
internal fun snapshotParse(j:JSONObject):FlowSnapshot {
    fun array(name:String)=j.getJSONArray(name).let { a -> require(a.length()<=1000);(0 until a.length()).map { a.getJSONObject(it) } }
    val dwell=j.getJSONObject("dwell");val nodes=array("nodes").map { FlowNode(it.getString("id"),it.getString("name"),it.getString("zone"),it.getInt("offset"),it.getLong("at"),it.getBoolean("healthy"),it.getLong("sequence")) }
    return FlowSnapshot(nodes,array("presence").map { FlowPresence(it.getString("target"),it.getString("label"),if(it.isNull("zone"))null else it.getString("zone"),it.getInt("margin"),it.getInt("receivers")) },
        array("signals").map { FlowSignal(it.getString("label"),it.getString("node"),it.getString("zone"),it.getInt("median"),it.getInt("samples")) },
        array("transitions").map { FlowTransition(it.getString("label"),it.getString("from"),it.getString("to"),it.getLong("at")) },dwell.keys().asSequence().associateWith { dwell.getLong(it) },
        array("truths").map { FlowTruth(it.getString("label"),it.getString("expected"),if(it.isNull("observed"))null else it.getString("observed"),it.getLong("at")) })
}
internal fun reportJson(c:FlowConfig,s:FlowSnapshot):JSONObject {
    val data=snapshotJson(s)
    data.getJSONArray("nodes").let { a -> (0 until a.length()).forEach { a.getJSONObject(it).remove("id");a.getJSONObject(it).remove("sequence") } }
    data.getJSONArray("presence").let { a -> (0 until a.length()).forEach { a.getJSONObject(it).remove("target") } }
    return JSONObject().put("format","CinemaWatch phone-flow v1").put("scope","authorized BLE test tags; not customer counts or customer trajectories")
        .put("cinema",c.cinema).put("zones",JSONObject(c.zones)).put("plannedRoute",c.route).put("finishedAt",System.currentTimeMillis()).put("data",data)
}
