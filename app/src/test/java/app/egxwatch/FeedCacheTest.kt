package app.egxwatch

import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException

class FeedCacheTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun validatedFeedSurvivesRestartAndFailedRefresh() = runTest {
        val first = PublicFeedClient(folder.root) { _, _ -> "good" }
        val original = first.get("https://example.test") { require(it == "good") }
        val restarted = PublicFeedClient(folder.root) { _, _ -> throw IOException("Offline") }
        val fallback = restarted.get("https://example.test", force = true) { require(it == "good") }
        assertEquals(original.body, fallback.body)
        assertEquals(original.fetchedAt, fallback.fetchedAt)
        assertTrue(fallback.warning!!.contains("Offline"))
    }
    @Test fun malformedResponsesCannotReplaceSavedFeed() = runTest {
        var body = "good"
        val client = PublicFeedClient(folder.root) { _, _ -> body }
        client.get("https://example.test") { require(it == "good") }
        body = "broken"
        val fallback = client.get("https://example.test", force = true) { require(it == "good") }
        assertEquals("good", fallback.body)
        assertNotNull(fallback.warning)
        assertNotNull(client.get("https://example.test") { require(it == "good") }.warning)
        assertFalse(folder.root.listFiles()!!.single().readText().contains("broken"))
    }
    @Test fun directoryContainsBroadCoverageAndVerifiedCurrencies() {
        val all = DirectoryProvider.instruments
        assertTrue(all.count { it.type == InstrumentType.STOCK } >= 296)
        assertTrue(all.count { it.type == InstrumentType.FUND } >= 153)
        assertEquals("USD", all.single { it.ticker == "EGBE" }.currency)
        assertEquals("EGP", all.single { it.ticker == "COMI" }.currency)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertFalse(all.any { it.ticker == "4170" })
    }
    @Test fun arbitraryFundRowsUseProviderIdentityAndCurrency() {
        val html = """<table><tr><td><a href="/eg/funds/example-fund?lang=en">Example fund</a></td><td>Money market</td><td>Sep 20, 2026</td><td>USD 10.25</td></tr></table>"""
        val instrument = InstrumentCatalog.funds(html).single()
        assertEquals("SNDUK:example-fund", instrument.id)
        val quote = FreePublicProvider.parseSnduk(instrument, html)
        assertEquals("USD", quote.currency)
        assertEquals("10.25", quote.value.toPlainString())
        assertEquals(TimestampBasis.VALUATION_DATE, quote.timestampBasis)
    }
    @Test fun connectionTestActuallyFetchesEachSource() = runTest {
        val visited = mutableListOf<String>()
        val provider = FreePublicProvider(PublicFeedClient { url, _ -> visited += url; throw IOException("Test unavailable") })
        val report = provider.health()
        assertEquals(4, visited.size)
        assertEquals(4, Regex("FAILED").findAll(report).count())
        assertFalse(report.contains("Connected"))
    }
}
