package app.egxwatch.data

import androidx.room.withTransaction
import java.time.Instant

object AlertCenter {
 fun stock(a:Alert)=CenterAlert("price:${a.id}","STOCKS",a.instrumentId,Instant.parse(a.dataTimestamp).toEpochMilli(),Instant.parse(a.checkedAt).toEpochMilli(),"${a.ticker} · value change","${a.current} ${a.currency} · previous ${a.previous ?: "unknown"}\nChange ${a.absolute ?: "unknown"} (${a.percent ?: "unknown"}%)\n${a.kind} · ${a.source}",a.current,a.source)
 fun opportunity(a:OpportunityEvent)=CenterAlert("opportunity:${a.id}","STOCKS",a.instrumentId,GatewayProvider.parseQuote(org.json.JSONObject(a.quoteJson)).timestamp.toEpochMilli(),a.createdAt,"${a.ticker} · opportunity ${a.score}/100",EngineJson.analysis(a.resultJson).drivers.joinToString("\n"),GatewayProvider.parseQuote(org.json.JSONObject(a.quoteJson)).value.toPlainString(),GatewayProvider.parseQuote(org.json.JSONObject(a.quoteJson)).source)
 suspend fun importExisting(db:WatchDatabase,alerts:List<Alert>,events:List<OpportunityEvent>)=db.withTransaction {
  alerts.forEach { db.forwardDao().alert(stock(it)) };events.forEach { db.forwardDao().alert(opportunity(it)) };db.forwardDao().trimAlerts()
 }
}
