package app.egxwatch
import app.egxwatch.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class ChartRangeTest {
    @Test fun threeDaysEnablesOnlyShortRangesAndAll() {
        val first=Instant.parse("2026-09-20T10:00:00Z").toEpochMilli();val last=first+3*86400000
        assertTrue(LocalChartRange.FIVE_DAYS.available(first,last,20));assertTrue(LocalChartRange.ALL.available(first,last,20))
        assertFalse(LocalChartRange.MONTH.available(first,last,20));assertFalse(LocalChartRange.YEAR.available(first,last,20))
        assertFalse(LocalChartRange.TODAY.available(null,null,0))
    }
}
