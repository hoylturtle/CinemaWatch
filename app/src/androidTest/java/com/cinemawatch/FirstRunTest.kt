package com.cinemawatch

import android.graphics.Bitmap
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
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onNodeWithTag("authorization").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(15000) { runBlocking { app.repository.dao.assets().first().size == 1 } }
        compose.waitUntil(15000) { compose.onAllNodesWithText(text(R.string.awaiting_binding)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.awaiting_binding)).assertIsDisplayed()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("asset-name").fetchSemanticsNodes().isEmpty() }
        waitForToast(R.string.asset_created)
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
}
