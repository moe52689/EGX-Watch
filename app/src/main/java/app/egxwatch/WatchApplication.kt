package app.egxwatch

import android.app.Application
import androidx.room.Room
import app.egxwatch.data.*
import app.egxwatch.monitor.*
import kotlinx.coroutines.*

class WatchApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, WatchDatabase::class.java, "egx-watch.db")
        .addMigrations(WatchDatabase.MIGRATION_1_2, WatchDatabase.MIGRATION_2_3).build() }
    val opportunityNotifier by lazy { OpportunityNotificationManager(this) }
    val analytics by lazy { AnalyticsRepository(database) { opportunityNotifier.send(it) } }
    val repository by lazy { WatchRepository(database, java.io.File(filesDir, "public-feeds")).apply { snapshotObserver = { i,q,p -> analytics.observe(i,q,p) } } }
    val notifier by lazy { AlertNotifier(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            repository.initialize()
            MonitorScheduler.apply(this@WatchApplication, repository.dao.getSettings() ?: Settings())
        }
    }
}
