package app.egxwatch

import app.egxwatch.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AnalyticsTest {
    private val now=Instant.parse("2026-09-24T09:00:00Z")
    private val instrument=Instrument("EGX:TEST","TEST","Test",InstrumentType.STOCK,source="mock",verifiedAt="2026-09-01")
    private val quote=Quote(instrument.id,"110".toBigDecimal(),"EGP",DataKind.LIVE,now,"mock")
    private fun history(): PriceHistory {
        val bars=(0..99).map { Candle(LocalDate.of(2026,5,1).plusDays(it.toLong()),100+it*.1,100+it*.1,101+it*.1,99+it*.1,1000) }
        val shift=java.time.temporal.ChronoUnit.DAYS.between(bars.last().date,LocalDate.of(2026,9,23))
        val shifted=bars.map { it.copy(date=it.date.plusDays(shift)) }
        return PriceHistory(instrument.id,"EGP","mock",DataKind.LIVE,true,now,shifted.map { it.date },shifted)
    }
    @Test fun indicatorReferenceValues() {
        assertEquals(4.0,IndicatorEngine.sma(listOf(1.0,2.0,3.0,4.0,5.0),3)!!,1e-9)
        assertEquals(listOf(2.0,3.0,4.0),IndicatorEngine.emaSeries(listOf(1.0,2.0,3.0,4.0,5.0),3))
        assertEquals(50.0,IndicatorEngine.rsi(List(40){10.0})!!,1e-9)
        assertEquals(100.0,IndicatorEngine.rsi((1..40).map(Int::toDouble))!!,1e-9)
        assertEquals(0.0,IndicatorEngine.rsi((40 downTo 1).map(Int::toDouble))!!,1e-9)
        val macd=IndicatorEngine.macd(List(80){10.0})!!; assertEquals(0.0,macd.first,1e-9);assertEquals(0.0,macd.second,1e-9)
        assertEquals(.5,IndicatorEngine.drawdown(listOf(100.0,120.0,60.0,90.0)),1e-9)
        assertEquals(2.0,IndicatorEngine.atr(List(30){Candle(LocalDate.of(2026,1,1).plusDays(it.toLong()),10.0,10.0,11.0,9.0)})!!,1e-9)
    }
    @Test fun calculationsReproducibleAndExplainable() {
        val engine=QuantitativeEngine();val a=engine.analyze(instrument,quote,history(),now)
        assertEquals(a,engine.analyze(instrument,quote,history(),now));assertEquals("READY",a.status)
        assertTrue(a.score!! in 0..100); assertTrue(a.components.containsKey("Risk resilience"));assertFalse(a.components.containsKey("Valuation"))
        assertTrue(StructuredExplanationEngine().explain(a).contains("not the probability"))
    }
    @Test fun missingAndBadHistoryFailClosed() {
        val h=history(); val engine=QuantitativeEngine()
        listOf(h.copy(candles=h.candles.take(20)),h.copy(candles=h.candles.drop(1)),h.copy(comparable=false),h.copy(currency="USD"),
            h.copy(candles=h.candles.dropLast(1)+h.candles.last().copy(close=Double.NaN)),h.copy(candles=h.candles.reversed())).forEach {
            assertNull(engine.analyze(instrument,quote,it,now).score)
        }
        assertNull(engine.analyze(instrument,quote.copy(value="1000".toBigDecimal()),h,now).score)
    }
    @Test fun staleAndIndicativeCannotTriggerAnalysis() {
        assertEquals(Freshness.STALE,quote.copy(timestamp=now.minusSeconds(3600)).freshness(now))
        assertEquals(Freshness.UNAVAILABLE,quote.copy(kind=DataKind.INDICATIVE).freshness(now))
        assertNull(QuantitativeEngine().analyze(instrument,quote.copy(notice="cached"),history(),now).score)
        assertEquals(Freshness.DELAYED,quote.copy(kind=DataKind.DELAYED,delayMinutes=15,timestamp=now.minusSeconds(900)).freshness(now))
    }
    @Test fun fingerprintsNormalizeDecimalScaleAndIncludeMarketFields() {
        assertEquals(quote.fingerprint(),quote.copy(value="110.00".toBigDecimal()).fingerprint())
        assertNotEquals(quote.fingerprint(),quote.copy(fields=MarketFields(volume=1)).fingerprint())
        assertNotEquals(quote.fingerprint(),quote.copy(timestamp=now.plusSeconds(1)).fingerprint())
    }
    @Test fun alertThresholdDedupCooldownMaterialAndDailyLimit() {
        val rules=OpportunityRules(enabled=true);val a=StructuredAnalysis("READY",80,.8)
        assertTrue(shouldNotifyOpportunity(a,"a",null,0,now,rules))
        assertFalse(shouldNotifyOpportunity(a.copy(confidence=.69),"a",null,0,now,rules))
        assertFalse(shouldNotifyOpportunity(a.copy(score=74),"a",null,0,now,rules))
        val last=LastOpportunity("a",80,OpportunityState.OPPORTUNITY,now)
        assertFalse(shouldNotifyOpportunity(a,"a",last,0,now.plusSeconds(3600),rules))
        assertFalse(shouldNotifyOpportunity(a.copy(score=95),"b",last,0,now.plusSeconds(3599),rules))
        assertTrue(shouldNotifyOpportunity(a.copy(score=95),"b",last,0,now.plusSeconds(3600),rules))
        assertFalse(shouldNotifyOpportunity(a.copy(score=95),"b",last,10,now.plusSeconds(3600),rules))
        assertFalse(shouldNotifyOpportunity(a.copy(score=81),"b",last,0,now.plusSeconds(3600),rules))
        assertTrue(shouldNotifyOpportunity(a,"b",last,0,now.plusSeconds(3600),rules,OpportunityState.NORMAL))
        assertTrue(rules.quiet(Instant.parse("2026-09-24T21:00:00Z")))
    }
    @Test fun sessionWeekendsHolidaysExceptionsAndResume() {
        val p=MonitorPolicy(start=LocalTime.of(10,0),end=LocalTime.of(14,30))
        val cal=SessionCalendar.parse(p,"2026-09-24","2026-09-27 11:00-12:00")
        assertFalse(cal.isOpen(now));assertFalse(cal.isOpen(Instant.parse("2026-09-25T09:00:00Z")))
        assertEquals(Instant.parse("2026-09-27T08:00:00Z"),cal.nextOpen(now))
        assertTrue(cal.isOpen(Instant.parse("2026-09-27T08:00:00Z")));assertFalse(cal.isOpen(Instant.parse("2026-09-27T09:00:00Z")))
    }
}
