package app.egxwatch

import app.egxwatch.data.*
import app.egxwatch.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.*

class EngineContractTest {
    @Test fun completeSnapshotAndAnalysisSurviveSerialization() {
        val q=Quote("EGX:T","10.25".toBigDecimal(),"EGP",DataKind.DELAYED,Instant.parse("2026-09-25T08:00:00Z"),"Authorized source",15,
            fields=MarketFields("10".toBigDecimal(),"9.8".toBigDecimal(),"11".toBigDecimal(),"9".toBigDecimal(),1200,"10.2".toBigDecimal(),"10.3".toBigDecimal()))
        assertEquals(q,GatewayProvider.parseQuote(JSONObject(EngineJson.quote(q))))
        val a=StructuredAnalysis("READY",80,.8,mapOf("Trend" to 75),mapOf("RSI14" to 60.0),listOf("Positive trend"),listOf("Risk"))
        assertEquals(a,EngineJson.analysis(EngineJson.analysis(a)))
        val unavailable=StructuredAnalysis("INSUFFICIENT DATA")
        assertEquals(unavailable,EngineJson.analysis(EngineJson.analysis(unavailable)))
    }
    @Test fun candleHistoryRoundTripsWithoutInventingMissingFields() {
        val date=LocalDate.of(2026,9,24)
        val h=PriceHistory("EG:FUND:T","EGP","issuer",DataKind.NAV,true,Instant.parse("2026-09-25T08:00:00Z"),listOf(date),listOf(Candle(date,10.0)))
        val result=EngineJson.history(JSONObject(EngineJson.history(h)))
        assertEquals(h,result);assertNull(result.candles.single().volume);assertNull(result.candles.single().high)
    }
    @Test fun malformedBookAndInventedNavTimestampAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MarketFields(bid="11".toBigDecimal(),ask="10".toBigDecimal()).validate() }
        assertThrows(IllegalArgumentException::class.java) { MarketFields(volume=-1).validate() }
        val i=Instrument("F","F","Fund",InstrumentType.FUND,source="issuer",verifiedAt="2026-09-25")
        val q=Quote("F","10".toBigDecimal(),"EGP",DataKind.NAV,Instant.now(),"issuer",timestampBasis=TimestampBasis.RETRIEVAL_TIME)
        assertThrows(IllegalArgumentException::class.java) { validateQuote(i,q) }
    }
}
