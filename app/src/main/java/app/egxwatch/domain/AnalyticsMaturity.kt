package app.egxwatch.domain

/** A session is represented by observed data, not simply by an elapsed calendar day. */
enum class AnalyticsMaturity { INITIALIZING, INTRADAY, SHORT_TERM, DEVELOPING, ESTABLISHED }
enum class IndicatorState { AVAILABLE, INSUFFICIENT_DATA, STALE_DATA }
data class SampleQuality(val validSamples:Long,val sessions:Int,val occupiedSlots:Long,val expectedSlots:Long,
    val expectedSessions:Int= sessions,val validFraction:Double=1.0) {
    val completeness:Double get() = if(expectedSlots==0L || expectedSessions==0) 0.0 else
        (occupiedSlots.toDouble()/expectedSlots).coerceIn(0.0,1.0) * (sessions.toDouble()/expectedSessions).coerceIn(0.0,1.0) * validFraction.coerceIn(0.0,1.0)
}
data class IndicatorRequirement(val minimumSamples:Int,val minimumSessions:Int,val maximumMissingRatio:Double) {
    fun state(quality:SampleQuality, fresh:Boolean=true):IndicatorState = when {
        !fresh -> IndicatorState.STALE_DATA
        quality.validSamples<minimumSamples || quality.occupiedSlots<minimumSamples || quality.sessions<minimumSessions ||
            1-quality.completeness>maximumMissingRatio+1e-9 -> IndicatorState.INSUFFICIENT_DATA
        else -> IndicatorState.AVAILABLE
    }
}
object LocalIndicatorRequirements {
    val requirements=linkedMapOf(
        "Intraday momentum" to IndicatorRequirement(3,1,.4),
        "Observed session SMA5" to IndicatorRequirement(5,5,.25),
        "Observed session SMA20" to IndicatorRequirement(20,20,.2),
        "Observed session SMA50" to IndicatorRequirement(50,50,.1),
        "Observed session EMA20" to IndicatorRequirement(20,20,.2),
        "Observed session RSI14" to IndicatorRequirement(15,15,.2),
        "Observed session MACD" to IndicatorRequirement(35,35,.1),
        "Observed session Bollinger20" to IndicatorRequirement(20,20,.2),
        "Observed session volatility20" to IndicatorRequirement(21,21,.1),
        "Observed session ATR14" to IndicatorRequirement(15,15,.2),
        "Observed drawdown20" to IndicatorRequirement(20,20,.2),
        "Observed support/resistance20" to IndicatorRequirement(20,20,.2))
    fun maturity(q:SampleQuality):AnalyticsMaturity=when {
        IndicatorRequirement(50,50,.1).state(q)==IndicatorState.AVAILABLE -> AnalyticsMaturity.ESTABLISHED
        IndicatorRequirement(20,20,.2).state(q)==IndicatorState.AVAILABLE -> AnalyticsMaturity.DEVELOPING
        IndicatorRequirement(5,5,.25).state(q)==IndicatorState.AVAILABLE -> AnalyticsMaturity.SHORT_TERM
        IndicatorRequirement(3,1,.4).state(q)==IndicatorState.AVAILABLE -> AnalyticsMaturity.INTRADAY
        else -> AnalyticsMaturity.INITIALIZING
    }
}
