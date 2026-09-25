package app.egxwatch

import android.app.Application
import androidx.room.Room
import app.egxwatch.data.*
import app.egxwatch.monitor.*
import kotlinx.coroutines.*

class WatchApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, WatchDatabase::class.java, "egx-watch.db")
        .addMigrations(WatchDatabase.MIGRATION_1_2, WatchDatabase.MIGRATION_2_3, ForwardMigrations.FROM_3, ForwardMigrations.FROM_4).build() }
    val opportunityNotifier by lazy { OpportunityNotificationManager(this) }
    val analytics by lazy { AnalyticsRepository(database) { opportunityNotifier.send(it) } }
    val repository by lazy { WatchRepository(database, java.io.File(filesDir, "public-feeds")).apply { snapshotObserver = { i,q,p -> analytics.observe(i,q,p) } } }
    val goldAlerts by lazy { GoldAlertManager(database) { GoldNotifier(this).send(it) } }
    val goldRepository by lazy { GoldMarketRepository(database, analytics).apply { onFresh={goldAlerts.onFresh(it)} } }
    val notifier by lazy { AlertNotifier(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            repository.initialize()
            ObservationRepository(database).compact()
            GoldScheduler.apply(this@WatchApplication, database.forwardDao().goldConfig() ?: GoldConfig())
            MonitorScheduler.apply(this@WatchApplication, repository.dao.getSettings() ?: Settings())
        }
    }
}
