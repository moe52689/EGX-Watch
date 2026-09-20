package app.egxwatch

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.egxwatch.data.*
import app.egxwatch.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: WatchDatabase
    private lateinit var repo: WatchRepository
    private val instrument = DirectoryProvider.instruments.first()
    private var value = "10.00"
    private var time = Instant.parse("2026-01-01T10:00:00Z")
    private var failure = false
    private val fake = object : MarketDataProvider {
        override suspend fun search(query: String) = listOf(instrument)
        override suspend fun resolve(id: String) = instrument
        override suspend fun quote(instrument: Instrument): Quote {
            if (failure || instrument.ticker != "CCAP") throw IOException("No feed")
            return Quote(instrument.id, value.toBigDecimal(), "EGP", DataKind.DELAYED, time, "Test only", 15)
        }
        override suspend fun marketStatus() = MarketStatus("Unknown", "Test", null)
    }
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WatchDatabase::class.java).build()
        repo = WatchRepository(db) { fake }
        repo.initialize()
    }
    @After fun close() { db.close() }
    private suspend fun check() = repo.check(false) { "Test delivery" }
    @Test fun baselineChangeDuplicateAndFailureAreHandledAtomically() = runBlocking {
        assertEquals(1, check()); assertTrue(db.dao().history().first().isEmpty())
        value = "11"; time = time.plusSeconds(900); check()
        val alert = db.dao().history().first().single()
        assertEquals("10", alert.previous); assertEquals("1", alert.absolute); assertEquals("10", alert.percent)
        assertEquals("Test delivery", alert.delivery)
        check(); assertEquals(1, db.dao().history().first().size)
        failure = true; check()
        val row = db.dao().getInstruments().first { it.ticker == "CCAP" }
        assertEquals("11", row.value); assertNotNull(row.error)
        assertEquals(1, db.dao().history().first().size)
    }
    @Test fun olderAndSameTimestampChangedValuesDoNotOverwriteBaseline() = runBlocking {
        check(); value = "999"; check()
        assertEquals("10", db.dao().getInstruments().first { it.ticker == "CCAP" }.value)
        time = time.minusSeconds(60); check()
        assertEquals("10", db.dao().getInstruments().first { it.ticker == "CCAP" }.value)
        assertTrue(db.dao().history().first().isEmpty())
    }
    @Test fun everyCheckRecordsUnchangedAndBlockedDelivery() = runBlocking {
        repo.save(Settings(everyCheck = true))
        repo.check(false) { "Blocked by notification permission" }; check()
        assertEquals(2, db.dao().history().first().size)
        assertEquals("Blocked by notification permission", db.dao().history().first().last().delivery)
    }
    @Test fun sameInstrumentInTwoListsOnlyNotifiesOnce() = runBlocking {
        val list = db.dao().insertList(Watchlist(name = "Second")); repo.add(list, instrument)
        check(); value = "12"; time = time.plusSeconds(900); check()
        assertEquals(1, db.dao().history().first().size)
        assertEquals(2, db.dao().getInstruments().count { it.value == "12" })
    }
    @Test fun duplicateAdditionIsRejectedWithoutBlocking() = runBlocking {
        val list = db.dao().getLists().first()
        try { repo.add(list.id, instrument); fail("Duplicate should be rejected") }
        catch (expected: IllegalStateException) { assertEquals("Already in this watchlist", expected.message) }
        assertEquals(1, check())
    }
    @Test fun providerSwitchResetsValuesAndDisabledBackgroundDoesNothing() = runBlocking {
        check(); repo.save(Settings(freeFeeds = false))
        assertTrue(db.dao().getInstruments().all { it.value == null })
        assertEquals(0, repo.check(true) { "unexpected" })
    }
    @Test fun perInstrumentThresholdSuppressesSmallChanges() = runBlocking {
        val row = db.dao().getInstruments().first { it.ticker == "CCAP" }
        db.dao().update(row.copy(absoluteThreshold = "5"))
        check(); value = "11"; time = time.plusSeconds(900); check()
        assertTrue(db.dao().history().first().isEmpty())
        value = "16"; time = time.plusSeconds(900); check()
        assertEquals(1, db.dao().history().first().size)
    }
}
