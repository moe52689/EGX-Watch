package app.egxwatch

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.egxwatch.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun versionOneUpgradePreservesSelectionsAndLatestValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("schema-v1.sql")
                .bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.forEach { old.execSQL(it) } }
            old.execSQL("INSERT INTO watchlists (id,name) VALUES (1,'My chosen shares')")
            old.execSQL("""INSERT INTO instruments (listId,id,ticker,name,type,currency,identitySource,verifiedAt,value,kind,timestamp,quoteSource,timestampBasis)
                VALUES (1,'EGX:CCAP','CCAP','Qalaa for Financial Investments','STOCK','EGP','Verified directory','2026-09-20','7.24','INDICATIVE','2026-09-20T10:00:00Z','EGXpilot','PROVIDER_SNAPSHOT')""")
            old.execSQL("""INSERT INTO settings VALUES (1,0,15,'1,2,3,4,7','00:00','00:00',0,'','','','System',1)""")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, WatchDatabase::class.java, name).addMigrations(WatchDatabase.MIGRATION_1_2, WatchDatabase.MIGRATION_2_3, ForwardMigrations.FROM_3, ForwardMigrations.FROM_4).build()
        try {
            WatchRepository(db).initialize()
            val row = db.dao().getInstruments().single()
            assertEquals("7.24", row.value)
            assertEquals("2026-09-20T10:00:00Z", row.timestamp)
            assertNull(row.baselineKey)
            assertEquals("My chosen shares", db.dao().getLists().single().name)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
