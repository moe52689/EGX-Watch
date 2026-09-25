package app.egxwatch.domain

import java.time.*
import kotlin.math.*

object IndicatorEngine {
    fun sma(values: List<Double>, period: Int): Double? = values.takeLast(period).takeIf { period > 0 && it.size == period }?.average()
    fun emaSeries(values: List<Double>, period: Int): List<Double> {
        require(period > 0)
        if (values.size < period) return emptyList()
        val result = mutableListOf(values.take(period).average()); val alpha = 2.0 / (period + 1)
        values.drop(period).forEach { result += it * alpha + result.last() * (1 - alpha) }; return result
    }
    fun rsi(values: List<Double>, period: Int = 14): Double? {
        if (values.size <= period) return null
        val changes = values.zipWithNext { a,b -> b-a }
        var gain = changes.take(period).sumOf { max(0.0,it) } / period
        var loss = changes.take(period).sumOf { max(0.0,-it) } / period
        changes.drop(period).forEach { gain = (gain * (period-1) + max(0.0,it))/period; loss = (loss * (period-1) + max(0.0,-it))/period }
        return if (loss == 0.0) { if (gain == 0.0) 50.0 else 100.0 } else 100 - 100/(1+gain/loss)
    }
    fun macd(values: List<Double>): Pair<Double, Double>? {
        val fast = emaSeries(values,12); val slow = emaSeries(values,26)
        val line = fast.drop(14).zip(slow) { a,b -> a-b }; val signal = emaSeries(line,9)
        return if (signal.isEmpty()) null else line.last() to signal.last()
    }
    fun standardDeviation(values: List<Double>): Double = sqrt(values.map { (it-values.average()).pow(2) }.average())
    fun atr(bars: List<Candle>, period: Int = 14): Double? {
        if (bars.size <= period || bars.any { it.high == null || it.low == null }) return null
        val ranges = bars.zipWithNext { a,b -> max(b.high!!-b.low!!, max(abs(b.high-a.close),abs(b.low-a.close))) }
        var result = ranges.take(period).average(); ranges.drop(period).forEach { result = (result*(period-1)+it)/period }; return result
    }
    fun drawdown(values: List<Double>): Double {
        var peak=values.first(); var worst=0.0
        values.forEach { peak=max(peak,it); worst=max(worst,1-it/peak) }; return worst
    }
}

enum class OpportunityState { NORMAL, WATCH, OPPORTUNITY, STRONG_OPPORTUNITY }
data class StructuredAnalysis(val status: String, val score: Int? = null, val confidence: Double = 0.0,
    val components: Map<String, Int> = emptyMap(), val indicators: Map<String, Double> = emptyMap(),
    val drivers: List<String> = emptyList(), val risks: List<String> = emptyList(),
    val maturity:String="INITIALIZING",val available:List<String> = emptyList(),val building:List<String> = emptyList(),
    val observations:Long=0,val sessions:Int=0,val completeness:Double=0.0,val startedAt:Long?=null) {
    val state: OpportunityState get() = when { score == null || score < 60 -> OpportunityState.NORMAL; score < 75 -> OpportunityState.WATCH;
        score < 85 -> OpportunityState.OPPORTUNITY; else -> OpportunityState.STRONG_OPPORTUNITY }
}

/** Reproducible heuristic, not a fitted probability model or return forecast. */
class QuantitativeEngine {
    fun analyze(instrument: Instrument, quote: Quote, history: PriceHistory, now: Instant): StructuredAnalysis {
        historyProblem(instrument,quote,history,now)?.let { return StructuredAnalysis(it) }
        val values=history.candles.map { it.close }; val bars=history.candles
        val price=quote.value.toDouble(); val sma20=IndicatorEngine.sma(values,20)!!; val sma50=IndicatorEngine.sma(values,50)!!
        val ema20=IndicatorEngine.emaSeries(values,20).last(); val rsi=IndicatorEngine.rsi(values)!!
        val macd=IndicatorEngine.macd(values)!!; val sigma=IndicatorEngine.standardDeviation(values.takeLast(20))
        val returns=values.zipWithNext { a,b -> ln(b/a) }; val volatility=IndicatorEngine.standardDeviation(returns.takeLast(20))*sqrt(252.0)
        val support=bars.takeLast(20).minOf { it.low ?: it.close }; val resistance=bars.takeLast(20).maxOf { it.high ?: it.close }
        val momentum=price/values[values.size-20]-1; val drawdown=IndicatorEngine.drawdown(values)
        val avgVolume=bars.dropLast(1).takeLast(20).map { it.volume }.takeIf { it.size==20 && it.all { v -> v != null } }?.map { it!!.toDouble() }?.average()
        // Full daily volume only: never compare partial intraday volume to a full-day mean.
        val relativeVolume=avgVolume?.takeIf { it > 0 }?.let { avg -> bars.last().volume?.div(avg) }
        val trend=(listOf(price>sma20,price>sma50,sma20>sma50,price>ema20).count { it }*25)
        val momentumScore=((if (momentum>0) 35 else 0)+(if (rsi in 45.0..70.0) 35 else if (rsi in 30.0..80.0) 15 else 0)+(if (macd.first>macd.second) 30 else 0))
        val riskScore=(100-volatility*80-drawdown*100).roundToInt().coerceIn(0,100)
        val volumeScore=relativeVolume?.let { (50+(it-1)*35).roundToInt().coerceIn(0,100) }
        val components=linkedMapOf("Trend" to trend,"Momentum" to momentumScore,"Risk resilience" to riskScore)
        volumeScore?.let { components["Volume"] = it }
        val weights=mapOf("Trend" to .35,"Momentum" to .30,"Risk resilience" to .25,"Volume" to .10)
        val score=(components.entries.sumOf { it.value*weights.getValue(it.key) }/components.keys.sumOf { weights.getValue(it) }).roundToInt()
        val features=linkedMapOf("SMA20" to sma20,"SMA50" to sma50,"EMA20" to ema20,"RSI14" to rsi,
            "MACD" to macd.first,"MACD signal" to macd.second,"Bollinger lower" to sma20-2*sigma,"Bollinger upper" to sma20+2*sigma,
            "Annualized volatility" to volatility,"Momentum20" to momentum,"Support20" to support,"Resistance20" to resistance,
            "Maximum drawdown" to drawdown,"Trend confirmation fraction" to trend/100.0)
        IndicatorEngine.atr(bars)?.let { features["ATR14"] = it }
        relativeVolume?.let { features["Relative daily volume"] = it }
        if (price>support && resistance>price) features["Historical range reward/risk"]=(resistance-price)/(price-support)
        val drivers=mutableListOf<String>(); val risks=mutableListOf<String>()
        if (price>sma50) drivers+="Above 50-observation moving average"
        if (momentum>0) drivers+="Positive 20-observation momentum"
        if (macd.first>macd.second) drivers+="MACD above signal line"
        if (relativeVolume != null && relativeVolume>=1.2) drivers+="Completed-day volume above 20-day average"
        if (price>resistance) drivers+="Above prior 20-observation resistance"
        if (price<support) risks+="Below prior 20-observation support"
        if (resistance>=price && resistance/price-1<.03) risks+="Approaching historical resistance"
        if (volatility>.4) risks+="Elevated historical volatility"
        if (rsi>70) risks+="RSI elevated; reversal risk"
        if (drawdown>.2) risks+="Historical drawdown exceeds 20%"
        if (relativeVolume != null && relativeVolume>3) risks+="Abnormally high completed-day volume"
        if (abs(price/values.last()-1)>.1) risks+="Large move from last completed observation"
        if (quote.kind==DataKind.NAV) risks+="NAV valuations are not executable market prices"
        risks+="Fundamentals, news and market context not supplied"
        val confidence=(.60 + (if (values.size>=100) .08 else 0.0)+(if (volumeScore!=null) .08 else 0.0)+
            (if (features.containsKey("ATR14")) .08 else 0.0)+(if (quote.kind==DataKind.LIVE) .04 else 0.0)).coerceAtMost(.88)
        return StructuredAnalysis("READY",score,confidence,components,features,drivers,risks)
    }
}
interface AIInterpretationEngine { fun explain(analysis: StructuredAnalysis): String }
class StructuredExplanationEngine : AIInterpretationEngine {
    override fun explain(analysis: StructuredAnalysis): String = if (analysis.score==null) analysis.status else
        "${analysis.state.name.replace('_',' ')} · heuristic score ${analysis.score}/100. " + analysis.drivers.joinToString("; ") +
            ". Confidence measures data coverage, not the probability of a return. Analytical signals are not guaranteed outcomes or individualized financial advice."
}

data class OpportunityRules(val enabled: Boolean = false, val minimumScore: Int = 75, val minimumConfidence: Double = .70,
    val cooldownMinutes: Long = 60, val materialChange: Int = 10, val dailyLimit: Int = 10,
    val quietStart: LocalTime = LocalTime.of(22,0), val quietEnd: LocalTime = LocalTime.of(8,0)) {
    init { require(minimumScore in 0..100 && minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0 && cooldownMinutes in 0..10080 && materialChange in 1..100 && dailyLimit in 1..100) }
    fun quiet(now: Instant): Boolean {
        val time=now.atZone(ZoneId.of("Africa/Cairo")).toLocalTime()
        return if (quietStart==quietEnd) false else if (quietStart<quietEnd) time>=quietStart && time<quietEnd else time>=quietStart || time<quietEnd
    }
}
data class LastOpportunity(val fingerprint: String, val score: Int, val state: OpportunityState, val at: Instant)
fun shouldNotifyOpportunity(analysis: StructuredAnalysis, fingerprint: String, previous: LastOpportunity?,
    dailyCount: Int, now: Instant, rules: OpportunityRules, previousObservedState:OpportunityState?=null): Boolean {
    val score=analysis.score ?: return false
    if (!rules.enabled || analysis.status!="READY" || score<rules.minimumScore || analysis.confidence<rules.minimumConfidence || rules.quiet(now) || dailyCount>=rules.dailyLimit) return false
    if (previous==null) return true
    if (fingerprint==previous.fingerprint || now < previous.at.plusSeconds(rules.cooldownMinutes*60)) return false
    return analysis.state.ordinal>previous.state.ordinal || (previousObservedState!=null && analysis.state.ordinal>previousObservedState.ordinal) || abs(score-previous.score)>=rules.materialChange
}
