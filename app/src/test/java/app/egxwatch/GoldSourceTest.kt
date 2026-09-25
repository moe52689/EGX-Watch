package app.egxwatch
import app.egxwatch.data.*
import app.egxwatch.domain.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
class GoldSourceTest {
 @Test fun timestampAndUnitAreRequired() {
  val q=GoldMarketProvider.parse("""{"symbol":"XAU","currency":"USD","price":2000,"updatedAt":"2026-09-25T10:00:00Z"}""")
  validateQuote(GlobalGold.instrument,q,Instant.parse("2026-09-25T10:01:00Z"))
  assertEquals(Freshness.LIVE,q.freshness(Instant.parse("2026-09-25T10:01:00Z")))
  assertEquals(Freshness.STALE,q.freshness(Instant.parse("2026-09-25T11:00:00Z")))
  assertNull(q.fields.volume)
  assertThrows(Exception::class.java) { GoldMarketProvider.parse("""{"symbol":"XAU","currency":"EGP","price":2000}""") }
 }
 @Test fun goldScheduleIsIndependentAndHonorsLocalHolidays() {
  val friday=Instant.parse("2026-09-25T10:00:00Z")
  assertFalse(EgxSessionManager(Settings(),EngineConfig()).isOpen(friday))
  assertTrue(GoldSessionManager(GoldConfig()).isOpen(friday))
  assertFalse(GoldSessionManager(GoldConfig(holidays="2026-09-25")).isOpen(friday))
  assertFalse(GoldSessionManager(GoldConfig(start="12:00",end="13:00")).isOpen(friday))
 }
}
