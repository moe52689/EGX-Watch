package app.egxwatch.monitor

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.egxwatch.*
import app.egxwatch.data.*

class OpportunityNotificationManager(private val context:Context) {
    init { context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel("opportunities","Analytical opportunities",NotificationManager.IMPORTANCE_DEFAULT)) }
    fun send(event:OpportunityEvent):String {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return "Blocked by notification permission"
        val manager=NotificationManagerCompat.from(context)
        if(!manager.areNotificationsEnabled()) return "Blocked by notification permission"
        if(context.getSystemService(NotificationManager::class.java).getNotificationChannel("opportunities").importance==NotificationManager.IMPORTANCE_NONE) return "Channel disabled"
        val quote=GatewayProvider.parseQuote(org.json.JSONObject(event.quoteJson));val analysis=EngineJson.analysis(event.resultJson)
        val percent=quote.fields.previousClose?.takeIf { it.signum()>0 }?.let { app.egxwatch.domain.change(quote.value,it).percent?.toPlainString() }
        val text="Score ${event.score}/100 · data confidence ${(analysis.confidence*100).toInt()}%\n"+
            "${quote.value} ${quote.currency} · ${quote.kind}\nDaily change: ${percent?.let { "$it%" } ?: "Unavailable"}\n"+
            analysis.drivers.take(3).joinToString("\n")+"\nRisk: ${analysis.risks.firstOrNull() ?: "Uncertain market outcomes"}\n${quote.source} · ${quote.timestamp}\nTap for analysis; not financial advice."
        val intent=Intent(context,MainActivity::class.java).putExtra("analysisId",event.instrumentId)
            .setData(android.net.Uri.parse("egxwatch://analysis/${event.id}"))
        val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify("opportunity",(event.id%Int.MAX_VALUE).toInt(),NotificationCompat.Builder(context,"opportunities")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Potential opportunity: ${event.ticker} · ${event.name}")
            .setContentText("Score ${event.score}/100 · tap for drivers and risks").setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).build())
        return "Sent to Android"
    }
}
