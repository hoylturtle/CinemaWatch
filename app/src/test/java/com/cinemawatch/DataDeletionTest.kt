package com.cinemawatch

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cinemawatch.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29], application=android.app.Application::class)
class DataDeletionTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var db: CinemaDatabase
    private lateinit var repo: CinemaRepository
    private lateinit var first: CinemaAsset
    private lateinit var second: CinemaAsset
    @Before fun setup() = runBlocking {
        root=Files.createTempDirectory("cinema-deletion").toFile()
        context=object: ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
        }
        db=Room.inMemoryDatabaseBuilder(context,CinemaDatabase::class.java).build()
        repo=CinemaRepository(db,CinemaDataFiles(context))
        repo.createCinema("Synthetic cinema","Lobby")
        val zone=repo.dao.zones().first().single().id
        first=repo.createAsset(zone,"First",true);second=repo.createAsset(zone,"Second",true)
    }
    private fun put(path:String)=File(root,path).apply { parentFile.mkdirs();writeText("Synthetic data") }
    private suspend fun attach(asset:CinemaAsset,photo:String) {
        val issue=MaintenanceIssue("issue-${asset.id}",asset.id,"source","WEAK","OPEN",1,1)
        repo.dao.insertIssue(issue)
        repo.dao.insertEvent(IssueEvent("event-${asset.id}",issue.id,1,"NOTE","Synthetic note",photo))
    }
    @After fun close() { db.close();root.deleteRecursively() }

    @Test fun resetRemovesDatabasePhotosPlansReportsAndCachesButPreservesUnrelatedData() = runBlocking {
        val privateData=listOf("files/plans/cinema/image.png","files/phone-flow/session.json","files/maintenance-photos/photo.jpg","cache/maintenance-capture/photo.jpg","cache/maintenance-photo.tmp","cache/reports/inspection.csv","cache/updates/update.apk")
        privateData.forEach(::put);put("files/keep.txt");put("cache/keep.txt")
        attach(first,"photo.jpg")
        repo.clearCinemaData()
        assertTrue(repo.dao.cinemas().first().isEmpty());assertTrue(repo.dao.assets().first().isEmpty())
        assertTrue(repo.dao.issueEvents().first().isEmpty())
        privateData.forEach { assertFalse(it,File(root,it).exists()) }
        assertTrue(File(root,"files/keep.txt").exists());assertTrue(File(root,"cache/keep.txt").exists())
        // Retrying a completed reset is safe; subsequent new cinema data can still be stored.
        repo.clearCinemaData();repo.createCinema("New synthetic cinema","Lobby")
        assertEquals(1,repo.dao.cinemas().first().size)
    }
    @Test fun deleteAssetReclaimsOnlyUnsharedPhotosAndItsOwnPlanMarker() = runBlocking {
        attach(first,"first.jpg");attach(second,"second.jpg")
        put("files/maintenance-photos/first.jpg");put("files/maintenance-photos/second.jpg")
        val pins=put("files/plans/cinema/pins.json")
        pins.writeText("""[{"target":"asset:${first.id}"},{"target":"asset:${second.id}"},{"target":"zone:lobby"}]""")
        put("cache/reports/inspection.csv");put("cache/maintenance-capture/photo.jpg")
        repo.deleteAsset(first.id)
        assertNull(repo.dao.asset(first.id));assertNotNull(repo.dao.asset(second.id))
        assertFalse(File(root,"files/maintenance-photos/first.jpg").exists())
        assertTrue(File(root,"files/maintenance-photos/second.jpg").exists())
        val retained=JSONArray(pins.readText())
        assertEquals(2,retained.length());assertFalse(pins.readText().contains(first.id))
        assertFalse(File(root,"cache/reports").exists());assertFalse(File(root,"cache/maintenance-capture").exists())
    }
    @Test fun sharedAttachmentSurvivesUntilLastReferencingAssetIsDeleted() = runBlocking {
        attach(first,"shared.jpg");attach(second,"shared.jpg");val photo=put("files/maintenance-photos/shared.jpg")
        repo.deleteAsset(first.id);assertTrue(photo.exists())
        repo.deleteAsset(second.id);assertFalse(photo.exists())
    }
    @Test fun failedMarkerCleanupKeepsAssetForRepairAndRetry() = runBlocking {
        val pins=put("files/plans/cinema/pins.json").apply { writeText("invalid json") }
        assertNotNull(runCatching { repo.deleteAsset(first.id) }.exceptionOrNull())
        assertNotNull(repo.dao.asset(first.id))
        pins.writeText("""[{"target":"asset:${first.id}"}]""")
        repo.deleteAsset(first.id)
        assertNull(repo.dao.asset(first.id));assertEquals(0,JSONArray(pins.readText()).length())
    }
    @Test fun corruptAttachmentPathCannotDeleteOutsidePhotoDirectoryOrDropAsset() = runBlocking {
        attach(first,"../keep.txt");val keep=put("files/keep.txt")
        val error=runCatching { repo.deleteAsset(first.id) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException);assertNotNull(repo.dao.asset(first.id));assertTrue(keep.exists())
    }
}
