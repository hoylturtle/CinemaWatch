package com.cinemawatch

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun chineseSetupDemoAndPersistedHistory() {
        compose.onNodeWithText(compose.activity.getString(R.string.setup)).performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithTag("cinema-name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cinema-name").performTextInput("星光影城")
        compose.onNodeWithTag("zone-name").performTextInput("一號廳")
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("星光影城 · 一號廳").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.demo)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(compose.activity.getString(R.string.demo_note)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(40000) { !(compose.activity.application as CinemaApp).scanner.state.value.running && !(compose.activity.application as CinemaApp).scanner.state.value.saving }
        compose.onNodeWithText(compose.activity.getString(R.string.reports)).performClick()
        compose.onNodeWithText("一號廳").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.calibration_pending)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.demo_note)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        listOf("简体中文" to "设置", "繁體中文" to "設定", "English" to "Settings").forEach { (choice, expected) ->
            compose.onNodeWithText(choice).performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(expected).assertIsDisplayed()
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
