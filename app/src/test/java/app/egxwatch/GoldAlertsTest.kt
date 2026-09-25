package app.egxwatch
import app.egxwatch.data.*
import app.egxwatch.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
class GoldAlertsTest {
 private val now=Instant.parse("2026-09-25T10:00:00Z")
 private fun quote(value:Int)=Quote(GlobalGold.instrument.id,value.toBigDecimal(),"USD",DataKind.SPOT,now,"Mock",timestampBasis=TimestampBasis.PROVIDER_SNAPSHOT)
 @Test fun baselineCrossingDedupAndCooldown() {
  val rule=GoldRule(type="PRICE_ABOVE",threshold=100.0)
  val q=quote(101)
  assertFalse(GoldAlertEngine.evaluate(rule,null,q,emptyList(),now)!!.notify)
  val prior=GoldRuleState(0,"previous",false)
  assertTrue(GoldAlertEngine.evaluate(rule,prior,q,emptyList(),now)!!.notify)
  assertFalse(GoldAlertEngine.evaluate(rule,prior.copy(lastNotifiedAt=now.minusSeconds(10).toEpochMilli()),q,emptyList(),now)!!.notify)
  assertNull(GoldAlertEngine.evaluate(rule,prior.copy(fingerprint=q.fingerprint()),q,emptyList(),now))
  assertFalse(GoldAlertEngine.evaluate(rule,prior.copy(lastCondition=true),q,emptyList(),now)!!.notify)
  assertNull(GoldAlertEngine.evaluate(rule,prior,q,emptyList(),now.plusSeconds(3600)))
 }
 @Test fun percentNeedsRealBaselineAndRapidNeedsHistory() {
  val q=quote(110);val rule=GoldRule(type="PERCENT_MOVEMENT",threshold=5.0)
  assertNull(GoldAlertEngine.evaluate(rule,null,q,emptyList(),now))
  assertTrue(GoldAlertEngine.evaluate(rule,GoldRuleState(0,"old",false),q,listOf(LocalSample(now.minusSeconds(3600),100.0)),now)!!.notify)
  assertNull(GoldAlertEngine.evaluate(rule.copy(type="RAPID_MOVEMENT"),null,q,emptyList(),now))
 }
}
