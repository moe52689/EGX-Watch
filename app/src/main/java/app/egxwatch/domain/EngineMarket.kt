package app.egxwatch.domain

import java.time.*
import java.math.BigDecimal
import java.security.MessageDigest

/** Exchange fields are optional, never inferred from NAV or polling observations. */
data class MarketFields(val open: BigDecimal? = null, val previousClose: BigDecimal? = null,
    val high: BigDecimal? = null, val low: BigDecimal? = null, val volume: Long? = null,
    val bid: BigDecimal? = null, val ask: BigDecimal? = null) {
    fun validate() {
        listOfNotNull(open, previousClose, high, low, bid, ask).forEach { require(it.signum() >= 0 && it.precision() <= 24 && it.scale() in 0..12) }
        require(volume == null || volume >= 0)
        require(high == null || low == null || high >= low)
        require(bid == null || ask == null || bid <= ask)
        if (high != null && low != null) require(open == null || open in low..high)
    }
}
enum class Freshness { LIVE, DELAYED, STALE, UNAVAILABLE }
fun Quote.freshness(now: Instant, maxAgeMinutes: Long = 20): Freshness {
    if (timestamp > now.plusSeconds(60) || value.signum() <= 0 || kind == DataKind.INDICATIVE) return Freshness.UNAVAILABLE
    if (notice != null) return Freshness.STALE
    val allowance = if (kind == DataKind.NAV) 4 * 24 * 60L else maxAgeMinutes + (delayMinutes ?: 0)
    if (Duration.between(timestamp, now).toMinutes() > allowance) return Freshness.STALE
    return if (kind in setOf(DataKind.LIVE, DataKind.SPOT)) Freshness.LIVE else Freshness.DELAYED
}
fun Quote.fingerprint(): String {
    val parts = listOf(instrumentId, currency, kind.name, timestamp.toString(), source, timestampBasis.name,
        value.display(), delayMinutes?.toString(), fields.open?.display(), fields.previousClose?.display(),
        fields.high?.display(), fields.low?.display(), fields.volume?.toString(), fields.bid?.display(), fields.ask?.display())
    return MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }
}

data class SessionWindow(val start: LocalTime, val end: LocalTime)
data class SessionCalendar(val policy: MonitorPolicy, val holidays: Set<LocalDate> = emptySet(),
    val exceptions: Map<LocalDate, SessionWindow> = emptyMap()) {
    private val zone = ZoneId.of("Africa/Cairo")
    private fun window(date: LocalDate): SessionWindow? = when {
        date in holidays -> null
        date in exceptions -> exceptions[date]
        date.dayOfWeek in policy.days -> SessionWindow(policy.start, policy.end)
        else -> null
    }
    private fun bounds(date: LocalDate): Pair<Instant, Instant>? = window(date)?.let {
        val start = if (it.start == it.end) LocalTime.MIDNIGHT else it.start
        val endDate = if (it.end <= it.start) date.plusDays(1) else date
        date.atTime(start).atZone(zone).toInstant() to endDate.atTime(if (it.start == it.end) LocalTime.MIDNIGHT else it.end).atZone(zone).toInstant()
    }
    fun isOpen(now: Instant): Boolean {
        val date = now.atZone(zone).toLocalDate()
        if (date in holidays) return false
        return listOf(date.minusDays(1), date).mapNotNull(::bounds).any { now >= it.first && now < it.second }
    }
    fun nextOpen(now: Instant): Instant? {
        if (isOpen(now)) return now
        val date = now.atZone(zone).toLocalDate()
        return (0L..370L).mapNotNull { bounds(date.plusDays(it))?.first }.firstOrNull { it > now }
    }
    companion object {
        fun parse(policy: MonitorPolicy, holidays: String, exceptions: String): SessionCalendar {
            val dates = holidays.split(',', '\n').filter { it.isNotBlank() }.map { LocalDate.parse(it.trim()) }.toSet()
            val overrides = exceptions.lines().filter { it.isNotBlank() }.associate { line ->
                val parts = line.trim().split(' '); require(parts.size == 2) { "Use YYYY-MM-DD HH:mm-HH:mm per exception" }
                val times = parts[1].split('-'); require(times.size == 2)
                LocalDate.parse(parts[0]) to SessionWindow(LocalTime.parse(times[0]), LocalTime.parse(times[1]))
            }
            require(dates.size <= 1000 && overrides.size <= 1000)
            return SessionCalendar(policy, dates, overrides)
        }
    }
}

data class Candle(val date: LocalDate, val close: Double, val open: Double? = null,
    val high: Double? = null, val low: Double? = null, val volume: Long? = null)
data class PriceHistory(val instrumentId: String, val currency: String, val source: String,
    val kind: DataKind, val comparable: Boolean, val asOf: Instant,
    val expectedDates: List<LocalDate>, val candles: List<Candle>)
interface HistoricalMarketDataProvider : MarketDataProvider { suspend fun history(instrument: Instrument): PriceHistory }

/** Reject questionable inputs rather than turning missing data into a bullish score. */
fun historyProblem(instrument: Instrument, quote: Quote, history: PriceHistory, now: Instant): String? {
    if (quote.freshness(now) !in setOf(Freshness.LIVE, Freshness.DELAYED)) return "STALE OR UNVERIFIED DATA"
    if (history.instrumentId != instrument.id || history.currency != quote.currency || history.kind != quote.kind || history.source != quote.source || !history.comparable)
        return "INCOMPARABLE HISTORY"
    if (history.asOf > now.plusSeconds(60) || Duration.between(history.asOf, now).toHours() > 24) return "STALE HISTORY"
    val bars = history.candles
    if (bars.size !in 60..1000) return "INSUFFICIENT DATA · 60 completed daily observations required"
    val dates = bars.map { it.date }
    if (dates != dates.sorted().distinct() || dates != history.expectedDates || dates.last() > quote.timestamp.atZone(ZoneId.of("Africa/Cairo")).toLocalDate()) return "MISSING OR DUPLICATE CANDLES"
    if (Duration.between(dates.last().atStartOfDay(ZoneId.of("Africa/Cairo")).toInstant(), now).toDays() > 7) return "STALE HISTORY"
    if (bars.any { b -> !b.close.isFinite() || b.close <= 0 || listOfNotNull(b.open, b.high, b.low).any { !it.isFinite() || it <= 0 } ||
        (b.volume != null && b.volume < 0) || (b.high != null && b.low != null && (b.low > b.high || b.close !in b.low..b.high || (b.open != null && b.open !in b.low..b.high))) }) return "MALFORMED HISTORY"
    if (bars.zipWithNext().any { (a,b) -> kotlin.math.abs(b.close / a.close - 1) > .5 } || kotlin.math.abs(quote.value.toDouble() / bars.last().close - 1) > .5) return "BAD TICK OR UNADJUSTED CORPORATE ACTION"
    return null
}
