package app.egxwatch.domain

import java.time.*
import kotlin.math.*

data class LocalSample(val timestamp:Instant,val price:Double,val volume:Long?=null)

/** Each calculation is independently gated. Sampled session closes are not official daily closes. */
class AdaptiveQuantitativeEngine {
    fun analyze(instrument:Instrument,q:Quote,sessions:List<Candle>,recent:List<LocalSample>,quality:SampleQuality,now:Instant,
        startedAt:Long?):StructuredAnalysis {
        val maturity=LocalIndicatorRequirements.maturity(quality)
        val discontinuity=sessions.any { !it.close.isFinite() || it.close<=0 } || recent.any { !it.price.isFinite() || it.price<=0 } ||
            sessions.zipWithNext().any { (a,b)->abs(b.close/a.close-1)>.5 } || recent.zipWithNext().any { (a,b)->abs(b.price/a.price-1)>.5 }
        if(discontinuity) return StructuredAnalysis("UNRELIABLE DATA",risks=listOf("Large price discontinuity or malformed sample; possible corporate action or bad tick. Verify the provider before interpreting this series."),maturity=maturity.name,building=LocalIndicatorRequirements.requirements.keys.toList(),observations=quality.validSamples,sessions=quality.sessions,completeness=quality.completeness,startedAt=startedAt)
        val fresh=q.freshness(now) in setOf(Freshness.LIVE,Freshness.DELAYED)
        val indicators=linkedMapOf<String,Double>();val available=mutableListOf<String>();val building=mutableListOf<String>()
        val drivers=mutableListOf<String>();val risks=mutableListOf<String>()
        val values=sessions.map { it.close }
        fun calculate(name:String,body:()->Double?) {
            val requirement=LocalIndicatorRequirements.requirements.getValue(name)
            if(requirement.state(if(name=="Intraday momentum") quality else quality.copy(sessions=sessions.size),fresh)==IndicatorState.AVAILABLE) {
                val value=body()
                if(value!=null && value.isFinite()) { indicators[name]=value;available+=name;return }
            }
            building+=name
        }
        val zone=ZoneId.of(if(instrument.id=="GLOBAL:XAUUSD") "America/New_York" else "Africa/Cairo")
        val sameDay=recent.filter { it.timestamp.atZone(zone).toLocalDate()==q.timestamp.atZone(zone).toLocalDate() && it.price>0 }
        calculate("Intraday momentum") { if(q.kind!=DataKind.NAV && sameDay.distinctBy { it.timestamp.epochSecond/900 }.size>=3) (q.value.toDouble()/sameDay.first().price-1)*100 else null }
        calculate("Observed session SMA5") { IndicatorEngine.sma(values,5) }
        calculate("Observed session SMA20") { IndicatorEngine.sma(values,20) }
        calculate("Observed session SMA50") { IndicatorEngine.sma(values,50) }
        calculate("Observed session EMA20") { IndicatorEngine.emaSeries(values,20).lastOrNull() }
        calculate("Observed session RSI14") { IndicatorEngine.rsi(values) }
        calculate("Observed session MACD") { IndicatorEngine.macd(values)?.let { it.first-it.second } }
        calculate("Observed session Bollinger20") { IndicatorEngine.sma(values,20)?.let { mean -> mean+2*IndicatorEngine.standardDeviation(values.takeLast(20)) } }
        calculate("Observed session volatility20") { if(values.size>=21) IndicatorEngine.standardDeviation(values.takeLast(21).zipWithNext { a,b -> ln(b/a) })*100 else null }
        calculate("Observed session ATR14") { if(q.kind==DataKind.NAV) null else IndicatorEngine.atr(sessions) }
        calculate("Observed drawdown20") { if(values.size>=20) IndicatorEngine.drawdown(values.takeLast(20))*100 else null }
        calculate("Observed support/resistance20") { sessions.takeLast(20).maxOfOrNull { it.high ?: it.close } }
        if(indicators.containsKey("Observed support/resistance20")) indicators["Observed support20"]=sessions.takeLast(20).minOf { it.low ?: it.close }
        if(fresh) q.fields.previousClose?.takeIf { it.signum()>0 }?.let { indicators["Provider daily change %"]=change(q.value,it).percent!!.toDouble();available+="Provider daily movement" }
        if(fresh && sameDay.size>=3 && sameDay.all { it.volume!=null } && sameDay.zipWithNext().all { (a,b)->b.volume!!>=a.volume!! }) {
            indicators["Observed cumulative volume increase"]=(sameDay.last().volume!!-sameDay.first().volume!!).toDouble();available+="Volume movement"
        }
        val components=linkedMapOf<String,Int>()
        indicators["Intraday momentum"]?.let { m -> components["Intraday momentum"]=(50+m*8).roundToInt().coerceIn(0,100);if(m>0) drivers+="Positive momentum since today's first collected observation" }
        indicators["Observed session SMA5"]?.let { ma -> components["Observed trend"] = if(q.value.toDouble()>ma) 75 else 25; if(q.value.toDouble()>ma) drivers+="Above five locally observed session closes" }
        indicators["Observed session RSI14"]?.let { rsi -> components["Momentum"] = if(rsi in 45.0..70.0) 80 else 40; if(rsi>70) risks+="Elevated RSI on sampled session closes" }
        indicators["Observed session volatility20"]?.let { v -> components["Risk resilience"]=(100-v*15).roundToInt().coerceIn(0,100);if(v>3) risks+="Elevated volatility in the collected sample" }
        indicators["Observed session MACD"]?.let { if(it>0) drivers+="Positive MACD difference on collected session closes" }
        val weights=mapOf("Intraday momentum" to .2,"Observed trend" to .35,"Momentum" to .25,"Risk resilience" to .2)
        val score=if(fresh && components.isNotEmpty()) (components.entries.sumOf { it.value*weights.getValue(it.key) }/components.keys.sumOf { weights.getValue(it) }).roundToInt() else null
        val confidence=(when(maturity) { AnalyticsMaturity.INITIALIZING->0.0;AnalyticsMaturity.INTRADAY->.4;AnalyticsMaturity.SHORT_TERM->.55;AnalyticsMaturity.DEVELOPING->.68;AnalyticsMaturity.ESTABLISHED->.82 }*quality.completeness).coerceIn(0.0,.82)
        risks+="Sampled observations only; unobserved market movements are unknown"
        if(building.isNotEmpty()) risks+="${building.size} indicators still building history"
        if(q.kind==DataKind.NAV) risks+="Published NAV is not an executable exchange price"
        risks+="No fundamental, news or market-context conclusion"
        return StructuredAnalysis(if(!fresh) "STALE DATA" else if(score==null) "BUILDING HISTORY" else "READY",score,confidence,
            components,indicators,drivers,risks,maturity.name,available,building,quality.validSamples,quality.sessions,quality.completeness,startedAt)
    }
}
