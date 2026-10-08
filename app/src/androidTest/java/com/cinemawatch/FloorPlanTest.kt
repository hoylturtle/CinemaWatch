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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class FloorPlanTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun marksAnAssetOnThePlanAndReopensStoredCoordinates() {
        val cinema = Cinema(UUID.randomUUID().toString(), "Plan test")
        val zone = Zone("zone-test", cinema.id, "Hall 1")
        val asset = CinemaAsset("asset-test", zone.id, "Plan projector", location = "Rack A")
        val store = FloorPlanStore(compose.activity, cinema.id)
        store.sketch(listOf(zone))
        compose.activity.setContent { MaterialTheme { FloorPlanDialog(cinema, listOf(zone), listOf(asset), {}, { name -> Zone(UUID.randomUUID().toString(), cinema.id, name) }) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("floor-plan-image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Plan projector").performScrollTo().performClick()
        compose.onNodeWithTag("floor-plan-image").performScrollTo().performTouchInput { click(center) }
        compose.waitUntil(10000) { store.pins().any { it.target == "asset:asset-test" } }
        val marker = FloorPlanStore(compose.activity, cinema.id).pins().single { it.target == "asset:asset-test" }
        assertEquals(.5f, marker.x, .02f); assertEquals(.5f, marker.y, .02f)
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
