package app.egxwatch.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

enum class InstrumentType { STOCK, ETF, FUND, COMMODITY }
enum class DataKind { LIVE, DELAYED, NAV, INDICATIVE, SPOT }
enum class TimestampBasis { EXCHANGE, VALUATION_DATE, PROVIDER_SNAPSHOT, RETRIEVAL_TIME }
data class Instrument(val id: String, val ticker: String, val name: String, val type: InstrumentType,
    val currency: String = "EGP", val source: String, val verifiedAt: String)
data class Quote(val instrumentId: String, val value: BigDecimal, val currency: String,
    val kind: DataKind, val timestamp: Instant, val source: String, val delayMinutes: Int? = null,
    val timestampBasis: TimestampBasis = TimestampBasis.EXCHANGE, val notice: String? = null, val fields: MarketFields = MarketFields())
data class MarketStatus(val state: String, val detail: String, val timestamp: Instant?)

interface MarketDataProvider {
    suspend fun search(query: String): List<Instrument>
    suspend fun resolve(id: String): Instrument
    suspend fun quote(instrument: Instrument): Quote
    suspend fun marketStatus(): MarketStatus
}

data class MonitorPolicy(val intervalMinutes: Long = 15, val days: Set<DayOfWeek> = setOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    val start: LocalTime = LocalTime.MIDNIGHT, val end: LocalTime = LocalTime.MIDNIGHT,
    val everyCheck: Boolean = false, val absoluteThreshold: BigDecimal? = null,
    val percentThreshold: BigDecimal? = null) {
    init {
        require(intervalMinutes in 15..525600) { "Interval must be 15–525600 minutes" }
        require(days.isNotEmpty()) { "Select at least one monitoring day" }
        require(absoluteThreshold == null || absoluteThreshold > BigDecimal.ZERO)
        require(percentThreshold == null || percentThreshold > BigDecimal.ZERO)
    }
    fun isActive(instant: Instant): Boolean {
        val local = instant.atZone(ZoneId.of("Africa/Cairo"))
        val time = local.toLocalTime()
        if (start == end) return local.dayOfWeek in days
        if (start < end) return local.dayOfWeek in days && time >= start && time < end
        // An overnight window belongs to the day on which it starts.
        return (time >= start && local.dayOfWeek in days) ||
            (time < end && local.minusDays(1).dayOfWeek in days)
    }
}
data class Change(val absolute: BigDecimal, val percent: BigDecimal?)
fun change(current: BigDecimal, previous: BigDecimal): Change {
    val delta = current - previous
    return Change(delta, if (previous.signum() == 0) null else
        delta.multiply(BigDecimal(100)).divide(previous.abs(), 6, RoundingMode.HALF_UP))
}
fun shouldNotify(current: BigDecimal, previous: BigDecimal?, policy: MonitorPolicy): Boolean {
    if (policy.everyCheck) return true
    if (previous == null || current.compareTo(previous) == 0) return false
    val delta = change(current, previous)
    if (policy.absoluteThreshold == null && policy.percentThreshold == null) return true
    return (policy.absoluteThreshold?.let { delta.absolute.abs() >= it } == true) ||
        (policy.percentThreshold?.let { limit -> delta.percent?.abs()?.let { it >= limit } } == true)
}
fun validateQuote(instrument: Instrument, quote: Quote, now: Instant = Instant.now()) {
    require(quote.kind != DataKind.SPOT || (instrument.type == InstrumentType.COMMODITY && quote.timestampBasis == TimestampBasis.PROVIDER_SNAPSHOT))
    quote.fields.validate()
    require(quote.instrumentId == instrument.id) { "Quote belongs to a different instrument" }
    require(quote.currency == instrument.currency) { "Quote currency changed" }
    require(quote.value.signum() >= 0 && quote.value.precision() <= 24 && quote.value.scale() in 0..12) { "Invalid value" }
    require(quote.timestamp <= now.plusSeconds(300)) { "Quote timestamp is in the future" }
    require(quote.source.isNotBlank()) { "Missing data source" }
    require(if (instrument.type == InstrumentType.STOCK) quote.kind != DataKind.NAV else
        instrument.type in setOf(InstrumentType.ETF, InstrumentType.COMMODITY) || quote.kind == DataKind.NAV) { "Incorrect quote/NAV classification" }
    require(quote.kind != DataKind.DELAYED || (quote.delayMinutes != null && quote.delayMinutes in 0..1440)) { "Missing or invalid delay" }
    require(quote.kind != DataKind.NAV || quote.timestampBasis in setOf(TimestampBasis.VALUATION_DATE, TimestampBasis.EXCHANGE)) { "NAV requires a valuation timestamp" }
    require(quote.kind != DataKind.INDICATIVE || quote.timestampBasis in setOf(TimestampBasis.PROVIDER_SNAPSHOT, TimestampBasis.RETRIEVAL_TIME))
    require(quote.kind !in setOf(DataKind.LIVE, DataKind.DELAYED) || quote.timestampBasis == TimestampBasis.EXCHANGE)
}
fun BigDecimal.display(): String = stripTrailingZeros().toPlainString()
