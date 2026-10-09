package com.cinemawatch

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cinemawatch.flow.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Synthetic encrypted LAN packets. Real RF coverage must be validated with fixed physical phones. */
@RunWith(AndroidJUnit4::class)
class PhoneFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun joinsEncryptedLanInfersRegionRecordsTruthAndExportsAnonymousReport() {
        val app=compose.activity.application as CinemaApp
        val repo=app.repository
        val zones=runBlocking {
            repo.dao.clear();repo.createCinema("Phone pilot","Lobby")
            val cinema=repo.dao.cinemas().first().single()
            repo.createZone(cinema.id,"Corridor");repo.dao.zones().first()
        }
        val lobby=zones.first { it.name=="Lobby" }.id;val corridor=zones.first { it.name=="Corridor" }.id
        runBlocking { withContext(Dispatchers.Main) {
            app.phoneFlow.prepareHost("Phone pilot",zones.associate { it.id to it.name },listOf("Authorized beacon" to "AA:BB:CC:DD:EE:01"),"Lobby → Corridor","Coordinator",lobby,0)
            app.phoneFlow.begin()
        } }
        val sender=Executors.newSingleThreadScheduledExecutor()
        try {
            val key=app.phoneFlow.state.value.key
            val joined=FlowWire.exchange("127.0.0.1",JSONObject().put("action","join"),key)
            val cfg=FlowConfig.parse(joined.getJSONObject("config"))
            assertEquals("Phone pilot",cfg.cinema)
            fun push(id:String,zone:String,rssi:Int,seq:Long) {
                val now=System.currentTimeMillis()
                val readings=JSONArray().apply { repeat(3) { put(JSONObject().put("target",cfg.targets.single().id).put("rssi",rssi).put("at",now-it*20)) } }
                FlowWire.exchange("127.0.0.1",JSONObject().put("action","push").put("session",cfg.id).put("time",now).put("node",nodeJson(FlowNode(id,id,zone,0,now,true,seq))).put("readings",readings),key)
            }
            var sequence=0L
            sender.scheduleAtFixedRate({ runCatching { sequence++;push("Lobby node",lobby,-75,sequence);push("Corridor node",corridor,-45,sequence) } },0,1,TimeUnit.SECONDS)
            compose.waitUntil(10000) { app.phoneFlow.state.value.snapshot.presence.firstOrNull()?.zone==corridor }
            compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
            compose.onNodeWithTag("phone-flow-open").performScrollTo().performClick()
            compose.onNodeWithTag("truth-zone-$corridor").performScrollTo().performClick()
            compose.onNodeWithTag("flow-mark-truth").performScrollTo().performClick()
            assertEquals(corridor,app.phoneFlow.state.value.snapshot.truths.single().expected)
            assertEquals(corridor,app.phoneFlow.state.value.snapshot.truths.single().observed)
            compose.onNodeWithTag("flow-stop").performScrollTo().performClick()
            compose.waitUntil(10000) { app.phoneFlow.state.value.latestReport.isNotBlank() }
            val report=app.phoneFlow.reports().first { it.name==app.phoneFlow.state.value.latestReport }.readText()
            assertFalse(report.contains("AA:BB:CC:DD:EE:01"));assertFalse(report.contains(key));assertFalse(report.contains(cfg.targets.single().id))
            compose.onNodeWithText(compose.activity.getString(R.string.flow_preview)).performScrollTo().performClick()
            compose.onNodeWithText("Phone pilot").assertExists()
            val capture=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val dest=File(compose.activity.getExternalFilesDir(null),"phone-flow.png")
            dest.outputStream().use { capture.compress(Bitmap.CompressFormat.PNG,100,it) };capture.recycle()
            listOf("mkdir -p /sdcard/cinemawatch-ui","cp ${dest.path} /sdcard/cinemawatch-ui/phone-flow.png").forEach { cmd ->
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
            }
        } finally { sender.shutdownNow();runBlocking { withContext(Dispatchers.Main) { app.phoneFlow.stop() } } }
    }
}
