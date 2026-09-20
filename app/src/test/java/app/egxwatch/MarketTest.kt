package app.egxwatch

import app.egxwatch.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class MarketTest {
    private fun n(value: String) = value.toBigDecimal()
    private fun at(day: String, time: String) = LocalDateTime.parse("${day}T$time").atZone(ZoneId.of("Africa/Cairo")).toInstant()
    @Test fun minimumInterval() { assertThrows(IllegalArgumentException::class.java) { MonitorPolicy(intervalMinutes = 14) }; MonitorPolicy(intervalMinutes = 15) }
    @Test fun intervals() { listOf(15L, 30, 60, 120, 137).forEach { assertEquals(it, MonitorPolicy(intervalMinutes = it).intervalMinutes) } }
    @Test fun emptyDaysRejected() { assertThrows(IllegalArgumentException::class.java) { MonitorPolicy(days = emptySet()) } }
    @Test fun zeroThresholdRejected() { assertThrows(IllegalArgumentException::class.java) { MonitorPolicy(absoluteThreshold = BigDecimal.ZERO) } }
    @Test fun defaultDaysAreSundayThroughThursday() { val p = MonitorPolicy(); assertTrue(p.isActive(at("2026-09-20", "12:00"))); assertFalse(p.isActive(at("2026-09-18", "12:00"))) }
    @Test fun windowStartInclusiveEndExclusive() {
        val p = MonitorPolicy(start = LocalTime.of(10, 0), end = LocalTime.of(14, 30))
        assertTrue(p.isActive(at("2026-09-20", "10:00"))); assertFalse(p.isActive(at("2026-09-20", "14:30")))
    }
    @Test fun overnightBelongsToPreviousDay() {
        val p = MonitorPolicy(days = setOf(DayOfWeek.THURSDAY), start = LocalTime.of(22, 0), end = LocalTime.of(2, 0))
        assertTrue(p.isActive(at("2026-09-17", "23:00"))); assertTrue(p.isActive(at("2026-09-18", "01:00")))
        assertFalse(p.isActive(at("2026-09-18", "02:00"))); assertFalse(p.isActive(at("2026-09-18", "23:00")))
    }
    @Test fun equalTimesMeanWholeSelectedDay() { val p = MonitorPolicy(start = LocalTime.NOON, end = LocalTime.NOON); assertTrue(p.isActive(at("2026-09-20", "01:00"))) }
    @Test fun cairoUsesDaylightSavingZone() { assertEquals(3 * 3600, at("2026-09-20", "12:00").atZone(ZoneId.of("Africa/Cairo")).offset.totalSeconds) }
    @Test fun baselineDoesNotAlert() { assertFalse(shouldNotify(n("5"), null, MonitorPolicy())) }
    @Test fun decimalScaleDoesNotCauseFalseChange() { assertFalse(shouldNotify(n("5.00"), n("5.0"), MonitorPolicy())) }
    @Test fun detectsIncreaseAndDecrease() { assertTrue(shouldNotify(n("6"), n("5"), MonitorPolicy())); assertTrue(shouldNotify(n("4"), n("5"), MonitorPolicy())) }
    @Test fun everyCheckIncludesBaselineAndUnchanged() { val p = MonitorPolicy(everyCheck = true); assertTrue(shouldNotify(n("5"), null, p)); assertTrue(shouldNotify(n("5"), n("5"), p)) }
    @Test fun eitherThresholdCanTrigger() {
        val p = MonitorPolicy(absoluteThreshold = n("10"), percentThreshold = n("5"))
        assertTrue(shouldNotify(n("10.5"), n("10"), p)); assertTrue(shouldNotify(n("990"), n("1000"), p)); assertFalse(shouldNotify(n("1001"), n("1000"), p))
    }
    @Test fun percentageUsesAbsolutePrevious() { assertEquals(0, change(n("90"), n("100")).percent!!.compareTo(n("-10"))) }
    @Test fun zeroPreviousHasNoPercentage() { assertNull(change(n("1"), n("0")).percent); assertFalse(shouldNotify(n("1"), n("0"), MonitorPolicy(percentThreshold = n("1")))) }
    @Test fun exactThresholdIsInclusive() { assertTrue(shouldNotify(n("9"), n("10"), MonitorPolicy(absoluteThreshold = n("1")))) }
    private val instrument = Instrument("EGX:TEST", "TEST", "Test security", InstrumentType.STOCK, source = "test", verifiedAt = "2026-01-01")
    private val now = Instant.parse("2026-09-20T10:00:00Z")
    private fun quote() = Quote(instrument.id, n("1.25"), "EGP", DataKind.DELAYED, now, "test", 15)
    @Test fun quoteClassificationAndIdentity() {
        validateQuote(instrument, quote(), now)
        listOf(quote().copy(instrumentId = "other"), quote().copy(currency = "USD"), quote().copy(kind = DataKind.NAV),
            quote().copy(delayMinutes = null), quote().copy(value = n("-1")), quote().copy(timestamp = now.plusSeconds(301))).forEach {
            assertThrows(IllegalArgumentException::class.java) { validateQuote(instrument, it, now) }
        }
    }
    @Test fun navCannotMasqueradeAsLive() { assertThrows(IllegalArgumentException::class.java) { validateQuote(instrument.copy(type = InstrumentType.FUND), quote().copy(kind = DataKind.LIVE), now) } }
    @Test fun liveRequiresExchangeTimestamp() { assertThrows(IllegalArgumentException::class.java) { validateQuote(instrument, quote().copy(kind = DataKind.LIVE, timestampBasis = TimestampBasis.PROVIDER_SNAPSHOT), now) } }
}
