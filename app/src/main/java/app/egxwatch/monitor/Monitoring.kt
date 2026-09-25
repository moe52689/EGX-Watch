package app.egxwatch.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import app.egxwatch.*
import app.egxwatch.R
import app.egxwatch.data.*
import java.util.concurrent.TimeUnit

class AlertNotifier(private val context: Context) {
    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("market_changes", "Price and NAV changes", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun send(alert: Alert): String {
        if ((Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return "Blocked by notification permission"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel("market_changes").importance == NotificationManager.IMPORTANCE_NONE) return "Notification channel disabled"
        val text = "${if (alert.kind == "NAV") "NAV" else "Price"}: ${alert.current} ${alert.currency}\n" +
            "Previous: ${alert.previous ?: "Not yet available"}\nChange: ${alert.absolute ?: "—"} ${alert.currency} (${alert.percent ?: "N/A"}%)\n" +
            "${alert.kind} • ${when (alert.timestampBasis) {
                "VALUATION_DATE" -> "NAV date: " + java.time.Instant.parse(alert.dataTimestamp).atZone(java.time.ZoneId.of("Africa/Cairo")).toLocalDate() + " (time not published)"
                "PROVIDER_SNAPSHOT" -> "Snapshot: ${alert.dataTimestamp}; exchange time unknown"
                "RETRIEVAL_TIME" -> "Retrieved: ${alert.dataTimestamp}; trade time not supplied"
                else -> "Data: ${alert.dataTimestamp}"
            }}\nSource: ${alert.source}"
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("history", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify((alert.id % Int.MAX_VALUE).toInt(),
            NotificationCompat.Builder(context, "market_changes").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${alert.ticker} · ${alert.name}").setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(intent).setAutoCancel(true).build())
        return "Sent to Android"
    }
}
object MonitorScheduler {
    fun apply(context: Context, settings: Settings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.enabled) { manager.cancelUniqueWork("market-monitor"); return }
        val request = PeriodicWorkRequestBuilder<MonitorWorker>(settings.policy().intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork("market-monitor", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WatchApplication
        app.repository.initialize()
        val count = app.repository.check(background = true) { app.notifier.send(it) }
        ObservationRepository(app.database).compact()
        val settings = app.repository.dao.getSettings() ?: Settings()
        val errors = app.repository.dao.getInstruments().mapNotNull { it.error }
        if (count == 0 && runAttemptCount < 2 && settings.enabled && (app.database.engineDao().config() ?: EngineConfig()).calendar(settings).isOpen(java.time.Instant.now()) &&
            errors.any { it.contains("resolve host", true) || it.contains("timeout", true) || it.contains("HTTP 5") || it.contains("connect", true) }) return Result.retry()
        // Per-instrument failures are persisted and retried at the next scheduled interval.
        return Result.success()
    }
}
