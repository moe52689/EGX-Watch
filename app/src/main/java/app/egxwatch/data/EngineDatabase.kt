package app.egxwatch.data

import androidx.room.*
import app.egxwatch.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.*

@Entity(tableName="engine_config")
data class EngineConfig(@PrimaryKey val id:Int=1, val fallbackUrls:String="", val holidays:String="", val exceptions:String="",
    val enabled:Boolean=false, val minimumScore:Int=75, val minimumConfidence:Double=.70, val cooldownMinutes:Long=60,
    val materialChange:Int=10, val dailyLimit:Int=10, val quietStart:String="22:00", val quietEnd:String="08:00") {
    fun rules()=OpportunityRules(enabled,minimumScore,minimumConfidence,cooldownMinutes,materialChange,dailyLimit,LocalTime.parse(quietStart),LocalTime.parse(quietEnd))
    fun calendar(settings:Settings)=SessionCalendar.parse(settings.policy(),holidays,exceptions)
    fun urls()=fallbackUrls.lines().map(String::trim).filter(String::isNotEmpty).also { require(it.size<=2); it.forEach(GatewayProvider::validateBaseUrl) }
}
@Entity(tableName="market_snapshots",indices=[Index("instrumentId")])
data class MarketSnapshot(@PrimaryKey val fingerprint:String,val instrumentId:String,val observedAt:Long,val payload:String)
@Entity(tableName="historical_prices")
data class HistoricalPrice(@PrimaryKey val instrumentId:String,val fetchedAt:Long,val payload:String)
@Entity(tableName="analyses")
data class AnalysisResult(@PrimaryKey val instrumentId:String,val fingerprint:String,val analyzedAt:Long,
    val ticker:String,val name:String,val quoteJson:String,val resultJson:String) {
    fun analysis()=EngineJson.analysis(resultJson)
    fun quote()=GatewayProvider.parseQuote(org.json.JSONObject(quoteJson))
}
@Entity(tableName="opportunity_events",indices=[Index("instrumentId")])
data class OpportunityEvent(@PrimaryKey(autoGenerate=true) val id:Long=0,val instrumentId:String,val fingerprint:String,
    val createdAt:Long,val score:Int,val state:String,val ticker:String,val name:String,val quoteJson:String,val resultJson:String,val delivery:String="Pending")
@Entity(tableName="opportunity_state")
data class OpportunityAlertState(@PrimaryKey val instrumentId:String,val fingerprint:String,val score:Int,val state:String,val createdAt:Long)
@Entity(tableName="provider_health")
data class ProviderHealth(@PrimaryKey val provider:String,val failures:Int=0,val retryAt:Long=0,val lastSuccess:Long?=null,val status:String="Ready")
@Entity(tableName="engine_status")
data class EngineStatus(@PrimaryKey val id:Int=1,val lastAttempt:Long?=null,val lastSuccess:Long?=null,val lastFresh:Long?=null,val message:String="Awaiting a check")

@Dao
interface EngineDao {
    @Query("SELECT * FROM engine_config WHERE id=1") suspend fun config():EngineConfig?
    @Query("SELECT * FROM engine_config WHERE id=1") fun configFlow():Flow<EngineConfig?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(config:EngineConfig)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun snapshot(value:MarketSnapshot):Long
    @Query("SELECT * FROM market_snapshots WHERE instrumentId=:id ORDER BY observedAt DESC LIMIT 1") suspend fun latest(id:String):MarketSnapshot?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun history(value:HistoricalPrice)
    @Query("SELECT * FROM historical_prices WHERE instrumentId=:id") suspend fun history(id:String):HistoricalPrice?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun analysis(value:AnalysisResult)
    @Query("SELECT * FROM analyses ORDER BY ticker") fun analyses():Flow<List<AnalysisResult>>
    @Query("SELECT * FROM analyses WHERE instrumentId=:id") suspend fun analysis(id:String):AnalysisResult?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun alertState(value:OpportunityAlertState)
    @Query("SELECT * FROM opportunity_state WHERE instrumentId=:id") suspend fun alertState(id:String):OpportunityAlertState?
    @Insert suspend fun event(value:OpportunityEvent):Long
    @Query("SELECT * FROM opportunity_events ORDER BY id DESC LIMIT 500") fun events():Flow<List<OpportunityEvent>>
    @Query("SELECT * FROM opportunity_events WHERE instrumentId=:id ORDER BY id DESC LIMIT 1") suspend fun lastEvent(id:String):OpportunityEvent?
    @Query("SELECT COUNT(*) FROM opportunity_events WHERE createdAt>=:start") suspend fun dailyCount(start:Long):Int
    @Query("UPDATE opportunity_events SET delivery=:status WHERE id=:id") suspend fun delivery(id:Long,status:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun health(value:ProviderHealth)
    @Query("SELECT * FROM provider_health WHERE provider=:id") suspend fun health(id:String):ProviderHealth?
    @Query("SELECT * FROM provider_health") fun healthFlow():Flow<List<ProviderHealth>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun status(value:EngineStatus)
    @Query("SELECT * FROM engine_status WHERE id=1") suspend fun status():EngineStatus?
    @Query("SELECT * FROM engine_status WHERE id=1") fun statusFlow():Flow<EngineStatus?>
    @Query("DELETE FROM market_snapshots WHERE fingerprint NOT IN (SELECT fingerprint FROM market_snapshots ORDER BY observedAt DESC LIMIT 5000)") suspend fun trimSnapshots()
    @Query("DELETE FROM opportunity_events WHERE id NOT IN (SELECT id FROM opportunity_events ORDER BY id DESC LIMIT 500)") suspend fun trimEvents()
    @Query("DELETE FROM historical_prices WHERE instrumentId NOT IN (SELECT id FROM instruments)") suspend fun pruneHistory()
    @Query("DELETE FROM analyses WHERE instrumentId NOT IN (SELECT id FROM instruments)") suspend fun pruneAnalyses()
    @Query("DELETE FROM market_snapshots WHERE instrumentId NOT IN (SELECT id FROM instruments)") suspend fun pruneSnapshots()
    @Query("DELETE FROM provider_health WHERE provider NOT IN (:providers)") suspend fun pruneProviders(providers:List<String>)
}
