package com.cinemawatch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cinemawatch.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class PersistenceTest {
    private lateinit var db:CinemaDatabase
    private lateinit var repo:CinemaRepository
    @Before fun setUp() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),CinemaDatabase::class.java).build();repo=CinemaRepository(db) }
    @After fun tearDown() { db.close() }
    @Test fun unconfirmedIdentityRollsBack() = runBlocking {
        repo.createCinema("測試影院","一廳")
        val zone=repo.dao.zones().first().single()
        try { repo.register(zone.id,"未授權裝置","BLE","AA:BB:CC:DD:EE:FF",false);fail() } catch (_:IllegalArgumentException) { }
        assertTrue(repo.dao.assets().first().isEmpty());assertTrue(repo.dao.allBindings().isEmpty())
    }
    @Test fun radioIsEvidenceNotAssetIdentifierAndDuplicateIsAtomic() = runBlocking {
        repo.createCinema("測試影院","一廳")
        val zone=repo.dao.zones().first().single()
        repo.register(zone.id,"影院感測器","BLE","AA:BB:CC:DD:EE:FF",true)
        val asset=repo.dao.assets().first().single()
        assertNotEquals("AA:BB:CC:DD:EE:FF",asset.id)
        try { repo.register(zone.id,"重複綁定","BLE","AA:BB:CC:DD:EE:FF",true);fail() } catch (_:android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1,repo.dao.assets().first().size)
    }
    @Test fun aggregateSchemaHasNoRawRadioColumnsAndSurvivesReopen() = runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="roundtrip-test.db";context.deleteDatabase(name)
        val persistent=Room.databaseBuilder(context,CinemaDatabase::class.java,name).build()
        val r=CinemaRepository(persistent)
        r.createCinema("甲影院","一廳")
        val zone=r.dao.zones().first().single()
        val session=Inspection("s",zone.id,"LAB",1000,121000,120,2,4,0,3,30,0,0,true,true,false)
        r.save(session,emptyList());persistent.close()
        val reopened=Room.databaseBuilder(context,CinemaDatabase::class.java,name).build()
        assertEquals(session,reopened.dao().sessions().first().single())
        val columns=mutableListOf<String>()
        reopened.openHelper.readableDatabase.query("PRAGMA table_info(sessions)").use { c -> while(c.moveToNext())columns+=c.getString(c.getColumnIndexOrThrow("name")) }
        assertFalse(columns.any { it.lowercase() in listOf("mac","address","raw","payload","latitude","longitude") })
        reopened.close();context.deleteDatabase(name)
    }
    @Test fun exportNeverIncludesBindingAndGuardsFormula() = runBlocking {
        repo.createCinema("=2+2","一廳")
        val zone=repo.dao.zones().first().single()
        repo.register(zone.id,"影院感測器","BLE","AA:BB:CC:DD:EE:FF",true)
        val session=Inspection("s",zone.id,"LAB",1000,121000,120,2,4,0,3,30,0,0,true,true,false)
        val csv=ReportExport.csv(listOf(session),repo.dao.zones().first(),repo.dao.cinemas().first(),emptyList(),repo.dao.assets().first())
        assertFalse(csv.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(csv.contains("'=2+2"))
    }
}
