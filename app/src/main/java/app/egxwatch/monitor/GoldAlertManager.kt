package app.egxwatch.monitor

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import app.egxwatch.*
import app.egxwatch.data.*
import app.egxwatch.domain.*
import java.time.Instant

class GoldAlertManager(private val db:WatchDatabase,private val deliver:(CenterAlert)->Unit) {
 suspend fun onFresh(q:Quote,now:Instant=Instant.now()) {
  val key=ObservationRepository.seriesKey(GlobalGold.instrument,q)
  val samples=db.observationDao().recent(key,1024).filter { it.freshness in setOf("LIVE","DELAYED") }.map { LocalSample(Instant.ofEpochMilli(it.providerTime),it.price.toDouble()) }
  val events=mutableListOf<CenterAlert>()
  db.withTransaction {
   db.forwardDao().rules().forEach { rule ->
    val previous=db.forwardDao().ruleState(rule.id)
    val decision=GoldAlertEngine.evaluate(rule,previous,q,samples,now) ?: return@forEach
    val event=CenterAlert("gold:${rule.id}:${q.fingerprint()}","GOLD",q.instrumentId,q.timestamp.toEpochMilli(),now.toEpochMilli(),"Gold · ${rule.type.replace('_',' ')}",decision.detail,q.value.display(),q.source)
    if(decision.notify && db.forwardDao().alert(event)!=-1L) events+=event
    db.forwardDao().save(GoldRuleState(rule.id,q.fingerprint(),decision.condition,if(decision.notify) now.toEpochMilli() else previous?.lastNotifiedAt))
   }
   db.forwardDao().trimAlerts()
  }
  events.forEach { try { deliver(it) } catch(_:SecurityException) { /* History remains available when permission is denied. */ } }
 }
}
class GoldNotifier(private val context:Context) {
 fun send(event:CenterAlert) {
  val system=context.getSystemService(NotificationManager::class.java)
  system.createNotificationChannel(NotificationChannel("gold_alerts","Global gold alerts",NotificationManager.IMPORTANCE_DEFAULT))
  if(android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return
  if(!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
  val intent=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).putExtra("gold",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  NotificationManagerCompat.from(context).notify("gold",event.id.hashCode(),NotificationCompat.Builder(context,"gold_alerts")
   .setSmallIcon(R.drawable.ic_notification).setContentTitle(event.title).setContentText(event.body)
   .setStyle(NotificationCompat.BigTextStyle().bigText(event.body+"\nAnalytical alert, not financial advice."))
   .setContentIntent(intent).setAutoCancel(true).build())
 }
}
