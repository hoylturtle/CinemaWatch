package com.cinemawatch

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cinemawatch.data.Zone
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class FloorPlanTest {
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
        PdfDocument().use { doc ->
            doc.startPage(PdfDocument.PageInfo.Builder(300, 200, 1).create()).also { page -> page.canvas.drawColor(android.graphics.Color.WHITE); doc.finishPage(page) }
            pdf.outputStream().use { doc.writeTo(it) }
        }
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
