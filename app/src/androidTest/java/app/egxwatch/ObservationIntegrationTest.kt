package app.egxwatch

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.egxwatch.data.*
import app.egxwatch.domain.*
import app.egxwatch.monitor.GoldAlertManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ObservationIntegrationTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private fun memory()=Room.inMemoryDatabaseBuilder(context,WatchDatabase::class.java).build()
 private val instrument=GlobalGold.instrument
 private val start=Instant.parse("2026-09-21T10:00:00Z")
 private fun quote(time:Instant,price:String="100")=Quote(instrument.id,price.toBigDecimal(),"USD",DataKind.SPOT,time,"Mock gold",timestampBasis=TimestampBasis.PROVIDER_SNAPSHOT)
 @Test fun forwardCollectionDeduplicatesRejectsRegressionAndAggregates()=runBlocking {
  val db=memory()
  try {
   val repo=ObservationRepository(db);val q=quote(start)
   assertTrue(repo.collect(instrument,q,start))
   assertFalse(repo.collect(instrument,q,start.plusSeconds(60)))
   assertFalse(repo.collect(instrument,quote(start.minusSeconds(1)),start))
   assertTrue(repo.collect(instrument,quote(start.plusSeconds(900),"110"),start.plusSeconds(900)))
   assertTrue(repo.collect(instrument,quote(start.plusSeconds(1800),"90"),start.plusSeconds(1800)))
   val key=ObservationRepository.seriesKey(instrument,q)
   assertEquals(3L,repo.dao.series(key)!!.observations)
   val bar=repo.dao.sessions(key).single();assertEquals("100",bar.open);assertEquals("110",bar.high);assertEquals("90",bar.low);assertEquals("90",bar.close)
   assertEquals(3L,bar.occupiedSlots)
   val chart=repo.dao.chart(key,start.toEpochMilli(),start.plusSeconds(1800).toEpochMilli(),3600000).first()
   assertEquals(1,chart.size);assertEquals(90.0,chart.single().price,0.0);assertEquals(start.plusSeconds(1800).toEpochMilli(),chart.single().time)
   repo.compact(start.plusSeconds(92*86400L));assertTrue(repo.dao.recent(key).isEmpty());assertEquals(3L,repo.dao.sessions(key).single().samples)
   StorageRepository(db).erase(instrument.id);assertNull(repo.dao.latestSeries(instrument.id));assertTrue(repo.dao.sessions(key).isEmpty())
  } finally { db.close() }
 }
 @Test fun revisedNavReplacesDateWithoutInflatingMaturity()=runBlocking {
  val db=memory()
  try {
   val fund=Instrument("FUND:TEST","TEST","Test fund",InstrumentType.FUND,"EGP","Mock","2026-09-21")
   val repo=ObservationRepository(db);val q=Quote(fund.id,"100".toBigDecimal(),"EGP",DataKind.NAV,start,"Mock NAV",timestampBasis=TimestampBasis.VALUATION_DATE)
   repo.collect(fund,q,start);assertTrue(repo.collect(fund,q.copy(value="101".toBigDecimal()),start.plusSeconds(60)))
   val key=ObservationRepository.seriesKey(fund,q)
   assertEquals(1L,repo.dao.series(key)!!.observations);assertEquals(1,repo.dao.recent(key).size);assertEquals("101",repo.dao.sessions(key).single().close)
  } finally { db.close() }
 }
 @Test fun staleObservationNeverEntersAnalyticalAggregate()=runBlocking {
  val db=memory()
  try { val repo=ObservationRepository(db);val q=quote(start);repo.collect(instrument,q,start.plusSeconds(7200))
   assertTrue(repo.dao.sessions(ObservationRepository.seriesKey(instrument,q)).isEmpty())
   assertEquals("STALE",repo.dao.latest(instrument.id)!!.freshness)
  } finally { db.close() }
 }
 @Test fun goldCooldownAndDedupSurviveDatabaseRestart()=runBlocking {
  val name="gold-restart-test.db";context.deleteDatabase(name)
  fun open()=Room.databaseBuilder(context,WatchDatabase::class.java,name).build()
  var db=open();var delivered=0
  try {
   db.forwardDao().save(GoldRule(type="PRICE_ABOVE",threshold=100.0))
   var manager=GoldAlertManager(db){delivered++}
   manager.onFresh(quote(start,"90"),start)
   val crossing=quote(start.plusSeconds(900),"110")
   manager.onFresh(crossing,start.plusSeconds(900));assertEquals(1,delivered)
   db.close();db=open();manager=GoldAlertManager(db){delivered++}
   manager.onFresh(crossing,start.plusSeconds(901));assertEquals(1,delivered)
   manager.onFresh(quote(start.plusSeconds(1800),"90"),start.plusSeconds(1800))
   manager.onFresh(quote(start.plusSeconds(2700),"110"),start.plusSeconds(2700));assertEquals(1,delivered)
   val event=db.forwardDao().alerts().first().single();db.forwardDao().read(event.id,true);db.forwardDao().dismiss(event.id)
   assertTrue(db.forwardDao().alerts().first().isEmpty())
  } finally { db.close();context.deleteDatabase(name) }
 }
 @Test fun largeIndexedSeriesQueryIsBoundedAndUsesActualLastSamples()=runBlocking {
  val db=memory()
  try {
   val repo=ObservationRepository(db);val q=quote(start);repo.collect(instrument,q,start)
   val key=ObservationRepository.seriesKey(instrument,q)
   val original=repo.dao.recent(key).single()
   val began=System.nanoTime()
   db.withTransaction { repeat(10000) { n->repo.dao.insert(original.copy(id="sample:$n",providerTime=start.toEpochMilli()+(n+1)*60000L,receivedAt=start.toEpochMilli()+(n+1)*60000L,price=(100+n%100).toString())) } }
   val points=repo.dao.chart(key,start.toEpochMilli(),start.toEpochMilli()+10000*60000L,1200000).first()
   assertTrue(points.size<=501);assertEquals(start.toEpochMilli()+10000*60000L,points.last().time)
   assertEquals(199.0,points.last().price,0.0)
   println("10k inserts plus bounded chart query: ${(System.nanoTime()-began)/1000000} ms; ${points.size} points")
  } finally { db.close() }
 }
}
