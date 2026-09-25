package app.egxwatch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="gold_config")
data class GoldConfig(@PrimaryKey val id:Int=1,val enabled:Boolean=false,val freeProvider:Boolean=true,
    val providerUrl:String="",val fallbackUrl:String="",val interval:Long=15,val days:String="1,2,3,4,5,6,7",
    val start:String="00:00",val end:String="00:00",val zone:String="UTC",val holidays:String="") {
    fun validate() {
        require(interval in 15..525600);java.time.ZoneId.of(zone)
        require(days.split(',').map { java.time.DayOfWeek.of(it.toInt()) }.isNotEmpty())
        java.time.LocalTime.parse(start);java.time.LocalTime.parse(end)
        holidays.split(',', '\n').filter { it.isNotBlank() }.forEach { java.time.LocalDate.parse(it.trim()) }
        listOf(providerUrl,fallbackUrl).filter { it.isNotBlank() }.forEach(GatewayProvider::validateBaseUrl)
    }
}
@Entity(tableName="gold_status")
data class GoldStatus(@PrimaryKey val id:Int=1,val lastAttempt:Long?=null,val lastSuccess:Long?=null,val payload:String?=null,val message:String="Collection paused")
@Entity(tableName="forward_preferences")
data class ForwardPreferences(@PrimaryKey val id:Int=1,val onboardingSeen:Boolean=false,val egxOffHours:Boolean=false)
@Entity(tableName="gold_rules")
data class GoldRule(@PrimaryKey(autoGenerate=true) val id:Long=0,val type:String,val threshold:Double=0.0,val periodMinutes:Long=60,
    val cooldownMinutes:Long=60,val enabled:Boolean=true)
@Entity(tableName="gold_rule_state")
data class GoldRuleState(@PrimaryKey val ruleId:Long,val fingerprint:String,val lastCondition:Boolean,val lastNotifiedAt:Long?=null)
@Entity(tableName="alert_center",indices=[Index(value=["instrumentId","timestamp"]),Index("createdAt")])
data class CenterAlert(@PrimaryKey val id:String,val category:String,val instrumentId:String?,val timestamp:Long,val createdAt:Long,
    val title:String,val body:String,val price:String?,val provider:String?,val read:Boolean=false,val dismissed:Boolean=false)
@Dao
interface ForwardDao {
    @Query("SELECT * FROM gold_config WHERE id=1") suspend fun goldConfig():GoldConfig?
    @Query("SELECT * FROM gold_config WHERE id=1") fun goldConfigFlow():Flow<GoldConfig?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:GoldConfig)
    @Query("SELECT * FROM gold_status WHERE id=1") suspend fun goldStatus():GoldStatus?
    @Query("SELECT * FROM gold_status WHERE id=1") fun goldStatusFlow():Flow<GoldStatus?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:GoldStatus)
    @Query("SELECT * FROM forward_preferences WHERE id=1") suspend fun preferences():ForwardPreferences?
    @Query("SELECT * FROM forward_preferences WHERE id=1") fun preferencesFlow():Flow<ForwardPreferences?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:ForwardPreferences)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:GoldRule):Long
    @Query("SELECT * FROM gold_rules ORDER BY id") suspend fun rules():List<GoldRule>
    @Query("SELECT * FROM gold_rules ORDER BY id") fun rulesFlow():Flow<List<GoldRule>>
    @Delete suspend fun delete(value:GoldRule)
    @Query("SELECT * FROM gold_rule_state WHERE ruleId=:id") suspend fun ruleState(id:Long):GoldRuleState?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(value:GoldRuleState)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun alert(value:CenterAlert):Long
    @Query("SELECT * FROM alert_center WHERE dismissed=0 ORDER BY createdAt DESC LIMIT 500") fun alerts():Flow<List<CenterAlert>>
    @Query("SELECT * FROM alert_center WHERE instrumentId=:id AND timestamp>=:start ORDER BY timestamp DESC LIMIT 100") fun chartEvents(id:String,start:Long):Flow<List<CenterAlert>>
    @Query("UPDATE alert_center SET read=:read WHERE id=:id") suspend fun read(id:String,read:Boolean)
    @Query("UPDATE alert_center SET dismissed=1 WHERE id=:id") suspend fun dismiss(id:String)
    @Query("DELETE FROM alert_center WHERE id NOT IN (SELECT id FROM alert_center ORDER BY createdAt DESC LIMIT 2000)") suspend fun trimAlerts()
}
