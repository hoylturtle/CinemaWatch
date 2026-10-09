package com.cinemawatch

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinemawatch.data.*
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.After
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class FloorPlanTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun capturePlanScreen() { saveScreen("plan-screen.png") }
    private fun saveScreen(filename: String) { runCatching {
        val dir = File(compose.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(dir, filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        listOf("mkdir -p /sdcard/cinemawatch-ui", "cp ${dir.path}/$filename /sdcard/cinemawatch-ui/$filename").forEach { command ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
        }
    } }
    @Test fun marksAnAssetOnThePlanAndReopensStoredCoordinates() {
        val app = compose.activity.application as CinemaApp
        val zoneId = runBlocking { app.repository.dao.clear(); app.repository.createCinemaWithHalls("Plan test", 1, "Hall") }
        val cinema = runBlocking { app.repository.dao.cinemas().first().single() }
        val zones = runBlocking { app.repository.dao.zones().first() }
        val asset = runBlocking { app.repository.createAsset(zoneId, "Plan projector", true, location = "Rack A") }
        val originalOrientation = compose.activity.requestedOrientation
        val store = FloorPlanStore(compose.activity, cinema.id)
        store.sketch(zones)
        compose.waitUntil(10000) { compose.onAllNodesWithText("Plan test · Hall 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasText(compose.activity.getString(R.string.floor_plan)))
        compose.onNodeWithText(compose.activity.getString(R.string.floor_plan)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("floor-plan-image").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("plan-tool-ROOM").performScrollTo().performClick()
        val beforeGesture = store.pins()
        compose.onNodeWithTag("floor-plan-image").performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(width * .4f, height * .5f))
            down(1, androidx.compose.ui.geometry.Offset(width * .6f, height * .5f))
            for (step in 1..10) {
                moveTo(0, androidx.compose.ui.geometry.Offset(width * (.4f - step * .01f), height * .5f))
                moveTo(1, androidx.compose.ui.geometry.Offset(width * (.6f + step * .01f), height * .5f))
                advanceEventTime(16)
            }
            up(0); up(1)
        }
        compose.onNodeWithTag("plan-zoom").assert(hasText("200%") or hasText("199%"))
        assertEquals(beforeGesture, store.pins())
        compose.onNodeWithTag("plan-finish").assertDoesNotExist()
        compose.onNodeWithTag("plan-fit").performScrollTo().performClick()
        compose.onNodeWithTag("plan-zoom").assertTextEquals("100%")
        compose.onNodeWithTag("plan-tool-ASSET").performScrollTo().performClick()
        compose.onNodeWithText("Plan projector").performScrollTo().performClick()
        compose.onNodeWithTag("floor-plan-image").performTouchInput { click(center) }
        compose.waitUntil(10000) { store.pins().any { it.target == "asset:${asset.id}" } }
        compose.onNodeWithTag("plan-tool-ROOM").performScrollTo().performClick()
        compose.onNodeWithTag("floor-plan-image").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * .3f, height * .35f))
            click(androidx.compose.ui.geometry.Offset(width * .65f, height * .35f))
            click(androidx.compose.ui.geometry.Offset(width * .65f, height * .65f))
            click(androidx.compose.ui.geometry.Offset(width * .3f, height * .65f))
        }
        compose.onNodeWithTag("plan-finish").performClick()
        compose.onNodeWithText("Hall 1").performClick()
        compose.waitUntil(10000) { store.pins().any { it.target == "zone:$zoneId" && it.vertices.size == 4 } }
        compose.onNodeWithTag("plan-undo").performClick()
        compose.waitUntil(10000) { store.pins().single { it.target == "zone:$zoneId" }.vertices.isEmpty() }
        compose.onNodeWithTag("plan-redo").performClick()
        compose.waitUntil(10000) { store.pins().single { it.target == "zone:$zoneId" }.vertices.size == 4 }
        val marker = FloorPlanStore(compose.activity, cinema.id).pins().single { it.target == "asset:${asset.id}" }
        assertEquals(.5f, marker.x, .02f); assertEquals(.5f, marker.y, .02f)
        saveScreen("floor-plan-landscape.png")
        compose.onNodeWithText(compose.activity.getString(R.string.close)).performClick()
        compose.waitUntil(10000) { compose.activity.requestedOrientation == originalOrientation }
        store.image.parentFile?.deleteRecursively()
    }

    @Test fun importsPdfJpgAndCdrPreviewAndPersistsManualGeometry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cinema = UUID.randomUUID().toString()
        val store = FloorPlanStore(context, cinema)
        val jpg = File(context.cacheDir, "plan-test.jpg")
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        jpg.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        store.import(Uri.fromFile(jpg))
        assertEquals(200, android.graphics.BitmapFactory.decodeFile(store.image.path).width)
        val pins = listOf(PlanPin("asset:a", .2f, .4f), PlanPin("zone:z", .5f, .5f, .3f, .4f))
        store.save(pins); assertEquals(pins, FloorPlanStore(context, cinema).pins())
        try { store.save(listOf(PlanPin("asset:a", Float.NaN, .5f))); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(pins, store.pins())
        val pdf = File(context.cacheDir, "plan-test.pdf")
        val doc = PdfDocument()
        try {
            doc.startPage(PdfDocument.PageInfo.Builder(300, 200, 1).create()).also { page -> page.canvas.drawColor(android.graphics.Color.WHITE); doc.finishPage(page) }
            pdf.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
        store.import(Uri.fromFile(pdf), 1); assertTrue(store.image.length() > 0); assertTrue(store.pins().isEmpty())
        val before = store.image.readBytes()
        try { store.import(Uri.fromFile(pdf), 2); fail() } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, store.image.readBytes())
        val cdr = File(context.cacheDir, "plan-test.cdr")
        ZipOutputStream(cdr.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("metadata/thumbnails/thumbnail.png")); bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip); zip.closeEntry() }
        store.import(Uri.fromFile(cdr)); assertEquals(200, android.graphics.BitmapFactory.decodeFile(store.image.path).width)
        store.sketch(listOf(Zone("z", cinema, "Lobby"), Zone("z2", cinema, "Hall 1")))
        assertEquals(2, store.pins().size); assertTrue(store.pins().all { it.width > 0 && it.height > 0 })
        bitmap.recycle(); jpg.delete(); pdf.delete(); cdr.delete()
        store.image.parentFile?.deleteRecursively()
    }
}
