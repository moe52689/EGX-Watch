package app.egxwatch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class EngineRepositoryTest {
    @Test fun snapshotsAnalyticsCooldownAndHealthSurviveRestart()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="engine-restart-test.db"
        context.deleteDatabase(name)
        fun open()=Room.databaseBuilder(context,WatchDatabase::class.java,name).build()
        var db=open()
        val instrument=DirectoryProvider.referenceInstruments.first()
        val now=Instant.now();var q=Quote(instrument.id,"110".toBigDecimal(),"EGP",DataKind.LIVE,now,"Test provider")
        var historyCalls=0;var deliveries=0
        val mock=object:HistoricalMarketDataProvider {
            override suspend fun search(query:String)=listOf(instrument)
            override suspend fun resolve(id:String)=instrument
            override suspend fun quote(instrument:Instrument)=q
            override suspend fun marketStatus()=MarketStatus("UNKNOWN","Test",now)
            override suspend fun history(instrument:Instrument):PriceHistory {
                historyCalls++
                val end=now.atZone(ZoneId.of("Africa/Cairo")).toLocalDate().minusDays(1)
                val bars=(0..99).map { Candle(end.minusDays(99L-it),100+it*.1,100+it*.1,101+it*.1,99+it*.1,1000) }
                return PriceHistory(instrument.id,"EGP","Test provider",DataKind.LIVE,true,now,bars.map { it.date },bars)
            }
        }
        fun repository():WatchRepository {
            val analytics=AnalyticsRepository(db) { deliveries++;"Test delivery" }
            return WatchRepository(db) { mock }.apply { snapshotObserver={i,q,p->analytics.observe(i,q,p)} }
        }
        try {
            var repo=repository();repo.initialize()
            repo.save(Settings(days="1,2,3,4,5,6,7",start="00:00",end="00:00"))
            repo.saveEngine(EngineConfig(enabled=true,minimumScore=0,minimumConfidence=0.0,quietStart="00:00",quietEnd="00:00"))
            repo.add(db.dao().getLists().single().id,instrument)
            repo.check(false){"Test"}
            assertEquals("READY",db.engineDao().analysis(instrument.id)!!.analysis().status)
            assertEquals(1,historyCalls);assertEquals(1,deliveries)
            repo.check(false){"Test"};assertEquals(1,historyCalls);assertEquals(1,deliveries)
            q=q.copy(timestamp=now.plusSeconds(1),fields=MarketFields(volume=1001))
            repo.check(false){"Test"};assertEquals(1,deliveries)
            val retryAt=now.plusSeconds(3600).toEpochMilli()
            db.engineDao().health(ProviderHealth("test",2,retryAt,null,"HTTP 429"))
            // The next repository check prunes non-configured provider diagnostics, so verify before checking.
            db.close();db=open();assertEquals(retryAt,db.engineDao().health("test")!!.retryAt)
            repo=repository();repo.initialize();repo.check(false){"Test"}
            assertEquals(1,historyCalls);assertEquals(1,deliveries)
            assertEquals(1,db.engineDao().events().first().size)
            assertEquals("110",db.dao().getInstruments().single().value)
            assertNotNull(db.engineDao().alertState(instrument.id))
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun offSessionNeverCallsProvider()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WatchDatabase::class.java).build()
        var calls=0;val i=DirectoryProvider.referenceInstruments.first()
        val provider=object:MarketDataProvider {
            override suspend fun search(query:String)=listOf(i)
            override suspend fun resolve(id:String)=i
            override suspend fun quote(instrument:Instrument):Quote { calls++;error("Should not poll") }
            override suspend fun marketStatus()=MarketStatus("UNKNOWN","Test",null)
        }
        try {
            val repo=WatchRepository(db){provider};repo.initialize();repo.add(db.dao().getLists().single().id,i)
            repo.save(Settings(enabled=true,days="1,2,3,4,5,6,7",start="00:00",end="00:00"))
            repo.saveEngine(EngineConfig(holidays=LocalDate.now(ZoneId.of("Africa/Cairo")).toString()))
            assertEquals(0,repo.check(true){"Test"});assertEquals(0,calls)
        } finally { db.close() }
    }
}
