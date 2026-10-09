package com.cinemawatch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cinemawatch.data.*
import com.cinemawatch.domain.InspectionPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class MaintenanceTest {
    private lateinit var db: CinemaDatabase
    private lateinit var repo: CinemaRepository
    private lateinit var asset: CinemaAsset
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CinemaDatabase::class.java).build(); repo = CinemaRepository(db)
        repo.createCinema("Cinema", "Lobby"); val zone = repo.dao.zones().first().single()
        asset = repo.createAsset(zone.id, "Router", true, "WIFI", "AA:BB:CC:DD:EE:00", "Rack A")
    }
    @After fun close() { db.close() }
    private fun session(id: String, mode: String = "INSPECTION", healthy: Boolean = true, demo: Boolean = false, start: Long = 1000) =
        Inspection(id, asset.zoneId, mode, start, start + 120000, 120, 1, 1, 1, 3, 30, 0, 0, healthy, healthy, demo, requestedSeconds = 120)
    private fun result(s: Inspection, status: String, rssi: Int? = -50) = AssetResult(s.id, asset.id, s.endMs, status, rssi, 3, 0, null)
    @Test fun explicitBaselineUsesConfirmedMedianAndRejectsDemoOrLab() = runBlocking {
        val s = session("normal"); repo.save(s, listOf(result(s,"LEARNING"))); repo.confirmBaseline(asset.id,s.id)
        assertEquals(-50,repo.dao.baseline(asset.id)?.rssi)
        assertEquals("WEAK", InspectionPolicy.evaluate(listOf(-65,-64,-66),emptyList(),0,true,repo.dao.baseline(asset.id)?.rssi).status.name)
        for (s2 in listOf(session("lab", "LAB"),session("demo",demo=true),session("limited",healthy=false))) {
            repo.save(s2,listOf(result(s2,"LEARNING")))
            try { repo.confirmBaseline(asset.id,s2.id); fail() } catch (_: IllegalArgumentException) { }
        }
        assertEquals("normal",repo.dao.baseline(asset.id)?.sourceSessionId)
    }
    @Test fun oneTicketAccumulatesFailuresAndClosesOnlyAfterFreshNormalRecheck() = runBlocking {
        val seed = session("baseline"); repo.save(seed,listOf(result(seed,"LEARNING")));repo.confirmBaseline(asset.id,seed.id)
        val s = session("weak",start=150000);repo.save(s,listOf(result(s,"WEAK")))
        val id = repo.dao.issues().first().single().id
        val repeat = session("weak-again", start = 200000);repo.save(repeat,listOf(result(repeat,"MISSING",null)))
        assertEquals(1,repo.dao.issues().first().size)
        repo.updateIssue(id,"WAITING_RECHECK","Replaced power supply")
        val at = repo.dao.issue(id)!!.updatedAt
        val inFlight = session("old-running",start=at-1000);repo.save(inFlight,listOf(result(inFlight,"NORMAL")))
        assertEquals("WAITING_RECHECK",repo.dao.issue(id)!!.state)
        val limited = session("limited",healthy=false,start=at+1000);repo.save(limited,listOf(result(limited,"NORMAL")))
        assertEquals("WAITING_RECHECK",repo.dao.issue(id)!!.state)
        val failed = session("still-weak",start=at+200000);repo.save(failed,listOf(result(failed,"WEAK")))
        assertEquals("OPEN",repo.dao.issue(id)!!.state)
        repo.updateIssue(id,"WAITING_RECHECK","Moved back to calibrated position")
        val good = session("recovered",start=maxOf(failed.endMs+1000,repo.dao.issue(id)!!.updatedAt+1000));repo.save(good,listOf(result(good,"NORMAL")))
        assertEquals("CLOSED",repo.dao.issue(id)!!.state)
        assertTrue(repo.dao.issueEvents().first().any { it.action=="RECHECK_PASSED" && it.sessionId==good.id })
        assertTrue(repo.dao.issueEvents().first().any { it.note=="Replaced power supply" })
    }
    @Test fun demoLabAndLimitedScansNeverCreateFaultTicketsAndClosedTicketsAreImmutable() = runBlocking {
        for(s in listOf(session("demo",demo=true),session("lab","LAB"),session("limited",healthy=false))) repo.save(s,listOf(result(s,"MISSING",null)))
        assertTrue(repo.dao.issues().first().isEmpty())
        val real=session("real");repo.save(real,listOf(result(real,"REVIEW",null)))
        val issue=repo.dao.issues().first().single()
        try { repo.updateIssue(issue.id,"WAITING_RECHECK","");fail() } catch (_:IllegalArgumentException) { }
        try { repo.updateIssue(issue.id,"CLOSED","skip recheck");fail() } catch (_:IllegalArgumentException) { }
        assertEquals("OPEN",repo.dao.issue(issue.id)!!.state)
    }
    @Test fun versionThreeMigrationRetainsAssetsAndAddsEmptyWorkflowTables() = runBlocking {
        val c=ApplicationProvider.getApplicationContext<Context>();val name="maintenance-v3.db";c.deleteDatabase(name)
        c.openOrCreateDatabase(name,0,null).use { sql ->
            javaClass.classLoader!!.getResourceAsStream("v1-schema.sql")!!.bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).forEach { sql.execSQL(it) } }
            sql.execSQL("ALTER TABLE sessions ADD COLUMN imported INTEGER NOT NULL DEFAULT 0")
            sql.execSQL("ALTER TABLE sessions ADD COLUMN requestedSeconds INTEGER")
            sql.execSQL("CREATE TABLE signal_groups (sessionId TEXT NOT NULL, groupCode TEXT NOT NULL, radio TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(sessionId,groupCode,radio), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            sql.execSQL("CREATE INDEX index_signal_groups_sessionId ON signal_groups(sessionId)")
        }
        // Build a v3 fixture using SQL, rather than copying current Room's schema identity.
        c.openOrCreateDatabase(name,0,null).use { sql ->
            sql.execSQL("ALTER TABLE assets ADD COLUMN location TEXT NOT NULL DEFAULT ''")
            sql.execSQL("ALTER TABLE assets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            sql.execSQL("INSERT INTO cinemas VALUES ('c','Existing cinema')");sql.execSQL("INSERT INTO zones VALUES ('z','c','Lobby')")
            sql.execSQL("INSERT INTO assets VALUES ('a','z','Existing asset',1,'Rack A','Keep notes')");sql.version=3
        }
        val upgraded=Room.databaseBuilder(c,CinemaDatabase::class.java,name).addMigrations(CinemaDatabase.MIGRATION_3_4).build()
        assertEquals("Rack A",upgraded.dao().asset("a")!!.location);assertTrue(upgraded.dao().issues().first().isEmpty());assertTrue(upgraded.dao().baselines().first().isEmpty())
        upgraded.close();c.deleteDatabase(name)
    }
}
