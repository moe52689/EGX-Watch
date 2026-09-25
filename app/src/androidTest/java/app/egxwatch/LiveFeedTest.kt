package app.egxwatch

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.egxwatch.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveFeedTest {
    @Test fun productionDefaultsRequireAnAuthorizedGateway()=runBlocking {
        assertFalse(Settings().freeFeeds)
        val db=androidx.room.Room.inMemoryDatabaseBuilder(androidx.test.core.app.ApplicationProvider.getApplicationContext(),WatchDatabase::class.java).build()
        try { assertTrue(WatchRepository(db).provider(Settings(freeFeeds=true)) is DirectoryProvider) } finally { db.close() }
    }
}
