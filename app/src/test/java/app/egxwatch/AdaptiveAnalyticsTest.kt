package app.egxwatch

import app.egxwatch.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class AdaptiveAnalyticsTest {
    private val i=Instrument("EGX:T","T","Test",InstrumentType.STOCK,source="mock",verifiedAt="2026-09-25")
    private val now=Instant.parse("2026-09-25T09:00:00Z")
    private val q=Quote(i.id,"100".toBigDecimal(),"EGP",DataKind.LIVE,now,"mock")
    @Test fun noHistoryProducesNoConclusion() {
        val a=AdaptiveQuantitativeEngine().analyze(i,q,emptyList(),listOf(LocalSample(now,100.0)),SampleQuality(1,1,1,1),now,now.toEpochMilli())
        assertNull(a.score);assertEquals("BUILDING HISTORY",a.status);assertTrue(a.building.contains("Observed session SMA50"))
    }
    @Test fun earlyMomentumNeverPretendsToHaveDailyIndicators() {
        val samples=(0..4).map { LocalSample(now.minusSeconds((4L-it)*900),96.0+it) }
        val a=AdaptiveQuantitativeEngine().analyze(i,q,emptyList(),samples,SampleQuality(5,1,5,5),now,now.minusSeconds(3600).toEpochMilli())
        // Intraday uses the current collected session even without completed session bars.
        assertTrue(a.indicators.containsKey("Intraday momentum"));assertFalse(a.indicators.containsKey("Observed session SMA50"));assertTrue(a.confidence<.7)
    }
    @Test fun twentySessionsDoNotProduceFiftySessionAverage() {
        val bars=(0..19).map { Candle(LocalDate.of(2026,8,1).plusDays(it.toLong()),90.0+it) }
        val a=AdaptiveQuantitativeEngine().analyze(i,q,bars,emptyList(),SampleQuality(200,20,200,200),now,null)
        assertTrue(a.indicators.containsKey("Observed session SMA20"));assertFalse(a.indicators.containsKey("Observed session SMA50"));assertFalse(a.indicators.containsKey("Observed session MACD"))
        assertEquals(99.5,a.indicators.getValue("Observed session SMA20"),1e-9)
    }
    @Test fun discontinuitySuppressesOpportunity() {
        val a=AdaptiveQuantitativeEngine().analyze(i,q,emptyList(),listOf(LocalSample(now.minusSeconds(900),10.0),LocalSample(now,100.0)),SampleQuality(100,50,100,100),now,null)
        assertNull(a.score);assertEquals("UNRELIABLE DATA",a.status)
    }
    @Test fun staleSnapshotCannotBecomeOpportunity() {
        val a=AdaptiveQuantitativeEngine().analyze(i,q.copy(timestamp=now.minusSeconds(86400)),emptyList(),emptyList(),SampleQuality(500,50,500,500),now,null)
        assertNull(a.score);assertEquals("STALE DATA",a.status)
    }
}
