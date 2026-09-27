package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class GoldMarketRepository(private val db:WatchDatabase,private val analytics:AnalyticsRepository,private val providerFactory:((GoldConfig)->List<ProviderEndpoint>)?=null) {
    private val mutex=Mutex();private var key="";private var selected:MarketDataProvider?=null
    val refreshing=kotlinx.coroutines.flow.MutableStateFlow(false)
    val diagnostics=kotlinx.coroutines.flow.MutableStateFlow<List<ProviderDiagnostic>>(emptyList())
    var onFresh:(suspend(Quote)->Unit)?=null
    suspend fun check(background:Boolean):Boolean {
        if(!mutex.tryLock()) return false
        refreshing.value=true
        try { return performCheck(background) } finally { refreshing.value=false;mutex.unlock() }
    }
    private suspend fun performCheck(background:Boolean):Boolean {
        val config=db.forwardDao().goldConfig() ?: GoldConfig();val now=Instant.now()
        config.validate();val old=db.forwardDao().goldStatus() ?: GoldStatus()
        if(background && (!config.enabled || !GoldSessionManager(config).isOpen(now) || old.lastAttempt?.let { now.toEpochMilli()-it<config.interval*60000 }==true)) return false
        diagnostics.value=emptyList()
        db.forwardDao().save(old.copy(lastAttempt=now.toEpochMilli(),message="Checking global gold"))
        try {
            val configKey="${config.providerUrl}|${config.fallbackUrl}|${config.freeProvider}|${config.providerOrder}"
            if(selected==null || key!=configKey) {
                val providers=providerFactory?.invoke(config) ?: config.providerOrder.split(',').mapNotNull { entry -> when(entry) {
                    "primary" -> config.providerUrl.takeIf { it.isNotBlank() }?.let { ProviderEndpoint("gold:$it",GatewayProvider(it)) }
                    "fallback" -> config.fallbackUrl.takeIf { it.isNotBlank() }?.let { ProviderEndpoint("gold:$it",GatewayProvider(it)) }
                    "free" -> if(config.freeProvider) ProviderEndpoint("gold:gold-api.com",GoldMarketProvider()) else null
                    else -> null
                } }.distinctBy { it.id }
                require(providers.isNotEmpty()) { "Configure a gold provider" }
                selected=MarketDataRepository(providers,RoomHealthStore(db.engineDao()),diagnostic={ event->diagnostics.value=diagnostics.value.filterNot { it.provider==event.provider }+event });key=configKey
            }
            val prior=old.payload?.let { GatewayProvider.parseQuote(org.json.JSONObject(it)) }
            val quote=(selected as MarketDataRepository).quote(GlobalGold.instrument,prior?.timestamp);validateQuote(GlobalGold.instrument,quote)
            require((db.forwardDao().goldConfig() ?: GoldConfig())==config) { "Gold configuration changed during refresh" }
            require(prior==null || quote.timestamp>=prior.timestamp) { "Older gold observation rejected" }
            db.forwardDao().save(GoldStatus(lastAttempt=now.toEpochMilli(),lastSuccess=if(quote.notice==null) Instant.now().toEpochMilli() else old.lastSuccess,payload=EngineJson.quote(quote),message=quote.qualityWarning ?: quote.notice ?: "Provider observation received"))
            try { analytics.observe(GlobalGold.instrument,quote,selected!!) }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { db.forwardDao().save((db.forwardDao().goldStatus() ?: old).copy(message="Price saved; analysis unavailable")) }
            if(quote.freshness(now) in setOf(Freshness.LIVE,Freshness.DELAYED) && prior?.fingerprint()!=quote.fingerprint()) onFresh?.invoke(quote)
            return true
        } catch(e:CancellationException) { throw e } catch(_:Exception) {
            val message="Gold source unavailable; saved observation retained"
            if(old.message!=message) db.forwardDao().alert(CenterAlert("gold-system:${now.toEpochMilli()}","SYSTEM",GlobalGold.instrument.id,now.toEpochMilli(),now.toEpochMilli(),"Gold connection needs attention",message,null,null))
            db.forwardDao().save(old.copy(lastAttempt=now.toEpochMilli(),message=message));return false
        }
    }
}
