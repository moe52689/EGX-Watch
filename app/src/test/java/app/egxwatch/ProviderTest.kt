package app.egxwatch

import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProviderTest {
    private fun resource(name: String) = javaClass.getResource("/feeds/$name")!!.readText()
    private fun instrument(ticker: String) = DirectoryProvider.instruments.single { it.ticker == ticker }
    @Test fun directoryIncludesSixRequestedAndAnEtf() = runTest {
        val directory = DirectoryProvider()
        listOf("CCAP", "BINV", "T70", "CTQ", "AZG", "BFA", "EGX30ETF").forEach {
            assertEquals(it, directory.search(it).single().ticker)
        }
        assertEquals(InstrumentType.ETF, directory.resolve("EGX:EGX30ETF").type)
        assertTrue(directory.search("NOT-A-REAL-TICKER").isEmpty())
    }
    @Test fun httpsOnlyWithoutEmbeddedSecrets() {
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com?key=secret", "https://example.com/#secret").forEach {
            assertThrows(IllegalArgumentException::class.java) { GatewayProvider.validateBaseUrl(it) }
        }
        assertEquals("https://example.com/v1/", GatewayProvider.validateBaseUrl("https://example.com/v1"))
    }
    @Test fun gatewayAcceptsArbitraryValidatedInstruments() {
        val json = JSONObject("""{"id":"EGX:OTHER","ticker":"OTHER","name":"Another listed instrument","type":"ETF","currency":"EGP","source":"Test provider","verifiedAt":"2026-09-20","validated":true}""")
        assertEquals("OTHER", GatewayProvider.parseInstrument(json).ticker)
        json.put("validated", false)
        assertThrows(IllegalArgumentException::class.java) { GatewayProvider.parseInstrument(json) }
    }
    @Test fun realAzimutSnapshotPreservesValuationDate() {
        val q = FreePublicProvider.parseAzimut(instrument("AZG"), resource("azimut.json"))
        assertEquals("24.1079", q.value.toPlainString())
        assertEquals(DataKind.NAV, q.kind); assertEquals(TimestampBasis.VALUATION_DATE, q.timestampBasis)
        assertEquals(Instant.parse("2026-09-16T21:00:00Z"), q.timestamp)
    }
    @Test fun realSndukRowsAreMappedByIdentity() {
        mapOf("T70" to "1.7127", "CTQ" to "12.5476", "BFA" to "0.7739").forEach { (ticker, expected) ->
            val q = FreePublicProvider.parseSnduk(instrument(ticker), resource("snduk.html"))
            assertEquals(expected, q.value.toPlainString()); assertEquals(DataKind.NAV, q.kind)
        }
    }
    @Test fun htmlDriftAndCurrencyChangesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { FreePublicProvider.parseSnduk(instrument("T70"), "<html>Unavailable</html>") }
        assertThrows(IllegalArgumentException::class.java) { FreePublicProvider.parseSnduk(instrument("T70"), resource("snduk.html").replace("EGP", "USD")) }
    }
    @Test fun publicStocksNeverClaimLiveOrExchangeTime() {
        val q = FreePublicProvider.parsePublicStock(instrument("CCAP"), resource("stocks.json"))
        assertEquals(DataKind.INDICATIVE, q.kind); assertEquals(TimestampBasis.PROVIDER_SNAPSHOT, q.timestampBasis)
        assertEquals("6.89", q.value.toPlainString())
    }
    @Test fun incorrectStockIdentityRejected() {
        assertThrows(IllegalArgumentException::class.java) { FreePublicProvider.parsePublicStock(instrument("CCAP"), resource("stocks.json").replace("Qalaa", "Different issuer")) }
    }
}
