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
    val absoluteThreshold: String? = null, val percentThreshold: String? = null) {
    fun instrument() = Instrument(id, ticker, name, InstrumentType.valueOf(type), currency, identitySource, verifiedAt)
    companion object {
        fun from(listId: Long, value: Instrument) = TrackedInstrument(listId, value.id, value.ticker, value.name,
            value.type.name, value.currency, value.source, value.verifiedAt)
    }
}

@Entity(tableName = "settings")
data class Settings(@PrimaryKey val id: Int = 1, val enabled: Boolean = false, val interval: Long = 15,
    val days: String = "1,2,3,4,7", val start: String = "00:00", val end: String = "00:00",
    val everyCheck: Boolean = false, val absoluteThreshold: String = "", val percentThreshold: String = "",
    val providerUrl: String = "", val theme: String = "System", val freeFeeds: Boolean = true) {
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
    @Query("UPDATE instruments SET value = NULL, previous = NULL, kind = NULL, timestamp = NULL, quoteSource = NULL, delayMinutes = NULL, lastCheck = NULL, error = NULL") suspend fun resetQuotes()
}

@Database(entities = [Watchlist::class, TrackedInstrument::class, Settings::class, Alert::class], version = 1, exportSchema = false)
abstract class WatchDatabase : RoomDatabase() { abstract fun dao(): WatchDao }
