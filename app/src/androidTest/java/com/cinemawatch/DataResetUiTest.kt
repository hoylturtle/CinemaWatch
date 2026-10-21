package com.cinemawatch

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataResetUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun resetFromSettingsDeletesAttachmentsAndKeepsLanguage() {
        val app = compose.activity.application as CinemaApp
        runBlocking { app.repository.clearCinemaData(); app.repository.createCinema("Synthetic reset cinema", "Lobby") }
        val language = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val paths = listOf(
            File(app.filesDir,"plans/synthetic/image.png"), File(app.filesDir,"phone-flow/report.json"),
            File(app.filesDir,"maintenance-photos/synthetic.jpg"), File(app.cacheDir,"maintenance-capture/raw.jpg"),
            File(app.cacheDir,"maintenance-photo.tmp"), File(app.cacheDir,"reports/report.csv"),
            File(app.cacheDir,"updates/update.apk")
        )
        paths.forEach { it.parentFile!!.mkdirs(); it.writeText("Synthetic test content") }
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasText(compose.activity.getString(R.string.reset)))
        compose.onNodeWithText(compose.activity.getString(R.string.reset)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.delete)).performClick()
        compose.waitUntil(15000) { runBlocking { app.repository.dao.cinemas().first().isEmpty() } && paths.none { it.exists() } }
        assertEquals(language, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertTrue(runBlocking { app.repository.dao.assets().first().isEmpty() })
    }
}
