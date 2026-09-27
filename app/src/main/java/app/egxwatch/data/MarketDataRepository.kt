package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.*
import kotlin.math.min
import kotlin.random.Random

class ProviderHttpException(val code:Int,val retryAfterSeconds:Long?=null):IOException("Provider HTTP $code")
class NoAcceptedQuote:IOException("No provider returned a valid current observation; saved value retained")
private class RejectedQuote(message:String):IOException(message)
private class ProviderCooldown(val until:Long):IOException("Provider cooling down")
fun retryAfterSeconds(value:String?,now:Instant=Instant.now()):Long? = value?.let {
    it.toLongOrNull() ?: runCatching { Duration.between(now,java.time.ZonedDateTime.parse(it,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).seconds.coerceAtLeast(0) }.getOrNull()
}
data class ProviderEndpoint(val id:String,val provider:MarketDataProvider)
data class ProviderDiagnostic(val instrumentId:String,val provider:String,val state:String,val at:Long,
    val detail:String,val dataTimestamp:String?=null)
interface ProviderHealthStore {
    suspend fun read(id:String):ProviderHealth?
    suspend fun write(value:ProviderHealth)
}
class RoomHealthStore(private val dao:EngineDao):ProviderHealthStore {
    override suspend fun read(id:String)=dao.health(id)
    override suspend fun write(value:ProviderHealth)=dao.health(value)
}
/** Ordered validation, persisted per-category cooldowns, and one request at a time per endpoint. */
class MarketDataRepository(private val endpoints:List<ProviderEndpoint>,private val health:ProviderHealthStore,
    private val clock:()->Instant={Instant.now()},private val jitter:()->Double={Random.nextDouble()},
    private val timeoutMillis:Long=22000,
    private val diagnostic:suspend(ProviderDiagnostic)->Unit={},
    private val discrepancyPercent:Double=3.0):HistoricalMarketDataProvider {
    private val locks=endpoints.associate { it.id to Mutex() }
    private val chosen=java.util.concurrent.ConcurrentHashMap<String,ProviderEndpoint>()
    private suspend fun <T> attempt(endpoint:ProviderEndpoint,category:String,block:suspend(MarketDataProvider)->T):T = locks.getValue(endpoint.id).withLock {
        val key=healthKey(endpoint.id,category)
        val old=health.read(key) ?: ProviderHealth(key)
        if(old.retryAt>clock().toEpochMilli()) throw ProviderCooldown(old.retryAt)
        try {
            val value=withTimeout(timeoutMillis) { block(endpoint.provider) }
            health.write(old.copy(failures=0,retryAt=0,lastSuccess=clock().toEpochMilli(),status="Connected"))
            value
        } catch(e:CancellationException) {
            if(e !is TimeoutCancellationException) throw e
            fail(old,"Timed out",null);throw IOException("Provider timed out")
        } catch(e:Exception) {
            if(e is RejectedQuote) {
                health.write(old.copy(status=e.message ?: "Rejected observation"))
            } else if(e is ProviderHttpException && e.code in setOf(400,404,422)) {
                health.write(old.copy(status="Instrument/route unavailable (HTTP ${e.code})"))
            } else {
                fail(old,if(e is ProviderHttpException) "HTTP ${e.code}" else "Connection failed or invalid response",(e as? ProviderHttpException)?.retryAfterSeconds)
            }
            throw e
        }
    }
    private suspend fun fail(old:ProviderHealth,status:String,retryAfter:Long?) {
        val failures=(old.failures+1).coerceAtMost(16)
        val backoff=min(3600L,60L*(1L shl min(failures-1,6)))
        val seconds=maxOf((backoff*(1+jitter().coerceIn(0.0,1.0)*.25)).toLong(),retryAfter?.coerceIn(0,(Long.MAX_VALUE-clock().toEpochMilli())/1000) ?: 0)
        health.write(old.copy(failures=failures,retryAt=clock().plusSeconds(seconds).toEpochMilli(),status=status))
    }
    override suspend fun quote(instrument:Instrument)=quote(instrument,null)
    suspend fun quote(instrument:Instrument,notBefore:Instant?):Quote {
        var accepted:Quote?=null
        var selected:ProviderEndpoint?=null
        var warning:String?=null
        val category="quote:${instrument.type.name}"
        for(endpoint in endpoints) {
            suspend fun report(state:String,detail:String,q:Quote?=null) = diagnostic(ProviderDiagnostic(instrument.id,endpoint.id,state,clock().toEpochMilli(),detail,q?.timestamp?.toString()))
            report("CHECKING","Validating current observation")
            try {
                val q=attempt(endpoint,category) { p ->
                    val value=p.quote(instrument)
                    try { validateQuote(instrument,value,clock()) }
                    catch(_:IllegalArgumentException) { throw RejectedQuote("Identity, classification or numeric validation failed") }
                    if(notBefore!=null && value.timestamp<notBefore) throw RejectedQuote("Older than saved observation (${value.timestamp})")
                    if(value.freshness(clock()) !in setOf(Freshness.LIVE,Freshness.DELAYED)) throw RejectedQuote("Stale, indicative or unverified observation (${value.timestamp})")
                    value
                }
                if(accepted==null) {
                    accepted=q;selected=endpoint
                    report("ACCEPTED","First valid source in configured order",q)
                } else {
                    // A valuation-date NAV and a trade quote are never comparable prices.
                    val comparable=q.kind==accepted.kind && q.timestampBasis==accepted.timestampBasis &&
                        (if(q.kind==DataKind.NAV) q.timestamp.atZone(ZoneId.of("Africa/Cairo")).toLocalDate()==accepted.timestamp.atZone(ZoneId.of("Africa/Cairo")).toLocalDate()
                        else kotlin.math.abs(Duration.between(q.timestamp,accepted.timestamp).seconds)<=300)
                    val delta=(q.value-accepted.value).abs().divide(accepted.value,10,java.math.RoundingMode.HALF_UP).toDouble()*100
                    if(comparable && delta>discrepancyPercent) {
                        warning="Provider discrepancy: ${selected!!.id} and ${endpoint.id} differ by ${"%.2f".format(java.util.Locale.US,delta)}%; alerts suppressed"
                        report("DISCREPANCY",warning,q)
                    } else report("CHECKED",if(comparable) "Within ${discrepancyPercent}% comparison tolerance" else "Different series or timestamps; not compared",q)
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) {
                val detail=when(e) {
                    is ProviderCooldown -> "Cooldown until ${Instant.ofEpochMilli(e.until)}"
                    is RejectedQuote -> e.message ?: "Rejected observation"
                    is ProviderHttpException -> "HTTP ${e.code}"+(e.retryAfterSeconds?.let { "; Retry-After ${it}s" } ?: "")
                    else -> "Connection failed, timeout or invalid gateway contract"
                }
                report("REJECTED",detail)
            }
        }
        val result=accepted ?: throw NoAcceptedQuote()
        chosen[instrument.id]=selected!!
        return result.copy(qualityWarning=warning)
    }
    override suspend fun history(instrument:Instrument):PriceHistory {
        val endpoint=chosen[instrument.id] ?: throw IOException("Fetch a matching snapshot before history")
        return attempt(endpoint,"history:${instrument.type.name}") { p -> (p as? HistoricalMarketDataProvider)?.history(instrument) ?: throw ProviderHttpException(404) }
    }
    private suspend fun <T> first(category:String,block:suspend(MarketDataProvider)->T):T {
        for(endpoint in endpoints) {
            try { return attempt(endpoint,category,block) } catch(e:CancellationException) { throw e } catch(_:Exception) { }
        }
        throw IOException("Configured providers unavailable or cooling down")
    }
    override suspend fun search(query:String)=first("directory") { it.search(query) }
    override suspend fun resolve(id:String)=first("directory") { it.resolve(id) }
    override suspend fun marketStatus()=first("status") { it.marketStatus() }
    companion object { fun healthKey(provider:String,category:String)="$provider|$category" }
}
