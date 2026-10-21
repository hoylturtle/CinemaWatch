package com.cinemawatch

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinemawatch.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpgradeSeedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun seedPriorInstall() = runBlocking {
        val app = compose.activity.application as CinemaApp
        app.repository.dao.clear()
        val zone = app.repository.createCinemaWithHalls("Upgrade cinema", 2, "Hall")
        val asset = app.repository.createAsset(zone, "Upgrade projector", true, "WIFI", "AA:BB:CC:DD:EE:10")
        val session = Inspection(UUID.randomUUID().toString(), zone, "INSPECTION", 1000, 121000, 120, 1, 0, 1,
            3, 0, 0, 0, true, false, false, requestedSeconds = 120)
        app.repository.save(session, listOf(AssetResult(session.id, asset.id, 121000, "LEARNING", -50, 4, 0, null)))
        app.repository.dao.insertIssue(MaintenanceIssue("upgrade-issue",asset.id,session.id,"WEAK","OPEN",1,1))
        app.repository.dao.insertEvent(IssueEvent("upgrade-event","upgrade-issue",1,"NOTE","Synthetic upgrade note","upgrade.jpg"))
        File(app.filesDir,"maintenance-photos/upgrade.jpg").apply { parentFile!!.mkdirs(); writeText("Synthetic attachment bytes") }
        val cinema=app.repository.dao.cinemas().first().single()
        File(app.filesDir,"plans/${cinema.id}/pins.json").apply {
            parentFile!!.mkdirs(); writeText("""[{"target":"asset:${asset.id}","x":0.5,"y":0.5}]""")
        }

        compose.waitUntil(15000) { compose.onAllNodesWithText("Upgrade cinema · Hall 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Upgrade cinema · Hall 1").performClick()
        compose.onAllNodesWithText("Upgrade cinema · Hall 1").onLast().performClick()
        compose.waitUntil(15000) { app.getSharedPreferences("ui", 0).getString("zone", null) == zone }
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hant")) }
        compose.waitUntil(15000) { AppCompatDelegate.getApplicationLocales().toLanguageTags() == "zh-Hant" }
        assertEquals(2, app.repository.dao.zones().first().size)
    }
}
@RunWith(AndroidJUnit4::class)
class UpgradeVerifyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun replacementInstallKeepsDatabaseAndSettings() = runBlocking {
        val app = compose.activity.application as CinemaApp
        assertEquals("com.cinemawatch", app.packageName)
        assertTrue(app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode > 11)
        assertEquals("Upgrade cinema", app.repository.dao.cinemas().first().single().name)
        assertEquals(2, app.repository.dao.zones().first().size)
        assertEquals("Upgrade projector", app.repository.dao.assets().first().single().name)
        assertEquals("AA:BB:CC:DD:EE:10", app.repository.dao.allBindings().single().address)
        assertEquals(1, app.repository.dao.sessions().first().size)
        assertEquals(-50, app.repository.dao.results().first().single().medianRssi)
        assertEquals("Synthetic upgrade note", app.repository.dao.issueEvents().first().single().note)
        assertEquals("Synthetic attachment bytes", File(app.filesDir,"maintenance-photos/upgrade.jpg").readText())
        val cinema=app.repository.dao.cinemas().first().single()
        val pins=org.json.JSONArray(File(app.filesDir,"plans/${cinema.id}/pins.json").readText())
        assertEquals("asset:${app.repository.dao.assets().first().single().id}",pins.getJSONObject(0).getString("target"))

        val zone = app.getSharedPreferences("ui", 0).getString("zone", null)
        assertTrue(app.repository.dao.zones().first().any { it.id == zone })
        compose.waitUntil(15000) { AppCompatDelegate.getApplicationLocales().toLanguageTags() == "zh-Hant" }
    }
}
