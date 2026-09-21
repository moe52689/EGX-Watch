package app.egxwatch

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in explicitly: upstream uptime must not make ordinary builds flaky. */
@RunWith(AndroidJUnit4::class)
class LiveFeedTest {
    @Test fun realPublicFeedsResolveAndReturnPublishedValues() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveFeeds") == "true")
        val provider = FreePublicProvider()
        val health = provider.health()
        Log.i("EGXFeedAudit", health)
        assertFalse(health, health.contains("FAILED"))
        val catalogue = provider.refreshDirectory()
        Log.i("EGXFeedAudit", catalogue)
        assertFalse(catalogue, catalogue.contains("using saved identities"))
        for (ticker in listOf("CCAP", "BINV", "COMI", "EGBE", "T70", "CTQ", "AZG", "BFA")) {
            val instrument = provider.search(ticker).single { it.ticker == ticker }
            val quote = provider.quote(instrument)
            validateQuote(instrument, quote)
            assertNull(quote.notice)
            Log.i("EGXFeedAudit", "$ticker ${quote.value} ${quote.currency} ${quote.kind} ${quote.timestamp} ${quote.source}")
        }
        val extra = provider.search("").first { it.id.startsWith("SNDUK:") }
        validateQuote(extra, provider.quote(extra))
    }
}
