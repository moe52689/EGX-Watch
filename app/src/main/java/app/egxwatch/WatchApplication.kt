package app.egxwatch

import android.app.Application
import androidx.room.Room
import app.egxwatch.data.*
import app.egxwatch.monitor.*
import kotlinx.coroutines.*

class WatchApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, WatchDatabase::class.java, "egx-watch.db").build() }
    val repository by lazy { WatchRepository(database) }
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
