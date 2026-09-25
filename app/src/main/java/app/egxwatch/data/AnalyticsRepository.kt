package app.egxwatch.data

import androidx.room.withTransaction
import app.egxwatch.domain.*
import kotlinx.coroutines.CancellationException
import java.time.*
import org.json.JSONObject

class AnalyticsRepository(private val db:WatchDatabase,private val deliver:suspend(OpportunityEvent)->String) {
    val dao=db.engineDao()
    private val engine=QuantitativeEngine()
    suspend fun observe(instrument:Instrument,quote:Quote,provider:MarketDataProvider) {
        val now=Instant.now();val fingerprint=quote.fingerprint()
        val old=dao.analysis(instrument.id)
        if(old?.fingerprint==fingerprint) return
        var analysis=StructuredAnalysis(if(quote.freshness(now) in setOf(Freshness.STALE,Freshness.UNAVAILABLE)) "STALE OR UNVERIFIED DATA" else "INSUFFICIENT DATA · historical API not configured")
        var history:PriceHistory?=null
        if(analysis.status.startsWith("INSUFFICIENT") && provider is HistoricalMarketDataProvider) {
            try {
                val cached=dao.history(instrument.id)?.takeIf { now.toEpochMilli()-it.fetchedAt<60*60*1000 }
                    ?.let { EngineJson.history(JSONObject(it.payload)) }?.takeIf { it.source==quote.source && it.kind==quote.kind }
                history=cached ?: provider.history(instrument)
                analysis=engine.analyze(instrument,quote,history,now)
            } catch(e:CancellationException) { throw e } catch(_:Exception) { analysis=StructuredAnalysis("INSUFFICIENT DATA · history unavailable or invalid") }
        }
        var event:OpportunityEvent?=null
        db.withTransaction {
            if(db.dao().getInstruments().none { it.id==instrument.id && it.timestamp==quote.timestamp.toString() && it.quoteSource==quote.source }) return@withTransaction
            val settings=db.dao().getSettings() ?: Settings();val config=dao.config() ?: EngineConfig()
            dao.snapshot(MarketSnapshot(fingerprint,instrument.id,now.toEpochMilli(),JSONObject(EngineJson.quote(quote)).put("ticker",instrument.ticker).put("name",instrument.name).toString()))
            history?.takeIf { analysis.status=="READY" }?.let { dao.history(HistoricalPrice(instrument.id,now.toEpochMilli(),EngineJson.history(it))) }
            dao.analysis(AnalysisResult(instrument.id,fingerprint,now.toEpochMilli(),instrument.ticker,instrument.name,EngineJson.quote(quote),EngineJson.analysis(analysis)))
            val previous=dao.alertState(instrument.id)?.let { LastOpportunity(it.fingerprint,it.score,OpportunityState.valueOf(it.state),Instant.ofEpochMilli(it.createdAt)) }
            val day=now.atZone(ZoneId.of("Africa/Cairo")).toLocalDate().atStartOfDay(ZoneId.of("Africa/Cairo")).toInstant().toEpochMilli()
            // Manual checks outside the session may update details, but never emit opportunities.
            if(config.calendar(settings).isOpen(now) && shouldNotifyOpportunity(analysis,fingerprint,previous,dao.dailyCount(day),now,config.rules(),old?.analysis()?.state)) {
                val pending=OpportunityEvent(instrumentId=instrument.id,fingerprint=fingerprint,createdAt=now.toEpochMilli(),score=analysis.score!!,
                    state=analysis.state.name,ticker=instrument.ticker,name=instrument.name,quoteJson=EngineJson.quote(quote),resultJson=EngineJson.analysis(analysis))
                event=pending.copy(id=dao.event(pending))
                dao.alertState(OpportunityAlertState(instrument.id,fingerprint,analysis.score!!,analysis.state.name,now.toEpochMilli()))
            }
            val status=dao.status() ?: EngineStatus()
            dao.status(status.copy(lastFresh=if(quote.freshness(now) in setOf(Freshness.LIVE,Freshness.DELAYED)) now.toEpochMilli() else status.lastFresh))
            dao.trimSnapshots();dao.trimEvents();dao.pruneHistory();dao.pruneAnalyses();dao.pruneSnapshots()
        }
        event?.let { e ->
            val result=try { deliver(e) } catch(_:SecurityException) { "Blocked by Android" }
            dao.delivery(e.id,result)
        }
    }
}
