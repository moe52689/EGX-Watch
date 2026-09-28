package app.egxwatch

import app.egxwatch.domain.*
import app.egxwatch.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FeedAvailabilityTest {
    private val now=Instant.parse("2026-09-28T09:00:00Z")
    private val trade=Quote("EGX:COMI","100".toBigDecimal(),"EGP",DataKind.LIVE,now,"Test only")
    @Test fun noSourceNeverClaimsActiveOrPromisesAQuote() {
        assertEquals("DATA UNAVAILABLE",egxMonitoringLabel(true,false,true))
        assertEquals("UNAVAILABLE · no verified source",observationLabel(null,false,false,now))
        assertTrue(missingFeedMessage("T70",true).contains("NAV unavailable"))
    }
    @Test fun aSavedLiveObservationDoesNotImplyAWorkingConnection() {
        assertEquals("UNAVAILABLE · last saved value retained",observationLabel(trade,false,true,now))
        assertEquals("LIVE",observationLabel(trade,true,true,now))
        assertEquals("STALE",observationLabel(trade,true,true,now.plusSeconds(1201)))
    }
    @Test fun navUsesValuationLanguageAndRejectsFutureOrExpiredValues() {
        val nav=trade.copy(kind=DataKind.NAV,timestampBasis=TimestampBasis.VALUATION_DATE)
        assertEquals("Published NAV · valuation date below",observationLabel(nav,true,true,now))
        assertEquals("STALE · published NAV",observationLabel(nav,true,true,now.plusSeconds(4*86400L+1)))
        assertTrue(observationLabel(nav.copy(timestamp=now.plusSeconds(3600)),true,true,now).startsWith("UNAVAILABLE"))
    }
    @Test fun delayedQuotesRespectSourceAgeAndLegacyValuesStayUnverified() {
        assertEquals("DELAYED",observationLabel(trade.copy(kind=DataKind.DELAYED,delayMinutes=15,timestamp=now.minusSeconds(900)),true,true,now))
        assertEquals("STALE",observationLabel(trade.copy(maxAgeSeconds=10),true,true,now.plusSeconds(11)))
        assertEquals("Saved value · freshness unverified",observationLabel(null,true,true,now))
    }
}
