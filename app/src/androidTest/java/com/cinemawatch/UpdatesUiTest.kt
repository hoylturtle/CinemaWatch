package com.cinemawatch

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinemawatch.update.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class UpdatesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun explicitCheckLatestAvailableNetworkFailureAndUnsafeDownload() {
        val app = compose.activity.application as CinemaApp
        var update: UpdateRelease? = null
        var offline = false
        app.updateSource = object : UpdateSource {
            override suspend fun latest(): UpdateRelease? { if (offline) throw IOException(); return update }
            override suspend fun download(release: UpdateRelease, progress: (Int)->Unit): File { throw UpdateException(UpdateError.SIGNATURE) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("check-update").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("update-latest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("update-latest").assertExists()
        update = UpdateRelease(999, "Test future", app.packageName, 29,
            "https://github.com/hoylturtle/CinemaWatch/releases/download/v999/test.apk", 100, "a".repeat(64), "b".repeat(64), "Test only")
        compose.onNodeWithTag("check-update").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("download-update").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("download-update").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("update-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("install-update").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.update_signature_error)).assertExists()
        offline = true
        compose.onNodeWithTag("check-update").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(compose.activity.getString(R.string.update_network_error)).fetchSemanticsNodes().isNotEmpty() }
        offline = false; update = null
        compose.onNodeWithTag("check-update").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("update-latest").fetchSemanticsNodes().isNotEmpty() }
    }
}
