package app.egxwatch

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ProviderRepositoryIntegrationTest {
    private fun database()=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),WatchDatabase::class.java).build()
    @Test fun missingEgxConfigurationDoesNotOverwriteSavedValuesOrAttemptQuotes()=runBlocking {
        val db=database()
        try {
            val repo=WatchRepository(db);repo.initialize()
            val i=DirectoryProvider.referenceInstruments.first()
            repo.add(db.dao().getLists().single().id,i)
            val old=db.dao().getInstruments().single().copy(value="100",timestamp="2026-09-25T09:00:00Z",kind="INDICATIVE",quoteSource="Legacy saved feed",error="Old connection failure")
            db.dao().update(old);val settings=db.dao().getSettings()
            assertEquals(0,repo.check(false){error("Must not notify")})
            assertEquals(old,db.dao().getInstruments().single());assertEquals(settings,db.dao().getSettings())
            assertTrue(db.engineDao().status()!!.message.startsWith("SETUP REQUIRED"));assertTrue(repo.diagnostics.value.isEmpty())
        } finally { db.close() }
    }
    @Test fun goldFailureRetainsCacheAndEgxStateDoesNotDisableGold()=runBlocking {
        val db=database()
        try {
            val now=Instant.now()
            val q=Quote(GlobalGold.instrument.id,"2000".toBigDecimal(),"USD",DataKind.SPOT,now,"Mock gold",timestampBasis=TimestampBasis.PROVIDER_SNAPSHOT)
            var failing=false
            val provider=object:MarketDataProvider {
                override suspend fun search(query:String)=listOf(GlobalGold.instrument)
                override suspend fun resolve(id:String)=GlobalGold.instrument
                override suspend fun marketStatus()=MarketStatus("UNKNOWN","Mock",now)
                override suspend fun quote(instrument:Instrument):Quote { if(failing) throw ProviderHttpException(503);return q }
            }
            db.engineDao().health(ProviderHealth("https://same.test/|quote:STOCK",1,now.plusSeconds(7200).toEpochMilli(),null,"HTTP 429"))
            val repo=GoldMarketRepository(db,AnalyticsRepository(db){"not delivered"}) { listOf(ProviderEndpoint("gold:https://same.test/",provider)) }
            assertTrue(repo.check(false));val saved=db.forwardDao().goldStatus()!!
            failing=true;assertFalse(repo.check(false))
            val after=db.forwardDao().goldStatus()!!
            assertEquals(saved.payload,after.payload);assertEquals(saved.lastSuccess,after.lastSuccess)
            assertTrue(after.message.contains("unavailable"));assertTrue(repo.diagnostics.value.any { it.state=="REJECTED" })
        } finally { db.close() }
    }
    @Test fun duplicateManualRefreshDoesNotQueueAnotherProviderRequest()=runBlocking {
        val db=database()
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var calls=0
        try {
            val i=DirectoryProvider.referenceInstruments.first()
            val provider=object:MarketDataProvider {
                override suspend fun search(query:String)=listOf(i)
                override suspend fun resolve(id:String)=i
                override suspend fun marketStatus()=MarketStatus("UNKNOWN","Mock",null)
                override suspend fun quote(instrument:Instrument):Quote { calls++;entered.complete(Unit);release.await();return Quote(i.id,"100".toBigDecimal(),"EGP",DataKind.LIVE,Instant.now(),"Mock") }
            }
            val repo=WatchRepository(db){provider};repo.initialize();repo.add(db.dao().getLists().single().id,i)
            val first=async { repo.check(false){"unused"} };entered.await()
            assertEquals(0,withTimeout(1000) { repo.check(false){"unused"} })
            release.complete(Unit);assertEquals(1,first.await());assertEquals(1,calls)
        } finally { release.complete(Unit);db.close() }
    }
}
