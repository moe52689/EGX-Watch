package app.egxwatch.data

import androidx.room.withTransaction
import app.egxwatch.domain.*
import java.time.*
import java.security.MessageDigest

class ObservationRepository(private val db:WatchDatabase) {
    val dao=db.observationDao()
    companion object {
        fun seriesKey(i:Instrument,q:Quote)=MessageDigest.getInstance("SHA-256").digest(
            listOf(i.id,q.currency,q.source,q.kind.name,q.timestampBasis.name).joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }
    }
    suspend fun collect(instrument:Instrument,quote:Quote,receivedAt:Instant=Instant.now(),intervalMinutes:Long=15):Boolean {
        validateQuote(instrument,quote,receivedAt)
        val key=seriesKey(instrument,quote);val stamp=quote.timestamp.toEpochMilli();val fingerprint=quote.fingerprint()
        val quality=quote.freshness(receivedAt)
        val date=quote.timestamp.atZone(ZoneId.of(if(instrument.id=="GLOBAL:XAUUSD") "America/New_York" else "Africa/Cairo")).toLocalDate().toString()
        val interval=intervalMinutes.coerceAtLeast(15)*60000
        val slot=if(quote.kind==DataKind.NAV) LocalDate.parse(date).toEpochDay() else stamp/interval
        return db.withTransaction {
            val series=dao.series(key)
            val correction=series!=null && quote.kind==DataKind.NAV && stamp==series.lastProviderTime && fingerprint!=series.lastFingerprint
            // A revised NAV replaces the valuation-date observation; it never adds a sample.
            if(series!=null && (fingerprint==series.lastFingerprint || stamp<series.lastProviderTime || (stamp==series.lastProviderTime && !correction))) return@withTransaction false
            val f=quote.fields
            val value=MarketObservation(fingerprint,instrument.id,instrument.ticker,instrument.name,key,stamp,receivedAt.toEpochMilli(),date,
                quote.value.display(),quote.currency,quote.source,quality.name,quote.kind.name,quote.timestampBasis.name,
                f.open?.display(),f.high?.display(),f.low?.display(),f.previousClose?.display(),f.volume,f.bid?.display(),f.ask?.display(),
                f.previousClose?.takeIf { it.signum()>0 }?.let { change(quote.value,it).percent?.display() },EngineJson.quote(quote))
            if(correction) dao.deleteObservation(series!!.lastFingerprint)
            if(dao.insert(value)==-1L) return@withTransaction false
            dao.save(CollectionSeries(key,instrument.id,series?.startedAt ?: receivedAt.toEpochMilli(),receivedAt.toEpochMilli(),stamp,fingerprint,(series?.observations ?: 0)+if(correction) 0 else 1))
            val valid=quality in setOf(Freshness.LIVE,Freshness.DELAYED)
            // Only usable observations enter analytical aggregates; raw provenance remains available.
            if(valid) {
                val old=dao.session(key,date)
                dao.save(if(old==null || correction) ObservedSession(key,date,instrument.id,stamp,stamp,value.price,value.price,value.price,value.price,1,1,1,1,f.volume,f.volume,slot)
                    else old.copy(lastTime=stamp,high=maxOf(old.high.toBigDecimal(),quote.value).display(),low=minOf(old.low.toBigDecimal(),quote.value).display(),close=value.price,
                        samples=old.samples+1,validSamples=old.validSamples+1,occupiedSlots=old.occupiedSlots+if(slot>old.lastSlot) 1 else 0,
                        expectedSlots=old.expectedSlots+(slot-old.lastSlot).coerceAtLeast(0),lastVolume=f.volume,lastSlot=slot))
            }
            true
        }
    }
    suspend fun compact(now:Instant=Instant.now())=db.withTransaction { val cutoff=now.minusSeconds(90L*86400).toEpochMilli();dao.compact(cutoff);dao.compactUnusable(cutoff) }
}
