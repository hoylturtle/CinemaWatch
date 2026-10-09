package com.cinemawatch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cinemawatch.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class AssetAndImportTest {
    private lateinit var db: CinemaDatabase
    private lateinit var repo: CinemaRepository
    @Before fun before() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), CinemaDatabase::class.java).build(); repo = CinemaRepository(db) }
    @After fun after() { db.close() }
    @Test fun assetsCanBeCreatedWithoutAnyScanAndLaterReceiveMultipleBindings() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema", 3, "Hall")
        val asset = repo.createAsset(zone, "Projector", true)
        assertTrue(repo.dao.allBindings().isEmpty())
        repo.addBinding(asset.id, "WIFI", "aa-bb-cc-dd-ee-00", true)
        repo.addBinding(asset.id, "BLE", "AA:BB:CC:DD:EE:02", true)
        assertEquals(setOf(asset.id), repo.dao.allBindings().map { it.assetId }.toSet())
        assertEquals(2, repo.dao.allBindings().size)
    }
    @Test fun assetDetailsEditPreservesIdentityAndBindingsAndEscapesCsv() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema", 1, "Hall")
        val asset = repo.createAsset(zone, "Projector", true, "WIFI", "AA:BB:CC:DD:EE:00", "Rear wall", "Maintenance")
        repo.updateAssetDetails(asset.id, "Projector A", "Rack, A", "Check \"power\"\nFriday")
        val edited = repo.dao.asset(asset.id)!!
        assertEquals("Rack, A", edited.location); assertEquals("Check \"power\"\nFriday", edited.notes)
        assertEquals(asset.id, repo.dao.allBindings().single().assetId)
        val session = Inspection(UUID.randomUUID().toString(), zone, "INSPECTION", 1000, 121000, 120, 0, 0, 1, 3, 30, 0, 0, true, true, false)
        repo.save(session, listOf(AssetResult(session.id, asset.id, 121000, "NORMAL", -50, 2, 0, -50)))
        val csv = ReportExport.csv(listOf(session), repo.dao.zones().first(), repo.dao.cinemas().first(), repo.dao.results().first(), listOf(edited))
        assertTrue(csv.contains("Rack, A")); assertTrue(csv.contains("Friday")); assertEquals(1, HistoryImport.parse(csv).size)
    }
    @Test fun bleAddressesDoNotUseWifiMulticastBitRules() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema", 1, "Hall")
        val asset = repo.createAsset(zone, "BLE sensor", true, "BLE", "D7:12:34:56:78:90")
        assertEquals("D7:12:34:56:78:90", repo.dao.allBindings().single().address)
        try { repo.addBinding(asset.id, "WIFI", "D7:12:34:56:78:90", true); fail() } catch (_: InvalidRadioAddressException) { }
        assertEquals(1, repo.dao.allBindings().size)
    }
    @Test fun invalidAddressAndDuplicateCannotLeaveGhostAssets() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema", 1, "Hall")
        try { repo.createAsset(zone, "Bad", true, "WIFI", "not a mac"); fail() } catch (_: InvalidRadioAddressException) { }
        assertTrue(repo.dao.assets().first().isEmpty())
        repo.register(zone, "Real", "WIFI", "AA:BB:CC:DD:EE:00", true)
        try { repo.register(zone, "Duplicate", "WIFI", "aa-bb-cc-dd-ee-00", true); fail() } catch (_: DuplicateRadioException) { }
        assertEquals(1, repo.dao.assets().first().size)
    }
    @Test fun bulkHallsContinueNumberingAndAreAtomic() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema", 3, "Hall")
        assertEquals("Hall 1", repo.dao.zones().first().find { it.id == zone }?.name)
        val cinema = repo.dao.cinemas().first().single()
        repo.addHalls(cinema.id, 2, "Hall")
        assertEquals(setOf("Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5"), repo.dao.zones().first().map { it.name }.toSet())
        try { repo.createCinemaWithHalls("Invalid", 101, "Hall"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.dao.cinemas().first().size)
    }
    @Test fun commonAreasAreCreatedAtomicallyAndRepeatedAddAcrossLanguagesIsSafe() = runBlocking {
        val simplified = listOf("大堂", "通道", "办公区", "放映层")
        val firstHall = repo.createCinemaWithHalls("Cinema", 2, "Hall", simplified)
        val cinema = repo.dao.cinemas().first().single()
        assertEquals("Hall 1", repo.dao.zones().first().single { it.id == firstHall }.name)
        assertEquals(setOf("Hall 1", "Hall 2") + simplified, repo.dao.zones().first().map { it.name }.toSet())
        val asset = repo.createAsset(firstHall, "Projector", true)
        assertTrue(repo.addCommonZones(cinema.id, listOf("Lobby", "Corridor", "Office", "Projection floor")).isEmpty())
        assertTrue(repo.addCommonZones(cinema.id, listOf("大堂", "通道", "辦公區", "放映層")).isEmpty())
        assertEquals(6, repo.dao.zones().first().size)
        assertEquals(asset, repo.dao.asset(asset.id))
        try { repo.createCinemaWithHalls("Bad", 2, "Hall", listOf("Lobby")); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.dao.cinemas().first().size)
    }
    @Test fun existingCinemaGetsOnlyMissingCommonAreasWithoutChangingOtherCinemas() = runBlocking {
        repo.createCinemaWithHalls("Existing", 1, "Hall")
        val existing = repo.dao.cinemas().first().single()
        repo.createZone(existing.id, "大堂")
        repo.createZone(existing.id, "Warehouse")
        repo.createCinemaWithHalls("Other", 1, "Hall")
        val added = repo.addCommonZones(existing.id, listOf("大堂", "通道", "办公区", "放映层"))
        assertEquals(3, added.size)
        assertTrue(repo.addCommonZones(existing.id, listOf("大堂", "通道", "办公区", "放映层")).isEmpty())
        assertEquals(6, repo.dao.zones().first().count { it.cinemaId == existing.id })
        assertEquals(1, repo.dao.zones().first().count { it.cinemaId != existing.id })
    }
    @Test fun localizedCsvRoundTripRestoresAggregatesButNotAssetIdentities() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema, \"East\"", 1, "Hall")
        val session = Inspection(UUID.randomUUID().toString(), zone, "INSPECTION", 1000, 121000, 120, 2, 1, 1, 3, 30, 0, 0, true, true, false, point = "Door\nA", requestedSeconds = 120)
        val asset = repo.createAsset(zone, "Projector", true, "WIFI", "AA:BB:CC:DD:EE:00")
        val groups = listOf(SignalGroupCount(session.id, "ACCESS_POINT", "WIFI", 2), SignalGroupCount(session.id, "UNKNOWN", "BLE", 1))
        repo.save(session, listOf(AssetResult(session.id, asset.id, 121000, "NORMAL", -50, 2, 0, -49)), groups)
        val c = ApplicationProvider.getApplicationContext<Context>()
        val config = android.content.res.Configuration(c.resources.configuration).apply { setLocale(java.util.Locale.SIMPLIFIED_CHINESE) }
        val csv = ReportExport.csv(listOf(session), repo.dao.zones().first(), repo.dao.cinemas().first(), repo.dao.results().first(), repo.dao.assets().first(), c.createConfigurationContext(config), groups)
        assertFalse(csv.contains("AA:BB:CC:DD:EE:00"))
        repo.dao.clear()
        assertEquals(1, repo.importHistory(csv)); assertEquals(0, repo.importHistory(csv))
        assertTrue(repo.dao.assets().first().isEmpty()); assertTrue(repo.dao.allBindings().isEmpty()); assertTrue(repo.dao.results().first().isEmpty())
        val imported = repo.dao.sessions().first().single()
        assertTrue(imported.imported); assertEquals("Door\nA", imported.point); assertEquals(2, repo.dao.groups().first().size)
    }
    @Test fun old25ColumnExportImportsAndRejectsBadInputWithoutPartialWrites() = runBlocking {
        val zone = repo.createCinemaWithHalls("Cinema", 1, "Hall")
        val session = Inspection(UUID.randomUUID().toString(), zone, "INSPECTION", 1000, 435000, 434, 19, 56, 0, 3, 30, 0, 0, true, true, false)
        val csv = ReportExport.csv(listOf(session), repo.dao.zones().first(), repo.dao.cinemas().first(), emptyList(), emptyList())
        val old = csv.lines().filter(String::isNotBlank).joinToString("\n") { it.split(',').take(25).joinToString(",") }
        assertEquals(434, HistoryImport.parse(old).single().session.durationSeconds)
        repo.dao.clear(); assertEquals(1, repo.importHistory(old))
        val before = repo.dao.sessions().first().size
        try { repo.importHistory(old + "\nbroken,record"); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.dao.sessions().first().size)
    }
    @Test fun versionTwoMigrationAddsEmptyDetailsWithoutChangingBindingsOrHistory() = runBlocking<Unit> {
        val c = ApplicationProvider.getApplicationContext<Context>(); val name = "migration-v2.db"; c.deleteDatabase(name)
        c.openOrCreateDatabase(name, 0, null).use { sqlite ->
            javaClass.classLoader!!.getResourceAsStream("v1-schema.sql")!!.bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).forEach { sqlite.execSQL(it) } }
            sqlite.execSQL("ALTER TABLE sessions ADD COLUMN imported INTEGER NOT NULL DEFAULT 0")
            sqlite.execSQL("ALTER TABLE sessions ADD COLUMN requestedSeconds INTEGER")
            sqlite.execSQL("CREATE TABLE signal_groups (sessionId TEXT NOT NULL, groupCode TEXT NOT NULL, radio TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(sessionId, groupCode, radio), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            sqlite.execSQL("CREATE INDEX index_signal_groups_sessionId ON signal_groups (sessionId)")
            sqlite.execSQL("INSERT INTO cinemas VALUES ('c','Old cinema')")
            sqlite.execSQL("INSERT INTO zones VALUES ('z','c','Old hall')")
            sqlite.execSQL("INSERT INTO assets VALUES ('a','z','Old projector',1)")
            sqlite.execSQL("INSERT INTO bindings VALUES ('b','a','WIFI','AA:BB:CC:DD:EE:00')")
            sqlite.execSQL("INSERT INTO sessions VALUES ('s','z','INSPECTION',1000,121000,120,2,4,1,3,30,0,0,1,1,0,NULL,NULL,NULL,'',0,120)")
            sqlite.version = 2
        }
        val upgraded = Room.databaseBuilder(c, CinemaDatabase::class.java, name).addMigrations(CinemaDatabase.MIGRATION_2_3, CinemaDatabase.MIGRATION_3_4).build()
        val old = upgraded.dao().asset("a")!!
        assertEquals("", old.location); assertEquals("", old.notes); assertEquals("Old projector", old.name)
        assertEquals("a", upgraded.dao().allBindings().single().assetId)
        assertEquals(120, upgraded.dao().sessions().first().single().durationSeconds)
        upgraded.close(); c.deleteDatabase(name)
    }
    @Test fun versionOneMigrationPreservesCinemaAssetBindingAndHistory() = runBlocking<Unit> {
        val c = ApplicationProvider.getApplicationContext<Context>(); val name = "migration-v1.db"; c.deleteDatabase(name)
        c.openOrCreateDatabase(name, 0, null).use { sqlite ->
            javaClass.classLoader!!.getResourceAsStream("v1-schema.sql")!!.bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).forEach { sqlite.execSQL(it) } }
            sqlite.execSQL("INSERT INTO cinemas VALUES ('c','Old cinema')")
            sqlite.execSQL("INSERT INTO zones VALUES ('z','c','Old hall')")
            sqlite.execSQL("INSERT INTO assets VALUES ('a','z','Old projector',1)")
            sqlite.execSQL("INSERT INTO bindings VALUES ('b','a','WIFI','AA:BB:CC:DD:EE:00')")
            sqlite.execSQL("INSERT INTO sessions VALUES ('s','z','INSPECTION',1000,121000,120,2,4,1,3,30,0,0,1,1,0,NULL,NULL,NULL,'')")
            sqlite.version = 1
        }
        val upgraded = Room.databaseBuilder(c, CinemaDatabase::class.java, name).addMigrations(CinemaDatabase.MIGRATION_1_2, CinemaDatabase.MIGRATION_2_3, CinemaDatabase.MIGRATION_3_4).build()
        assertEquals("Old projector", upgraded.dao().assets().first().single().name)
        assertEquals("AA:BB:CC:DD:EE:00", upgraded.dao().allBindings().single().address)
        assertEquals(120, upgraded.dao().sessions().first().single().durationSeconds)
        assertTrue(upgraded.dao().groups().first().isEmpty())
        upgraded.close(); c.deleteDatabase(name)
    }
}
