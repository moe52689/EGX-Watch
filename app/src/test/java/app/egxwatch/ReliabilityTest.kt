package app.egxwatch

import app.egxwatch.data.*
import app.egxwatch.domain.*
import app.egxwatch.ui.feedProblem
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.io.IOException

class ReliabilityTest {
    private fun instrument(ticker: String) = DirectoryProvider.instruments.single { it.ticker == ticker }
    private fun resource(name: String) = javaClass.getResource("/feeds/$name")!!.readText()
    @Test fun stockRetrievalDoesNotInventAnExchangeTimestamp() {
        val fetched = Instant.parse("2026-09-22T18:00:00Z")
        val quote = FeedParsers.stock(instrument("EGBE"), FeedResponse(resource("tradingview.json"), fetched))
        assertEquals("USD", quote.currency)
        assertEquals(fetched, quote.timestamp)
        assertEquals(TimestampBasis.RETRIEVAL_TIME, quote.timestampBasis)
        assertEquals(DataKind.INDICATIVE, quote.kind)
        validateQuote(instrument("EGBE"), quote, fetched)
        assertThrows(IllegalArgumentException::class.java) {
            FeedParsers.stock(instrument("EGBE").copy(currency = "EGP"), FeedResponse(resource("tradingview.json"), fetched))
        }
    }
    @Test fun etfIssuerNavIsNotItsIndicativeIntradayValue() {
        val quote = FeedParsers.etf(instrument("EGX30ETF"), FeedResponse(resource("etf.html"), Instant.now()))
        assertEquals("63.11", quote.value.toPlainString())
        assertEquals(DataKind.NAV, quote.kind)
        assertEquals(TimestampBasis.VALUATION_DATE, quote.timestampBasis)
        assertEquals(Instant.parse("2026-09-21T21:00:00Z"), quote.timestamp)
        validateQuote(instrument("EGX30ETF"), quote, Instant.parse("2026-09-22T18:00:00Z"))
    }
    @Test fun brokerCodesPreserveExistingFundIds() {
        assertEquals("SNDUK:misr-equity-fund", instrument("CI30").id)
        assertEquals("SNDUK:beltone-b-secure-fund", instrument("BSC").id)
        assertEquals("EG:FUND:AZG", instrument("AZG").id)
        assertEquals("USD", instrument("AZ-25").currency)
    }
    @Test fun explicitCycleRetriesOncePerSourceAndPreservesCache() = runTest {
        var calls = 0
        val client = PublicFeedClient { _, _ -> calls++; if (calls > 1) throw IOException("Offline"); "good" }
        client.get("https://example.test") { }
        client.beginCheck()
        assertNotNull(client.get("https://example.test") { }.warning)
        assertNotNull(client.get("https://example.test") { }.warning)
        assertEquals(2, calls)
        client.finishCheck()
    }
    @Test fun dnsFailureHasActionableCopyWithoutHostnameDump() {
        val message = feedProblem("Unable to resolve host app.azimut.eg: No address associated", true)
        assertTrue(message.contains("Private DNS"))
        assertTrue(message.contains("saved value"))
        assertFalse(message.contains("app.azimut.eg"))
    }
    @Test fun aSlowSourceDoesNotBlockAnotherProvider() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = PublicFeedClient { url, _ -> if (url.endsWith("slow")) { entered.complete(Unit); release.await() }; "valid" }
        val slow = async { client.get("https://example.test/slow") { } }
        entered.await()
        try { assertEquals("valid", withTimeout(5000) { client.get("https://example.test/fast") { } }.body) }
        finally { release.complete(Unit); slow.await() }
    }
    @Test fun publicClientRejectsUnsafeDestinationsBeforeConnecting() = runTest {
        for (url in listOf("http://snduk.com", "https://example.test", "https://user:secret@snduk.com", "https://snduk.com:8443")) {
            try { PublicFeedClient().get(url) { }; fail("Unsafe destination accepted") }
            catch (expected: IOException) { assertTrue(expected.message!!.contains("Unapproved")) }
        }
    }
}
