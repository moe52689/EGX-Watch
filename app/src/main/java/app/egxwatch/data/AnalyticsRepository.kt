package app.egxwatch.data

import androidx.room.withTransaction
import app.egxwatch.domain.*
import kotlinx.coroutines.CancellationException
import java.time.*
import org.json.JSONObject

class AnalyticsRepository(private val db:WatchDatabase,private val deliver:suspend(OpportunityEvent)->String) {
    val dao=db.engineDao()
    private val engine=AdaptiveQuantitativeEngine()
    private val observations=ObservationRepository(db)
    suspend fun observe(instrument:Instrument,quote:Quote,provider:MarketDataProvider) {
        val now=Instant.now();val fingerprint=quote.fingerprint()
        val old=dao.analysis(instrument.id)
        if(old?.fingerprint==fingerprint) return
        val settings=db.dao().getSettings() ?: Settings()
        observations.collect(instrument,quote,now,if(instrument.id==GlobalGold.instrument.id) (db.forwardDao().goldConfig() ?: GoldConfig()).interval else settings.interval)
        val key=ObservationRepository.seriesKey(instrument,quote)
        val series=observations.dao.series(key)
        val sessions=observations.dao.sessions(key).reversed()
        val config=dao.config() ?: EngineConfig()
        val dates=sessions.map { LocalDate.parse(it.sessionDate) }
        val expectedSessions=if(dates.isEmpty()) 0 else if(quote.kind==DataKind.NAV || instrument.id==GlobalGold.instrument.id) dates.size else {
            val calendar=config.calendar(settings)
            generateSequence(dates.first()) { it.plusDays(1) }.takeWhile { it<=dates.last() }.count { date ->
                (0..23).any { hour -> calendar.isOpen(date.atTime(hour,0).atZone(ZoneId.of("Africa/Cairo")).toInstant()) }
            }.coerceAtLeast(dates.size)
        }
        val quality=SampleQuality(sessions.sumOf { it.validSamples },sessions.size,sessions.sumOf { it.occupiedSlots },sessions.sumOf { it.expectedSlots },expectedSessions,
            if(sessions.isEmpty()) 0.0 else sessions.sumOf { it.validSamples }.toDouble()/sessions.sumOf { it.samples }.coerceAtLeast(1))
        val quoteDate=quote.timestamp.atZone(ZoneId.of(if(instrument.id==GlobalGold.instrument.id) "America/New_York" else "Africa/Cairo")).toLocalDate()
        val closed=sessions.filter { quote.kind==DataKind.NAV || LocalDate.parse(it.sessionDate)<quoteDate }
            .map { Candle(LocalDate.parse(it.sessionDate),it.close.toDouble(),it.open.toDouble(),it.high.toDouble(),it.low.toDouble()) }
        val recent=observations.dao.recent(key).reversed().filter { it.freshness in setOf("LIVE","DELAYED") }
            .map { LocalSample(Instant.ofEpochMilli(it.providerTime),it.price.toDouble(),it.volume) }
        val analysis=if(instrument.id==GlobalGold.instrument.id) GoldAnalyticsEngine().analyze(quote,closed,recent,quality,now,series?.startedAt) else engine.analyze(instrument,quote,closed,recent,quality,now,series?.startedAt)
        var event:OpportunityEvent?=null
        db.withTransaction {
            if(instrument.id!=GlobalGold.instrument.id && db.dao().getInstruments().none { it.id==instrument.id && it.timestamp==quote.timestamp.toString() && it.quoteSource==quote.source }) return@withTransaction
            val settings=db.dao().getSettings() ?: Settings();val config=dao.config() ?: EngineConfig()
            dao.snapshot(MarketSnapshot(fingerprint,instrument.id,now.toEpochMilli(),JSONObject(EngineJson.quote(quote)).put("ticker",instrument.ticker).put("name",instrument.name).toString()))

            dao.analysis(AnalysisResult(instrument.id,fingerprint,now.toEpochMilli(),instrument.ticker,instrument.name,EngineJson.quote(quote),EngineJson.analysis(analysis)))
            val previous=dao.alertState(instrument.id)?.let { LastOpportunity(it.fingerprint,it.score,OpportunityState.valueOf(it.state),Instant.ofEpochMilli(it.createdAt)) }
            val day=now.atZone(ZoneId.of("Africa/Cairo")).toLocalDate().atStartOfDay(ZoneId.of("Africa/Cairo")).toInstant().toEpochMilli()
            // Manual checks outside the session may update details, but never emit opportunities.
            if(instrument.id!=GlobalGold.instrument.id && config.calendar(settings).isOpen(now) && shouldNotifyOpportunity(analysis,fingerprint,previous,dao.dailyCount(day),now,config.rules(),old?.analysis()?.state)) {
                val pending=OpportunityEvent(instrumentId=instrument.id,fingerprint=fingerprint,createdAt=now.toEpochMilli(),score=analysis.score!!,
                    state=analysis.state.name,ticker=instrument.ticker,name=instrument.name,quoteJson=EngineJson.quote(quote),resultJson=EngineJson.analysis(analysis))
                event=pending.copy(id=dao.event(pending))
                db.forwardDao().alert(AlertCenter.opportunity(event!!))
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
