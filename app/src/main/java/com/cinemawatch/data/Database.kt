package com.cinemawatch.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "cinemas")
data class Cinema(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "zones", foreignKeys = [ForeignKey(entity = Cinema::class, parentColumns = ["id"], childColumns = ["cinemaId"], onDelete = ForeignKey.CASCADE)], indices = [Index("cinemaId")])
data class Zone(@PrimaryKey val id: String, val cinemaId: String, val name: String)

@Entity(tableName = "assets", foreignKeys = [ForeignKey(entity = Zone::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.CASCADE)], indices = [Index("zoneId")])
data class CinemaAsset(@PrimaryKey val id: String, val zoneId: String, val name: String, val authorized: Boolean = true, @ColumnInfo(defaultValue = "''") val location: String = "", @ColumnInfo(defaultValue = "''") val notes: String = "")

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
    val planned: Int? = null, val actual: Int? = null, val gate: Int? = null, val point: String = "",
    @ColumnInfo(defaultValue = "0") val imported: Boolean = false, val requestedSeconds: Int? = null
)

/** Only a category, source and count; no unregistered identifiers or payloads. */
@Entity(tableName = "signal_groups", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId")], primaryKeys = ["sessionId", "groupCode", "radio"])
data class SignalGroupCount(val sessionId: String, val groupCode: String, val radio: String, val count: Int)

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
    @Query("SELECT * FROM signal_groups") fun groups(): Flow<List<SignalGroupCount>>
    @Query("SELECT * FROM assets WHERE id = :id") suspend fun asset(id: String): CinemaAsset?
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun session(id: String): Inspection?
    @Query("SELECT * FROM bindings") suspend fun allBindings(): List<RadioBinding>
    @Query("SELECT * FROM assets WHERE zoneId = :zone") suspend fun assetsIn(zone: String): List<CinemaAsset>
    @Query("SELECT * FROM results WHERE assetId = :asset ORDER BY at DESC LIMIT 12") suspend fun history(asset: String): List<AssetResult>
    @Insert suspend fun insertCinema(value: Cinema)
    @Insert suspend fun insertZone(value: Zone)
    @Insert suspend fun insertAsset(value: CinemaAsset)
    @Insert suspend fun insertBinding(value: RadioBinding)
    @Insert suspend fun insertSession(value: Inspection)
    @Insert suspend fun insertResults(values: List<AssetResult>)
    @Insert suspend fun insertGroups(values: List<SignalGroupCount>)
    @Query("UPDATE assets SET name = :name WHERE id = :id") suspend fun renameAsset(id: String, name: String)
    @Query("UPDATE assets SET name = :name, location = :location, notes = :notes WHERE id = :id") suspend fun updateAssetDetails(id: String, name: String, location: String, notes: String)
    @Query("DELETE FROM assets WHERE id = :id") suspend fun deleteAsset(id: String)
    @Query("DELETE FROM cinemas") suspend fun clear()
}

@Database(entities = [Cinema::class, Zone::class, CinemaAsset::class, RadioBinding::class, Inspection::class, AssetResult::class, SignalGroupCount::class], version = 3, exportSchema = false)
abstract class CinemaDatabase : RoomDatabase() {
    abstract fun dao(): CinemaDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN imported INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN requestedSeconds INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS signal_groups (sessionId TEXT NOT NULL, groupCode TEXT NOT NULL, radio TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(sessionId, groupCode, radio), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_groups_sessionId ON signal_groups (sessionId)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN location TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE assets ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }
        fun create(context: Context) = Room.databaseBuilder(context, CinemaDatabase::class.java, "cinemawatch.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
