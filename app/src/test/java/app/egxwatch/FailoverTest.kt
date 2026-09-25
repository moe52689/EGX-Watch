package app.egxwatch

import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.io.IOException

/** Synthetic values are confined to test sources, never the APK. */
class MockMarketDataProvider(var result:Quote, var failure:Exception?=null, var pause:Long=0):MarketDataProvider {
    var calls=0
    override suspend fun quote(instrument:Instrument):Quote { calls++;delay(pause);failure?.let { throw it };return result }
    override suspend fun search(query:String)=emptyList<Instrument>()
    override suspend fun resolve(id:String)=error("No test directory")
    override suspend fun marketStatus()=MarketStatus("UNKNOWN","Test",null)
}
class MemoryHealth:ProviderHealthStore {
    val rows=mutableMapOf<String,ProviderHealth>()
    override suspend fun read(id:String)=rows[id]
    override suspend fun write(value:ProviderHealth) { rows[value.provider]=value }
}
class FailoverTest {
    private val now=Instant.parse("2026-09-24T09:00:00Z")
    private val i=Instrument("EGX:T","T","Test",InstrumentType.STOCK,source="test",verifiedAt="2026-01-01")
    private val q=Quote(i.id,"100".toBigDecimal(),"EGP",DataKind.LIVE,now,"mock")
    @Test fun offlineTimeoutMalformedAndStaleUseFallback()=runTest {
        for(mode in 0..3) {
            val primary=MockMarketDataProvider(q)
            when(mode) { 0->primary.failure=IOException("offline");1->primary.pause=1000;2->primary.result=q.copy(instrumentId="wrong");3->primary.result=q.copy(timestamp=now.minusSeconds(7200)) }
            val secondary=MockMarketDataProvider(q.copy(source="second"))
            val repo=MarketDataRepository(listOf(ProviderEndpoint("a",primary),ProviderEndpoint("b",secondary)),MemoryHealth(),{now},{0.0},100)
            assertEquals("second",repo.quote(i).source);assertEquals(1,secondary.calls)
        }
    }
    @Test fun rateLimitPersistsAcrossRepositoryRestartAndHonorsRetryAfter()=runTest {
        var clock=now;val health=MemoryHealth();val p=MockMarketDataProvider(q,ProviderHttpException(429,7200))
        fun repo()=MarketDataRepository(listOf(ProviderEndpoint("a",p)),health,{clock},{0.0})
        try { repo().quote(i) } catch(_:IOException) {}
        assertEquals(now.plusSeconds(7200).toEpochMilli(),health.rows["a"]!!.retryAt)
        try { repo().quote(i) } catch(_:IOException) {}
        assertEquals(1,p.calls)
        clock=now.plusSeconds(7201);p.failure=null;p.result=q.copy(timestamp=clock)
        assertEquals(p.result,repo().quote(i));assertEquals(2,p.calls)
    }
    @Test fun staleFallbackKeepsOriginalTimestampAndSuppressesAlerts()=runTest {
        val stale=q.copy(timestamp=now.minusSeconds(7200))
        val repo=MarketDataRepository(listOf(ProviderEndpoint("a",MockMarketDataProvider(stale))),MemoryHealth(),{now},{0.0})
        val result=repo.quote(i);assertEquals(stale.timestamp,result.timestamp);assertNotNull(result.notice)
    }
    @Test fun providerErrorsDoNotLeakCredentialsInHealth()=runTest {
        val health=MemoryHealth();val repo=MarketDataRepository(listOf(ProviderEndpoint("a",MockMarketDataProvider(q,IOException("secret-token")))),health,{now},{0.0})
        try { repo.quote(i) } catch(_:IOException) {}
        assertFalse(health.rows.values.toString().contains("secret-token"))
    }
}
