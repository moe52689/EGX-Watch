package app.egxwatch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="market_observations", indices=[Index(value=["instrumentId","providerTime"]), Index(value=["seriesKey","providerTime"],unique=true)])
data class MarketObservation(@PrimaryKey val id:String, val instrumentId:String, val ticker:String, val name:String,
    val seriesKey:String, val providerTime:Long, val receivedAt:Long, val sessionDate:String,
    val price:String, val currency:String, val provider:String, val freshness:String, val kind:String,
    val timestampBasis:String, val open:String?, val high:String?, val low:String?, val previousClose:String?,
    val volume:Long?, val bid:String?, val ask:String?, val changePercent:String?, val payload:String)

/** OHLC below is from prices actually sampled by this app, not official daily candles. */
@Entity(tableName="observed_sessions",primaryKeys=["seriesKey","sessionDate"],indices=[Index(value=["instrumentId","sessionDate"])])
data class ObservedSession(val seriesKey:String,val sessionDate:String,val instrumentId:String,
    val firstTime:Long,val lastTime:Long,val open:String,val high:String,val low:String,val close:String,
    val samples:Long,val validSamples:Long,val occupiedSlots:Long,val expectedSlots:Long,
    val firstVolume:Long?,val lastVolume:Long?,val lastSlot:Long)

@Entity(tableName="collection_series",indices=[Index("instrumentId")])
data class CollectionSeries(@PrimaryKey val seriesKey:String,val instrumentId:String,val startedAt:Long,
    val lastReceivedAt:Long,val lastProviderTime:Long,val lastFingerprint:String,val observations:Long)

data class HistoryStats(val observations:Long,val oldestAt:Long?,val newestAt:Long?)
data class ChartPoint(val time:Long,val price:Double,val high:Double,val low:Double,val samples:Long)

@Dao
interface ObservationDao {
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(value:MarketObservation):Long
    @Query("DELETE FROM market_observations WHERE id=:id") suspend fun deleteObservation(id:String)
    @Query("SELECT * FROM collection_series WHERE seriesKey=:key") suspend fun series(key:String):CollectionSeries?
    @Query("SELECT * FROM collection_series WHERE instrumentId=:id ORDER BY lastReceivedAt DESC LIMIT 1") suspend fun latestSeries(id:String):CollectionSeries?
    @Query("SELECT * FROM collection_series ORDER BY lastReceivedAt DESC") fun seriesFlow():Flow<List<CollectionSeries>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:CollectionSeries)
    @Query("SELECT * FROM observed_sessions WHERE seriesKey=:key AND sessionDate=:date") suspend fun session(key:String,date:String):ObservedSession?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:ObservedSession)
    @Query("SELECT * FROM observed_sessions WHERE seriesKey=:key ORDER BY sessionDate DESC LIMIT :limit") suspend fun sessions(key:String,limit:Int=260):List<ObservedSession>
    @Query("SELECT * FROM market_observations WHERE seriesKey=:key ORDER BY providerTime DESC LIMIT :limit") suspend fun recent(key:String,limit:Int=256):List<MarketObservation>
    @Query("SELECT * FROM market_observations WHERE instrumentId=:id ORDER BY receivedAt DESC LIMIT 1") fun latestFlow(id:String):Flow<MarketObservation?>
    @Query("SELECT * FROM market_observations WHERE instrumentId=:id ORDER BY receivedAt DESC LIMIT 1") suspend fun latest(id:String):MarketObservation?
    @Query("SELECT COALESCE(SUM(observations),0) AS observations, MIN(startedAt) AS oldestAt, MAX(lastReceivedAt) AS newestAt FROM collection_series WHERE instrumentId=:id") fun stats(id:String):Flow<HistoryStats>
    @Query("SELECT COALESCE(SUM(observations),0) AS observations, MIN(startedAt) AS oldestAt, MAX(lastReceivedAt) AS newestAt FROM collection_series") fun storageStats():Flow<HistoryStats>
    @Query("SELECT o.providerTime AS time, CAST(o.price AS REAL) AS price, b.high AS high, b.low AS low, b.samples AS samples FROM market_observations o JOIN (SELECT MAX(providerTime) AS lastTime, MAX(CAST(price AS REAL)) AS high, MIN(CAST(price AS REAL)) AS low, COUNT(*) AS samples FROM market_observations WHERE seriesKey=:key AND providerTime>=:start AND providerTime<=:end GROUP BY providerTime/:bucket) b ON o.seriesKey=:key AND o.providerTime=b.lastTime ORDER BY time LIMIT 600") fun chart(key:String,start:Long,end:Long,bucket:Long):Flow<List<ChartPoint>>
    @Query("SELECT * FROM observed_sessions WHERE seriesKey=:key AND lastTime>=:start AND firstTime<=:end ORDER BY sessionDate LIMIT 4000") fun sessionChart(key:String,start:Long,end:Long):Flow<List<ObservedSession>>
    @Query("DELETE FROM market_observations WHERE receivedAt<:cutoff AND EXISTS(SELECT 1 FROM observed_sessions s WHERE s.seriesKey=market_observations.seriesKey AND s.sessionDate=market_observations.sessionDate)") suspend fun compact(cutoff:Long)
    @Query("DELETE FROM market_observations WHERE receivedAt<:cutoff AND freshness NOT IN ('LIVE','DELAYED') AND id NOT IN (SELECT lastFingerprint FROM collection_series)") suspend fun compactUnusable(cutoff:Long)
    @Query("DELETE FROM market_observations WHERE instrumentId=:id") suspend fun deleteObservations(id:String)
    @Query("DELETE FROM observed_sessions WHERE instrumentId=:id") suspend fun deleteSessions(id:String)
    @Query("DELETE FROM collection_series WHERE instrumentId=:id") suspend fun deleteSeries(id:String)
    @Query("SELECT * FROM collection_series") suspend fun allSeries():List<CollectionSeries>
}
