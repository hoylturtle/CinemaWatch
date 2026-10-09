package com.cinemawatch

import app.fieldwatch.domain.SignatureClass
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FirstRunTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as CinemaApp
    private fun text(id: Int) = compose.activity.getString(id)
    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // Connected-test cleanup uninstalls the app, so copy synthetic evidence before cleanup.
        listOf("mkdir -p /sdcard/cinemawatch-ui", "cp ${dir.absolutePath}/$name.png /sdcard/cinemawatch-ui/$name.png").forEach { command ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
    }
    @After fun captureFinalScreen() { shot("final-screen") }
    private fun waitForToast(id: Int) {
        compose.waitUntil(15000) { compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15000) { compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isEmpty() }
    }
    @Before fun cleanDatabase() {
        runBlocking { app.repository.dao.clear() }
        compose.waitUntil(15000) { compose.onAllNodesWithText(text(R.string.setup)).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun bulkSetupManualAssetBindingDemoPreviewAndLanguagePersistence() {
        compose.onNodeWithText(text(R.string.setup)).performClick()
        compose.onNodeWithTag("cinema-name").performTextInput("Cinema Test")
        compose.onNodeWithTag("hall-count").performTextReplacement("2")
        compose.onNodeWithTag("hall-prefix").performTextReplacement("Hall")
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(15000) { runBlocking { app.repository.dao.zones().first().size == 6 } }
        runBlocking {
            val names = app.repository.dao.zones().first().map { it.name }.toSet()
            listOf(R.string.zone_lobby, R.string.zone_corridor, R.string.zone_office, R.string.zone_projection).forEach { assertTrue(names.contains(text(it))) }
        }
        compose.onNodeWithText(text(R.string.assets)).performClick()
        compose.onNodeWithTag("create-asset").performClick()
        compose.onNodeWithTag("asset-name").performTextInput("Projector 1")
        compose.onNodeWithTag("asset-location").performScrollTo().performTextInput("Rear wall / rack A")
        compose.onNodeWithTag("asset-notes").performScrollTo().performTextInput("Maintenance due Friday")
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onNodeWithTag("authorization").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(15000) { runBlocking { app.repository.dao.assets().first().size == 1 } }
        compose.waitUntil(15000) { compose.onAllNodesWithText(text(R.string.awaiting_binding)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.awaiting_binding)).assertIsDisplayed()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("asset-name").fetchSemanticsNodes().isEmpty() }
        waitForToast(R.string.asset_created)
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("Rear wall / rack A"))
        compose.onNodeWithText("Rear wall / rack A").assertIsDisplayed()
        shot("asset-before-binding")
        compose.onNodeWithText(text(R.string.bind_signal)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.bind_signal)).performScrollTo().performClick()
        compose.onNodeWithTag("radio-address", useUnmergedTree = true).performScrollTo().performTextInput("AA:BB:CC:DD:EE:00")
        compose.onNodeWithTag("authorization").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(15000) { runBlocking { app.repository.dao.allBindings().size == 1 } }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("radio-address").fetchSemanticsNodes().isEmpty() }
        waitForToast(R.string.binding_saved)
        shot("assets-with-binding")
        compose.onNodeWithTag("create-asset").performScrollTo().performClick()
        compose.onNodeWithTag("asset-name").performTextInput("Duplicate")
        closeSoftKeyboard()
        shot("manual-form")
        compose.onNodeWithTag("radio-address", useUnmergedTree = true).performScrollTo().performTextInput("AA:BB:CC:DD:EE:00")
        compose.onNodeWithTag("authorization").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText(text(R.string.duplicate_asset)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        Assert.assertEquals(1, runBlocking { app.repository.dao.assets().first().size })
        compose.onNodeWithText(text(R.string.dashboard)).performClick()
        compose.onNodeWithText(text(R.string.demo)).performScrollTo().performClick()
        compose.waitUntil(10000) { app.scanner.state.value.running }
        compose.waitUntil(10000) { app.scanner.state.value.live.isNotEmpty() }
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("group-ACCESS_POINT"))
        compose.onNodeWithTag("group-ACCESS_POINT").assertIsDisplayed()
        shot("fieldwatch-groups")
        compose.onNodeWithTag("group-ACCESS_POINT").performClick()
        val ap = "WIFI:02:00:00:00:00:01"
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("radio-ACCESS_POINT-$ap"))
        compose.onNodeWithTag("radio-ACCESS_POINT-$ap").assertIsDisplayed()
        compose.onNodeWithTag("rssi-ACCESS_POINT-$ap", useUnmergedTree = true).assertExists()
        shot("fieldwatch-devices")
        compose.onNodeWithTag("radio-ACCESS_POINT-$ap").performClick()
        compose.onNodeWithText(text(R.string.signal_average_note)).assertExists()
        compose.onNodeWithText(text(R.string.register)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasTestTag("group-ACCESS_POINT"))
        compose.onNodeWithTag("group-ACCESS_POINT").performClick()
        compose.onNodeWithTag("radio-ACCESS_POINT-$ap").assertDoesNotExist()
        compose.waitUntil(40000) { !app.scanner.state.value.running && !app.scanner.state.value.saving }
        val session = runBlocking { app.repository.dao.sessions().first().single() }
        compose.onNodeWithText(text(R.string.reports)).performClick()
        compose.onNodeWithTag("preview-${session.id}").performScrollTo().performClick()
        compose.onNodeWithTag("report-preview").assertExists()
        compose.onNodeWithText(text(R.string.report_title)).assertIsDisplayed()
        shot("report-preview")
        compose.onNodeWithTag("report-list").performScrollToNode(hasText(text(R.string.report_groups)))
        compose.onNodeWithText(text(R.string.group_access_point), substring = true).assertExists()
        shot("report-groups")
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.settings)).performClick()
        listOf("简体中文" to "设置", "繁體中文" to "設定", "English" to "Settings").forEach { (choice, expected) ->
            compose.onNodeWithText(choice).performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(expected).assertIsDisplayed()
        }
        compose.onNodeWithText("简体中文").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals("zh-Hans", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        shot("settings-simplified")
    }
    @Test fun domesticEcosystemsShowDevicesInsideIndependentGroups() {
        val now = System.currentTimeMillis()
        val cameras = com.cinemawatch.domain.SignalGroup.CAMERA
        val apple = com.cinemawatch.domain.SignalGroup.APPLE
        val huawei = com.cinemawatch.domain.SignalGroup.HUAWEI
        fun radio(address: String, label: String, ecosystem: com.cinemawatch.domain.SignalGroup) = com.cinemawatch.radio.LiveRadio(
            "BLE", address, label, -58, label, SignatureClass.CAMERA, now, null, listOf(-62, -58),
            signatureHits = listOf(com.cinemawatch.radio.SignatureHit("Camera signature", SignatureClass.CAMERA)),
            ecosystem = ecosystem, firstAt = now - 12000)
        val radios = listOf(radio("02:00:00:00:01:01", "Apple camera", apple), radio("02:00:00:00:01:02", "Huawei camera", huawei))
        val expanded = androidx.compose.runtime.mutableStateOf(listOf(cameras.name, apple.name, huawei.name))
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                androidx.compose.material3.MaterialTheme {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.testTag("outline-test"), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        signalOutline(radios, emptyList(), true, expanded.value.toSet(), now,
                            onToggle = { group -> expanded.value = if (group.name in expanded.value) expanded.value - group.name else expanded.value + group.name }, onOpen = {})
                    }
                }
            }
        }
        compose.onNodeWithTag("radio-CAMERA-BLE:02:00:00:00:01:02").assertExists()
        compose.onNodeWithTag("outline-test").performScrollToNode(hasTestTag("radio-HUAWEI-BLE:02:00:00:00:01:02"))
        compose.onNodeWithTag("radio-HUAWEI-BLE:02:00:00:00:01:02").assertIsDisplayed()
        shot("fieldwatch-domestic-ecosystem")
        compose.onNodeWithTag("outline-test").performScrollToNode(hasTestTag("group-APPLE"))
        compose.onNodeWithTag("group-APPLE").performClick()
        compose.onNodeWithTag("radio-APPLE-BLE:02:00:00:00:01:01").assertDoesNotExist()
        compose.onNodeWithTag("outline-test").performScrollToNode(hasTestTag("radio-HUAWEI-BLE:02:00:00:00:01:02"))
        compose.onNodeWithTag("radio-HUAWEI-BLE:02:00:00:00:01:02").assertIsDisplayed()
        compose.onNodeWithTag("outline-test").performScrollToNode(hasTestTag("radio-CAMERA-BLE:02:00:00:00:01:02"))
        compose.onNodeWithTag("radio-CAMERA-BLE:02:00:00:00:01:02").assertIsDisplayed()
    }
}
