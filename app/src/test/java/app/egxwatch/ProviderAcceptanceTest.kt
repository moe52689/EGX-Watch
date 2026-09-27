package app.egxwatch

import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProviderAcceptanceTest {
    private val now=Instant.parse("2026-09-27T09:00:00Z")
    private val instrument=Instrument("EGX:T","T","Test equity",InstrumentType.STOCK,source="Mock",verifiedAt="2026-09-27")
    private val quote=Quote(instrument.id,"100".toBigDecimal(),"EGP",DataKind.LIVE,now,"Mock",ticker="T",instrumentType=InstrumentType.STOCK,maxAgeSeconds=1200)
    private fun provider(q:Quote)=MockMarketDataProvider(q)
    @Test fun firstValidatedResponseWinsNotFastestOrLargestPrice()=runTest {
        val first=provider(quote).apply { pause=50 };val second=provider(quote.copy(value="101".toBigDecimal(),source="Secondary"))
        val events=mutableListOf<ProviderDiagnostic>()
        val repository=MarketDataRepository(listOf(ProviderEndpoint("primary",first),ProviderEndpoint("secondary",second)),MemoryHealth(),{now},{0.0},100,diagnostic={events+=it})
        val accepted=repository.quote(instrument)
        assertEquals("Mock",accepted.source);assertEquals("100",accepted.value.toPlainString());assertNull(accepted.qualityWarning)
        assertEquals(listOf("primary","primary","secondary","secondary"),events.map { it.provider })
        assertEquals(listOf("CHECKING","ACCEPTED","CHECKING","CHECKED"),events.map { it.state })
    }
    @Test fun oldSavedFloorAndDeclaredAgeAreRejectedInsideFailover()=runTest {
        for(bad in listOf(quote.copy(timestamp=now.minusSeconds(10)),quote.copy(timestamp=now.minusSeconds(5),maxAgeSeconds=1),quote.copy(ticker="OTHER"),quote.copy(instrumentType=InstrumentType.FUND))) {
            val events=mutableListOf<ProviderDiagnostic>();val fallback=provider(quote.copy(source="Fallback"))
            val repo=MarketDataRepository(listOf(ProviderEndpoint("bad",provider(bad)),ProviderEndpoint("good",fallback)),MemoryHealth(),{now},{0.0},diagnostic={events+=it})
            assertEquals("Fallback",repo.quote(instrument,now.minusSeconds(6)).source)
            assertTrue(events.any { it.provider=="bad" && it.state=="REJECTED" })
        }
    }
    @Test fun materialDisagreementPreservesFirstPriceButSuppressesAnalytics()=runTest {
        val repo=MarketDataRepository(listOf(ProviderEndpoint("first",provider(quote)),ProviderEndpoint("second",provider(quote.copy(value="110".toBigDecimal(),source="Other")))),MemoryHealth(),{now},{0.0})
        val q=repo.quote(instrument);assertEquals(quote.value,q.value);assertNotNull(q.qualityWarning)
        assertEquals(Freshness.UNAVAILABLE,q.freshness(now))
        val analysis=AdaptiveQuantitativeEngine().analyze(instrument,q,emptyList(),emptyList(),SampleQuality(1000,50,1000,1000),now,null)
        assertEquals("DISPUTED DATA",analysis.status);assertNull(analysis.score)
        assertNotEquals(quote.fingerprint(),q.fingerprint())
    }
    @Test fun etfNavAndTradedPriceAreNotCompared()=runTest {
        val etf=instrument.copy(type=InstrumentType.ETF)
        val trade=quote.copy(instrumentType=InstrumentType.ETF)
        val nav=trade.copy(kind=DataKind.NAV,timestampBasis=TimestampBasis.VALUATION_DATE,value="500".toBigDecimal())
        val repo=MarketDataRepository(listOf(ProviderEndpoint("trade",provider(trade)),ProviderEndpoint("nav",provider(nav))),MemoryHealth(),{now},{0.0})
        assertNull(repo.quote(etf).qualityWarning)
    }
    @Test fun strictGatewayContractRejectsMissingOrMismatchedIdentityAndTimestamp() {
        val valid=JSONObject(EngineJson.quote(quote))
        assertEquals(quote,GatewayProvider.parseVerifiedQuote(valid,instrument,now))
        for(field in listOf("ticker","type","timestamp","timestampBasis","maxAgeSeconds")) {
            val json=JSONObject(valid.toString()).apply { remove(field) }
            assertThrows(Exception::class.java) { GatewayProvider.parseVerifiedQuote(json,instrument,now) }
        }
        for(value in listOf("", "WRONG")) assertThrows(Exception::class.java) { GatewayProvider.parseVerifiedQuote(JSONObject(valid.toString()).put("ticker",value),instrument,now) }
        assertThrows(Exception::class.java) { GatewayProvider.parseVerifiedQuote(JSONObject(valid.toString()).put("volume",1.5),instrument,now) }
    }
    @Test fun cooldownIsIsolatedByAssetCategoryIncludingGold()=runTest {
        val health=MemoryHealth();val p=provider(quote).apply { failure=ProviderHttpException(429,7200) }
        val repo=MarketDataRepository(listOf(ProviderEndpoint("shared",p)),health,{now},{0.0})
        try { repo.quote(instrument);fail() } catch(_:NoAcceptedQuote) {}
        p.failure=null
        val gold=quote.copy(instrumentId=GlobalGold.instrument.id,ticker="XAU/USD",instrumentType=InstrumentType.COMMODITY,currency="USD",kind=DataKind.SPOT,timestampBasis=TimestampBasis.PROVIDER_SNAPSHOT)
        p.result=gold
        assertEquals(gold,repo.quote(GlobalGold.instrument))
        p.result=quote
        try { repo.quote(instrument);fail() } catch(_:NoAcceptedQuote) {}
        assertEquals(2,p.calls)
    }
    @Test fun goldFailoverAndEverySourceFailedAreExplicit()=runTest {
        val q=quote.copy(instrumentId=GlobalGold.instrument.id,ticker="XAU/USD",instrumentType=InstrumentType.COMMODITY,currency="USD",kind=DataKind.SPOT,timestampBasis=TimestampBasis.PROVIDER_SNAPSHOT)
        val bad=provider(q).apply { failure=ProviderHttpException(503) };val good=provider(q.copy(source="Gold fallback"))
        val health=MemoryHealth();val repo=MarketDataRepository(listOf(ProviderEndpoint("gold:first",bad),ProviderEndpoint("gold:second",good)),health,{now},{0.0})
        assertEquals("Gold fallback",repo.quote(GlobalGold.instrument).source)
        good.failure=ProviderHttpException(503)
        try { repo.quote(GlobalGold.instrument);fail() } catch(_:NoAcceptedQuote) {}
    }
    @Test fun retryAfterParsesDatesAndRejectsGarbage() {
        assertEquals(3600L,retryAfterSeconds("Sun, 27 Sep 2026 10:00:00 GMT",now))
        assertEquals(120L,retryAfterSeconds("120",now));assertNull(retryAfterSeconds("nonsense",now))
    }
}
