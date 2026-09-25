package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class GoldMarketRepository(private val db:WatchDatabase,private val analytics:AnalyticsRepository) {
    private val mutex=Mutex();private var key="";private var selected:MarketDataProvider?=null
    var onFresh:(suspend(Quote)->Unit)?=null
    suspend fun check(background:Boolean):Boolean=mutex.withLock {
        val config=db.forwardDao().goldConfig() ?: GoldConfig();val now=Instant.now()
        config.validate();val old=db.forwardDao().goldStatus() ?: GoldStatus()
        if(background && (!config.enabled || !GoldSessionManager(config).isOpen(now) || old.lastAttempt?.let { now.toEpochMilli()-it<config.interval*60000 }==true)) return@withLock false
        db.forwardDao().save(old.copy(lastAttempt=now.toEpochMilli(),message="Checking global gold"))
        try {
            val configKey="${config.providerUrl}|${config.fallbackUrl}|${config.freeProvider}"
            if(selected==null || key!=configKey) {
                val providers=buildList {
                    if(config.providerUrl.isNotBlank()) add(ProviderEndpoint("gold:${config.providerUrl}",GatewayProvider(config.providerUrl)))
                    if(config.freeProvider) add(ProviderEndpoint("gold:gold-api.com",GoldMarketProvider()))
                    if(config.fallbackUrl.isNotBlank()) add(ProviderEndpoint("gold:${config.fallbackUrl}",GatewayProvider(config.fallbackUrl)))
                }
                require(providers.isNotEmpty()) { "Configure a gold provider" }
                selected=MarketDataRepository(providers,RoomHealthStore(db.engineDao()));key=configKey
            }
            val quote=selected!!.quote(GlobalGold.instrument);validateQuote(GlobalGold.instrument,quote)
            require((db.forwardDao().goldConfig() ?: GoldConfig())==config) { "Gold configuration changed during refresh" }
            val prior=old.payload?.let { GatewayProvider.parseQuote(org.json.JSONObject(it)) }
            require(prior==null || quote.timestamp>=prior.timestamp) { "Older gold observation rejected" }
            db.forwardDao().save(GoldStatus(lastAttempt=now.toEpochMilli(),lastSuccess=if(quote.notice==null) now.toEpochMilli() else old.lastSuccess,payload=EngineJson.quote(quote),message=quote.notice ?: "Provider observation received"))
            try { analytics.observe(GlobalGold.instrument,quote,selected!!) }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { db.forwardDao().save((db.forwardDao().goldStatus() ?: old).copy(message="Price saved; analysis unavailable")) }
            if(quote.freshness(now) in setOf(Freshness.LIVE,Freshness.DELAYED) && prior?.fingerprint()!=quote.fingerprint()) onFresh?.invoke(quote)
            true
        } catch(e:CancellationException) { throw e } catch(_:Exception) {
            val message="Gold source unavailable; saved observation retained"
            if(old.message!=message) db.forwardDao().alert(CenterAlert("gold-system:${now.toEpochMilli()}","SYSTEM",GlobalGold.instrument.id,now.toEpochMilli(),now.toEpochMilli(),"Gold connection needs attention",message,null,null))
            db.forwardDao().save(old.copy(lastAttempt=now.toEpochMilli(),message=message));false
        }
    }
}
