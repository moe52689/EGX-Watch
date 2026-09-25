package app.egxwatch.monitor

import android.content.Context
import androidx.work.*
import app.egxwatch.WatchApplication
import app.egxwatch.data.GoldConfig
import app.egxwatch.data.ObservationRepository
import java.util.concurrent.TimeUnit

object GoldScheduler {
    fun apply(context:Context,config:GoldConfig) {
        config.validate()
        val manager=WorkManager.getInstance(context)
        if(!config.enabled) { manager.cancelUniqueWork("gold-monitor");return }
        manager.enqueueUniquePeriodicWork("gold-monitor",ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<GoldWorker>(config.interval,TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,1,TimeUnit.MINUTES).build())
    }
}
class GoldWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as WatchApplication
        app.goldRepository.check(true)
        ObservationRepository(app.database).compact()
        return Result.success()
    }
}
