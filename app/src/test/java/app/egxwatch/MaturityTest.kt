package app.egxwatch

import app.egxwatch.domain.*
import org.junit.Assert.*
import org.junit.Test

class MaturityTest {
    private fun q(samples:Long,sessions:Int)=SampleQuality(samples,sessions,samples,samples)
    @Test fun maturityDependsOnSessionsNotJustSampleCount() {
        assertEquals(AnalyticsMaturity.INITIALIZING,LocalIndicatorRequirements.maturity(q(1,1)))
        assertEquals(AnalyticsMaturity.INTRADAY,LocalIndicatorRequirements.maturity(q(500,1)))
        assertEquals(AnalyticsMaturity.SHORT_TERM,LocalIndicatorRequirements.maturity(q(50,5)))
        assertEquals(AnalyticsMaturity.DEVELOPING,LocalIndicatorRequirements.maturity(q(200,20)))
        assertEquals(AnalyticsMaturity.ESTABLISHED,LocalIndicatorRequirements.maturity(q(1000,50)))
    }
    @Test fun insufficientAndMissingHistoryNeverUnlocksLongIndicators() {
        val requirement=LocalIndicatorRequirements.requirements.getValue("Observed session SMA50")
        assertEquals(IndicatorState.INSUFFICIENT_DATA,requirement.state(q(20,20)))
        assertEquals(IndicatorState.INSUFFICIENT_DATA,requirement.state(q(500,1)))
        assertEquals(IndicatorState.INSUFFICIENT_DATA,requirement.state(q(500,50).copy(expectedSlots=1000)))
        assertEquals(IndicatorState.STALE_DATA,requirement.state(q(500,50),false))
        assertEquals(IndicatorState.AVAILABLE,requirement.state(q(500,50)))
    }
    @Test fun irregularFrequencyAndMissingSessionsReduceCompleteness() {
        assertEquals(.25,SampleQuality(100,5,50,100,10).completeness,1e-9)
        assertEquals(AnalyticsMaturity.INITIALIZING,LocalIndicatorRequirements.maturity(SampleQuality(100,5,50,100,10)))
    }
}
