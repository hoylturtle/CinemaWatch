package com.cinemawatch

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cinemawatch.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Synthetic authorized-asset fixtures; does not claim RF hardware validation. */
@RunWith(AndroidJUnit4::class)
class MaintenanceFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun confirmsBaselineRecordsPhotoAndClosesOnlyAfterRecheck() {
        val app = compose.activity.application as CinemaApp
        val repo = app.repository
        val asset = runBlocking {
            repo.dao.clear(); repo.createCinema("Workflow test", "Lobby")
            repo.createAsset(repo.dao.zones().first().single().id,"Projector AP",true,"WIFI","AA:BB:CC:DD:EE:00", "Rack A")
        }
        fun sample(id: String, start: Long, status: String, rssi: Int): Inspection {
            val s=Inspection(id,asset.zoneId,"INSPECTION",start,start+120000,120,1,1,1,3,30,0,0,true,true,false,requestedSeconds=120)
            runBlocking { repo.save(s,listOf(AssetResult(s.id,asset.id,s.endMs,status,rssi,3,0,null))) }; return s
        }
        sample("initial",System.currentTimeMillis()-125000,"LEARNING",-50)
        compose.waitUntil(10000) { compose.onAllNodesWithText("Workflow test · Lobby").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.assets)).performClick()
        compose.onNodeWithTag("workflow-${asset.id}").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-baseline").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.confirm_device_normal)).performClick()
        compose.waitUntil(10000) { runBlocking { repo.dao.baseline(asset.id) != null } }
        assertEquals(-50,runBlocking { repo.dao.baseline(asset.id)!!.rssi })
        sample("weak",System.currentTimeMillis()+1000,"WEAK",-68)
        compose.waitUntil(10000) { runBlocking { repo.dao.issues().first().isNotEmpty() } }
        val issue=runBlocking { repo.dao.issues().first().single() }
        val jpg=File(compose.activity.cacheDir,"workflow-photo.jpg")
        val bitmap=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888)
        jpg.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        val photo=MaintenancePhotos.import(compose.activity, Uri.fromFile(jpg))
        runBlocking { repo.updateIssue(issue.id,"NOTE","Photo of loose connector",photo) }
        assertTrue(MaintenancePhotos.file(compose.activity,photo).length()>0)
        compose.onNodeWithTag("issue-note").performScrollTo().performTextInput("Reconnected cable")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("issue-ready-recheck").performScrollTo().performClick()
        compose.waitUntil(10000) { runBlocking { repo.dao.issue(issue.id)!!.state=="WAITING_RECHECK" } }
        val update=runBlocking { repo.dao.issue(issue.id)!!.updatedAt }
        sample("recheck",maxOf(update+1000,issue.createdAt+1000),"NORMAL",-51)
        compose.waitUntil(10000) { runBlocking { repo.dao.issue(issue.id)!!.state=="CLOSED" } }
        assertTrue(runBlocking { repo.dao.issueEvents().first().any { it.action=="RECHECK_PASSED" } })
        compose.onNodeWithTag("maintenance-list").performScrollToNode(hasText(compose.activity.getString(R.string.issue_closed)))
        compose.onNodeWithText(compose.activity.getString(R.string.issue_closed)).assertExists()
        val capture=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dest=File(compose.activity.getExternalFilesDir(null),"workflow.png")
        dest.outputStream().use { capture.compress(Bitmap.CompressFormat.PNG,100,it) };capture.recycle()
        listOf("mkdir -p /sdcard/cinemawatch-ui","cp ${dest.path} /sdcard/cinemawatch-ui/workflow.png").forEach { cmd ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        }
        jpg.delete()
    }
}
