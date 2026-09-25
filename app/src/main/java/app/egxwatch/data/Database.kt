package app.egxwatch.data

import androidx.room.*
import app.egxwatch.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "watchlists")
data class Watchlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "instruments", primaryKeys = ["listId", "id"], foreignKeys = [ForeignKey(
    entity = Watchlist::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")])
data class TrackedInstrument(val listId: Long, val id: String, val ticker: String, val name: String,
    val type: String, val currency: String, val identitySource: String, val verifiedAt: String,
    val value: String? = null, val previous: String? = null, val kind: String? = null,
    val timestamp: String? = null, val quoteSource: String? = null, val delayMinutes: Int? = null,
    val lastCheck: String? = null, val error: String? = null,
    val timestampBasis: String = "EXCHANGE",
    val absoluteThreshold: String? = null, val percentThreshold: String? = null,
    val baselineKey: String? = null) {
    fun instrument() = Instrument(id, ticker, name, InstrumentType.valueOf(type), currency, identitySource, verifiedAt)
    companion object {
        fun from(listId: Long, value: Instrument) = TrackedInstrument(listId, value.id, value.ticker, value.name,
            value.type.name, value.currency, value.source, value.verifiedAt)
    }
}

@Entity(tableName = "settings")
data class Settings(@PrimaryKey val id: Int = 1, val enabled: Boolean = false, val interval: Long = 15,
    val days: String = "1,2,3,4,7", val start: String = "10:00", val end: String = "14:30",
    val everyCheck: Boolean = false, val absoluteThreshold: String = "", val percentThreshold: String = "",
    val providerUrl: String = "", val theme: String = "System", val freeFeeds: Boolean = false) {
    fun policy() = MonitorPolicy(interval, days.split(',').map { DayOfWeek.of(it.toInt()) }.toSet(),
        LocalTime.parse(start), LocalTime.parse(end), everyCheck,
        absoluteThreshold.takeIf { it.isNotBlank() }?.toBigDecimal(), percentThreshold.takeIf { it.isNotBlank() }?.toBigDecimal())
}

@Entity(tableName = "alerts")
data class Alert(@PrimaryKey(autoGenerate = true) val id: Long = 0, val instrumentId: String, val ticker: String,
    val name: String, val current: String, val previous: String?, val absolute: String?, val percent: String?,
    val currency: String, val kind: String, val dataTimestamp: String, val checkedAt: String,
    val source: String, val delivery: String = "Pending", val timestampBasis: String = "EXCHANGE")

@Dao
interface WatchDao {
    @Query("SELECT * FROM watchlists ORDER BY id") fun lists(): Flow<List<Watchlist>>
    @Query("SELECT * FROM watchlists ORDER BY id") suspend fun getLists(): List<Watchlist>
    @Insert suspend fun insertList(list: Watchlist): Long
    @Delete suspend fun deleteList(list: Watchlist)
    @Query("SELECT * FROM instruments ORDER BY ticker") fun instruments(): Flow<List<TrackedInstrument>>
    @Query("SELECT * FROM instruments ORDER BY ticker") suspend fun getInstruments(): List<TrackedInstrument>
    @Query("SELECT * FROM instruments WHERE listId = :listId AND id = :id") suspend fun getInstrument(listId: Long, id: String): TrackedInstrument?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(instrument: TrackedInstrument): Long
    @Update suspend fun update(instrument: TrackedInstrument)
    @Delete suspend fun remove(instrument: TrackedInstrument)
    @Query("SELECT * FROM settings WHERE id = 1") fun settings(): Flow<Settings?>
    @Query("SELECT * FROM settings WHERE id = 1") suspend fun getSettings(): Settings?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(settings: Settings)
    @Query("SELECT * FROM alerts ORDER BY id DESC LIMIT 500") fun history(): Flow<List<Alert>>
    @Insert suspend fun insertAlert(alert: Alert): Long
    @Query("UPDATE alerts SET delivery = :delivery WHERE id = :id") suspend fun delivery(id: Long, delivery: String)
    @Query("DELETE FROM alerts WHERE id NOT IN (SELECT id FROM alerts ORDER BY id DESC LIMIT 500)") suspend fun trimHistory()
    @Query("DELETE FROM alerts") suspend fun clearHistory()
    @Query("UPDATE instruments SET baselineKey = NULL, previous = NULL") suspend fun invalidateBaselines()
}

@Database(entities = [Watchlist::class, TrackedInstrument::class, Settings::class, Alert::class, EngineConfig::class, MarketSnapshot::class, HistoricalPrice::class, AnalysisResult::class, OpportunityEvent::class, ProviderHealth::class, EngineStatus::class, OpportunityAlertState::class, MarketObservation::class, ObservedSession::class, CollectionSeries::class, GoldConfig::class, GoldStatus::class, ForwardPreferences::class, GoldRule::class, GoldRuleState::class, CenterAlert::class], version = 5, exportSchema = false)
abstract class WatchDatabase : RoomDatabase() {
    abstract fun dao(): WatchDao
    abstract fun engineDao(): EngineDao
    abstract fun observationDao(): ObservationDao
    abstract fun forwardDao(): ForwardDao
    companion object {
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS engine_config (id INTEGER NOT NULL PRIMARY KEY, fallbackUrls TEXT NOT NULL, holidays TEXT NOT NULL, exceptions TEXT NOT NULL, enabled INTEGER NOT NULL, minimumScore INTEGER NOT NULL, minimumConfidence REAL NOT NULL, cooldownMinutes INTEGER NOT NULL, materialChange INTEGER NOT NULL, dailyLimit INTEGER NOT NULL, quietStart TEXT NOT NULL, quietEnd TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS market_snapshots (fingerprint TEXT NOT NULL PRIMARY KEY, instrumentId TEXT NOT NULL, observedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_market_snapshots_instrumentId ON market_snapshots(instrumentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS historical_prices (instrumentId TEXT NOT NULL PRIMARY KEY, fetchedAt INTEGER NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS analyses (instrumentId TEXT NOT NULL PRIMARY KEY, fingerprint TEXT NOT NULL, analyzedAt INTEGER NOT NULL, ticker TEXT NOT NULL, name TEXT NOT NULL, quoteJson TEXT NOT NULL, resultJson TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS opportunity_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, instrumentId TEXT NOT NULL, fingerprint TEXT NOT NULL, createdAt INTEGER NOT NULL, score INTEGER NOT NULL, state TEXT NOT NULL, ticker TEXT NOT NULL, name TEXT NOT NULL, quoteJson TEXT NOT NULL, resultJson TEXT NOT NULL, delivery TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_opportunity_events_instrumentId ON opportunity_events(instrumentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS provider_health (provider TEXT NOT NULL PRIMARY KEY, failures INTEGER NOT NULL, retryAt INTEGER NOT NULL, lastSuccess INTEGER, status TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS engine_status (id INTEGER NOT NULL PRIMARY KEY, lastAttempt INTEGER, lastSuccess INTEGER, lastFresh INTEGER, message TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS opportunity_state (instrumentId TEXT NOT NULL PRIMARY KEY, fingerprint TEXT NOT NULL, score INTEGER NOT NULL, state TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("UPDATE settings SET freeFeeds=0")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE instruments ADD COLUMN baselineKey TEXT")
            }
        }
    }
}
