package app.egxwatch.domain

import app.egxwatch.data.GoldRule
import app.egxwatch.data.GoldRuleState
import java.time.Instant
import kotlin.math.abs

enum class GoldAlertType { PRICE_ABOVE, PRICE_BELOW, PERCENT_MOVEMENT, RAPID_MOVEMENT, NEW_HIGH, NEW_LOW }
data class GoldDecision(val condition:Boolean,val notify:Boolean,val detail:String)
object GoldAlertEngine {
 fun validate(rule:GoldRule) {
  val type=GoldAlertType.valueOf(rule.type)
  require(rule.threshold.isFinite() && rule.threshold>=0)
  if(type in setOf(GoldAlertType.PRICE_ABOVE,GoldAlertType.PRICE_BELOW,GoldAlertType.PERCENT_MOVEMENT)) require(rule.threshold>0)
  require(rule.periodMinutes in 15..10080 && rule.cooldownMinutes in 15..10080)
 }
 fun evaluate(rule:GoldRule,previous:GoldRuleState?,q:Quote,history:List<LocalSample>,now:Instant):GoldDecision? {
  validate(rule)
  if(!rule.enabled || q.instrumentId!=GlobalGold.instrument.id || q.freshness(now) !in setOf(Freshness.LIVE,Freshness.DELAYED) || previous?.fingerprint==q.fingerprint()) return null
  val type=GoldAlertType.valueOf(rule.type);val price=q.value.toDouble()
  val earlier=history.filter { it.timestamp<q.timestamp && it.price.isFinite() && it.price>0 }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
  val cutoff=q.timestamp.minusSeconds(rule.periodMinutes*60)
  val baseline=earlier.lastOrNull { it.timestamp<=cutoff && it.timestamp>=cutoff.minusSeconds(30*60) }
  val recent=earlier.filter { it.timestamp>=cutoff }
  val condition=when(type) {
   GoldAlertType.PRICE_ABOVE->price>rule.threshold
   GoldAlertType.PRICE_BELOW->price<rule.threshold
   GoldAlertType.PERCENT_MOVEMENT->{ if(baseline==null) return null;abs(price/baseline.price-1)*100>=rule.threshold }
   GoldAlertType.NEW_HIGH,GoldAlertType.NEW_LOW->{ if(baseline==null || recent.size<3) return null
    if(type==GoldAlertType.NEW_HIGH) price>(recent+baseline).maxOf { it.price } else price<(recent+baseline).minOf { it.price } }
   GoldAlertType.RAPID_MOVEMENT->{
    val intervals=earlier.takeLast(22).zipWithNext();if(intervals.size<20) return null
    val durations=intervals.map { java.time.Duration.between(it.first.timestamp,it.second.timestamp).seconds }
    val median=durations.sorted()[durations.size/2];val lastGap=java.time.Duration.between(earlier.last().timestamp,q.timestamp).seconds
    if(median<=0 || durations.any { it<median/2 || it>median*2 } || lastGap<median/2 || lastGap>median*2) return null
    val moves=intervals.map { (it.second.price/it.first.price-1)*100 };val mean=moves.average();val deviation=IndicatorEngine.standardDeviation(moves)
    val movement=(price/earlier.last().price-1)*100
    abs(movement-mean)>=maxOf(rule.threshold,3*deviation,.1)
   }
  }
  val crossing=previous!=null && !previous.lastCondition && condition
  val cooled=previous?.lastNotifiedAt?.let { now.toEpochMilli()-it>=rule.cooldownMinutes*60000 } ?: true
  return GoldDecision(condition,crossing && cooled,"${type.name.replace('_',' ')} · ${q.value.display()} USD/oz · ${q.source} · ${q.timestamp}")
 }
}

/** Shares deterministic calculations, but gold never enters the EGX opportunity notifier. */
class GoldAnalyticsEngine {
 fun analyze(q:Quote,sessions:List<Candle>,recent:List<LocalSample>,quality:SampleQuality,now:Instant,startedAt:Long?)=
  AdaptiveQuantitativeEngine().analyze(GlobalGold.instrument,q,sessions,recent,quality,now,startedAt)
}
