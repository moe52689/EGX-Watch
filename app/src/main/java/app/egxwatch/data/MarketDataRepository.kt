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
data class ProviderEndpoint(val id:String,val provider:MarketDataProvider)
interface ProviderHealthStore {
    suspend fun read(id:String):ProviderHealth?
    suspend fun write(value:ProviderHealth)
}
class RoomHealthStore(private val dao:EngineDao):ProviderHealthStore {
    override suspend fun read(id:String)=dao.health(id)
    override suspend fun write(value:ProviderHealth)=dao.health(value)
}
/** Persistent cooldown shared by all securities on a provider, and no concurrent retries. */
class MarketDataRepository(private val endpoints:List<ProviderEndpoint>,private val health:ProviderHealthStore,
    private val clock:()->Instant={Instant.now()}, private val jitter:()->Double={Random.nextDouble()},
    private val timeoutMillis:Long=22000):HistoricalMarketDataProvider {
    private val locks=endpoints.associate { it.id to Mutex() }
    private val chosen=java.util.concurrent.ConcurrentHashMap<String,ProviderEndpoint>()
    private suspend fun <T> attempt(endpoint:ProviderEndpoint,block:suspend(MarketDataProvider)->T):T = locks.getValue(endpoint.id).withLock {
        val now=clock();val old=health.read(endpoint.id) ?: ProviderHealth(endpoint.id)
        if (old.retryAt>now.toEpochMilli()) throw IOException("Provider cooling down until ${Instant.ofEpochMilli(old.retryAt)}")
        try {
            val value=withTimeout(timeoutMillis) { block(endpoint.provider) }
            health.write(old.copy(failures=0,retryAt=0,lastSuccess=clock().toEpochMilli(),status="Connected"));value
        } catch(e:CancellationException) {
            if(e !is TimeoutCancellationException) throw e
            fail(endpoint,old,"Timed out",null);throw IOException("Provider timed out")
        } catch(e:Exception) {
            // A missing security/history route is not a provider-wide outage.
            if(e is ProviderHttpException && e.code in setOf(400,404,422)) throw e
            fail(endpoint,old,if(e is ProviderHttpException) "HTTP ${e.code}" else "Unavailable or invalid data",(e as? ProviderHttpException)?.retryAfterSeconds)
            throw e
        }
    }
    private suspend fun fail(endpoint:ProviderEndpoint,old:ProviderHealth,status:String,retryAfter:Long?) {
        val failures=(old.failures+1).coerceAtMost(16)
        val backoff=min(3600L,60L*(1L shl min(failures-1,6)))
        val seconds=maxOf((backoff*(1+jitter().coerceIn(0.0,1.0)*.25)).toLong(),retryAfter?.coerceIn(0,604800) ?: 0)
        health.write(old.copy(failures=failures,retryAt=clock().plusSeconds(seconds).toEpochMilli(),status=status))
    }
    override suspend fun quote(instrument:Instrument):Quote {
        var saved:Quote?=null; var savedEndpoint:ProviderEndpoint?=null
        for(endpoint in endpoints) {
            try {
                val q=attempt(endpoint) { p -> p.quote(instrument).also { validateQuote(instrument,it,clock()) } }
                if(q.freshness(clock()) in setOf(Freshness.LIVE,Freshness.DELAYED)) { chosen[instrument.id]=endpoint;return q }
                if(saved==null || q.timestamp>saved.timestamp) { saved=q;savedEndpoint=endpoint }
                health.read(endpoint.id)?.let { health.write(it.copy(status="Stale or unverified data")) }
            } catch(e:CancellationException) { throw e } catch(_:Exception) { /* Continue ordered fallback. Health contains sanitized reason. */ }
        }
        if(saved!=null) { chosen[instrument.id]=savedEndpoint!!;return saved.copy(notice="No fresh source available; latest provider observation") }
        throw IOException("All configured providers unavailable or cooling down; last saved value retained")
    }
    override suspend fun history(instrument:Instrument):PriceHistory {
        val endpoint=chosen[instrument.id] ?: throw IOException("Fetch a matching snapshot before history")
        return attempt(endpoint) { p -> (p as? HistoricalMarketDataProvider)?.history(instrument)
            ?: throw ProviderHttpException(404) }
    }
    private suspend fun <T> first(block:suspend(MarketDataProvider)->T):T {
        for(endpoint in endpoints) {
            try { return attempt(endpoint,block) } catch(e:CancellationException) { throw e } catch(_:Exception) { }
        }
        throw IOException("Configured providers unavailable or cooling down")
    }
    override suspend fun search(query:String)=first { it.search(query) }
    override suspend fun resolve(id:String)=first { it.resolve(id) }
    override suspend fun marketStatus()=first { it.marketStatus() }
}
