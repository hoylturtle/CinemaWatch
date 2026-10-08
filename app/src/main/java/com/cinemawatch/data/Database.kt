package com.cinemawatch.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cinemas")
data class Cinema(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "zones", foreignKeys = [ForeignKey(entity = Cinema::class, parentColumns = ["id"], childColumns = ["cinemaId"], onDelete = ForeignKey.CASCADE)], indices = [Index("cinemaId")])
data class Zone(@PrimaryKey val id: String, val cinemaId: String, val name: String)

@Entity(tableName = "assets", foreignKeys = [ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.CASCADE)], indices = [Index("zoneId")])
data class CinemaAsset(@PrimaryKey val id: String, val zoneId: String, val name: String, val authorized: Boolean = true)

/** One asset may have multiple registered radios. Address is evidence, never the asset ID. */
@Entity(tableName = "bindings", foreignKeys = [ForeignKey(entity = CinemaAsset::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("assetId"), Index(value = ["radio", "address"], unique = true)])
data class RadioBinding(@PrimaryKey val id: String, val assetId: String, val radio: String, val address: String)

/** Aggregate-only session: no customer address, name, payload or location columns. */
@Entity(tableName = "sessions", foreignKeys = [ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.CASCADE)], indices = [Index("zoneId")])
data class Inspection(
    @PrimaryKey val id: String, val zoneId: String, val mode: String,
    val startMs: Long, val endMs: Long, val durationSeconds: Int,
    val wifiCount: Int, val bleCount: Int, val assetCount: Int,
    val wifiBatches: Int, val bleEvents: Int, val discarded: Int, val dropped: Int,
    val wifiHealthy: Boolean, val bleHealthy: Boolean, val demo: Boolean,
    val planned: Int? = null, val actual: Int? = null, val gate: Int? = null, val point: String = ""
)

@Entity(tableName = "results", foreignKeys = [
    ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CinemaAsset::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("sessionId"), Index("assetId")], primaryKeys = ["sessionId", "assetId"])
data class AssetResult(val sessionId: String, val assetId: String, val at: Long, val status: String, val medianRssi: Int?, val spread: Int, val consecutiveMisses: Int, val baselineRssi: Int?)

@Dao
interface CinemaDao {
    @Query("SELECT * FROM cinemas ORDER BY name") fun cinemas(): Flow<List<Cinema>>
    @Query("SELECT * FROM zones ORDER BY name") fun zones(): Flow<List<Zone>>
    @Query("SELECT * FROM assets ORDER BY name") fun assets(): Flow<List<CinemaAsset>>
    @Query("SELECT * FROM bindings") fun bindings(): Flow<List<RadioBinding>>
    @Query("SELECT * FROM sessions ORDER BY startMs DESC") fun sessions(): Flow<List<Inspection>>
    @Query("SELECT * FROM results ORDER BY at DESC") fun results(): Flow<List<AssetResult>>
    @Query("SELECT * FROM bindings") suspend fun allBindings(): List<RadioBinding>
    @Query("SELECT * FROM assets WHERE zoneId = :zone") suspend fun assetsIn(zone: String): List<CinemaAsset>
    @Query("SELECT * FROM results WHERE assetId = :asset ORDER BY at DESC LIMIT 12") suspend fun history(asset: String): List<AssetResult>
    @Insert suspend fun insertCinema(value: Cinema)
    @Insert suspend fun insertZone(value: Zone)
    @Insert suspend fun insertAsset(value: CinemaAsset)
    @Insert suspend fun insertBinding(value: RadioBinding)
    @Insert suspend fun insertSession(value: Inspection)
    @Insert suspend fun insertResults(values: List<AssetResult>)
    @Query("DELETE FROM assets WHERE id = :id") suspend fun deleteAsset(id: String)
    @Query("DELETE FROM cinemas") suspend fun clear()
}

@Database(entities = [Cinema::class, Zone::class, CinemaAsset::class, RadioBinding::class, Inspection::class, AssetResult::class], version = 1, exportSchema = false)
abstract class CinemaDatabase : RoomDatabase() {
    abstract fun dao(): CinemaDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, CinemaDatabase::class.java, "cinemawatch.db").build()
    }
}
