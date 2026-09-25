package app.egxwatch.data

import androidx.room.withTransaction
import app.egxwatch.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class WatchRepository(private val database: WatchDatabase, feedCacheDirectory: java.io.File? = null,
    private val providerFactory: ((Settings) -> MarketDataProvider)? = null) {
    val dao = database.dao()
    private val mutex = Mutex()
    private val checkMutex = Mutex()
    @Volatile private var config=EngineConfig()
    private var providerKey=""
    private var configuredProvider:MarketDataProvider=DirectoryProvider()
    var snapshotObserver:(suspend (Instrument,Quote,MarketDataProvider)->Unit)?=null
    fun networkChanged() { /* Persisted rate-limit cooldown must survive network changes. */ }
    @Synchronized fun provider(settings: Settings): MarketDataProvider {
        providerFactory?.let { return it(settings) }
        val urls=(listOf(settings.providerUrl)+config.urls()).filter { it.isNotBlank() }.distinct()
        val key=urls.joinToString("\n")
        if(key!=providerKey) {
            configuredProvider=if(urls.isEmpty()) DirectoryProvider() else MarketDataRepository(urls.map { ProviderEndpoint(it,GatewayProvider(it)) },RoomHealthStore(database.engineDao()))
            providerKey=key
        }
        return configuredProvider
    }
    suspend fun saveEngine(value:EngineConfig) {
        value.rules(); value.urls();value.calendar(dao.getSettings() ?: Settings())
        database.engineDao().save(value);config=value
    }
    suspend fun initialize() = mutex.withLock {
        config=database.engineDao().config() ?: EngineConfig()
        database.withTransaction {
            if (dao.getSettings() == null) {
                dao.save(Settings())
                dao.insertList(Watchlist(name = "My EGX watchlist"))
            }
            val identities = DirectoryProvider.instruments.associateBy { it.id }
            dao.getInstruments().forEach { row ->
                identities[row.id]?.takeIf { it.currency == row.currency && it.type.name == row.type }?.let {
                    if (row.ticker != it.ticker || row.name != it.name) dao.update(row.copy(ticker = it.ticker, name = it.name))
                }
            }
        }
    }
    suspend fun add(listId: Long, candidate: Instrument) = mutex.withLock {
        val settings = dao.getSettings() ?: Settings()
        val validated = provider(settings).resolve(candidate.id)
        require(validated.ticker == candidate.ticker && validated.type == candidate.type && validated.currency == candidate.currency) {
            "Instrument identity changed. Search again before adding."
        }
        kotlin.check(dao.add(TrackedInstrument.from(listId, validated)) != -1L) { "Already in this watchlist" }
    }
    suspend fun save(settings: Settings) = mutex.withLock {
        settings.policy()
        config.calendar(settings)
        if (settings.providerUrl.isNotBlank()) GatewayProvider.validateBaseUrl(settings.providerUrl)
        database.withTransaction {
            val old = dao.getSettings()
            if (old?.providerUrl != settings.providerUrl || old.freeFeeds != settings.freeFeeds) dao.invalidateBaselines()
            dao.save(settings)
        }
    }
    /** Serializes manual/background checks; records baseline and alert atomically. */
    suspend fun check(background: Boolean, onlyIds: Set<String>? = null, deliver: suspend (Alert) -> String): Int = checkMutex.withLock {
        val settings = dao.getSettings() ?: Settings()
        val policy = settings.policy()
        config=database.engineDao().config() ?: EngineConfig()
        if (background && (!settings.enabled || !EgxSessionManager(settings,config,database.forwardDao().preferences()?.egxOffHours==true).isOpen(Instant.now()))) return@withLock 0
        if(background && database.engineDao().status()?.lastAttempt?.let { Instant.now().toEpochMilli()-it < settings.interval*60000 }==true) return@withLock 0
        database.engineDao().let { d -> d.status((d.status() ?: EngineStatus()).copy(lastAttempt=Instant.now().toEpochMilli(),message="Checking configured sources")) }
        val provider = provider(settings)
        val count = java.util.concurrent.atomic.AtomicInteger()
        // One request/notification per instrument even when it belongs to several lists.
        val baselineKey = "${settings.providerUrl}|${settings.freeFeeds}|${config.fallbackUrls}"
        if (provider is FreePublicProvider) provider.beginCheck()
        try {
        val slots = Semaphore(4)
        coroutineScope {
        dao.getInstruments().filter { onlyIds == null || it.id in onlyIds }.groupBy { it.id }.values.map { tracked -> async { slots.withPermit {
            val original = tracked.first()
            try {
                val quote = provider.quote(original.instrument())
                validateQuote(original.instrument(), quote)
                val now = Instant.now().toString()
                val pending = mutableListOf<Alert>()
                database.withTransaction {
                    val active = dao.getSettings() ?: Settings()
                    require("${active.providerUrl}|${active.freeFeeds}|${(database.engineDao().config() ?: EngineConfig()).fallbackUrls}" == baselineKey) { "Provider changed during refresh. Please check again." }
                    for (row in tracked) {
                        val current = dao.getInstrument(row.listId, row.id) ?: continue
                        val oldTime = current.timestamp?.let(Instant::parse)
                        require(oldTime == null || quote.timestamp >= oldTime) { "Provider returned older data; keeping last value" }
                        val sameSeries = current.baselineKey == baselineKey && current.kind == quote.kind.name &&
                            current.quoteSource == quote.source && current.timestampBasis == quote.timestampBasis.name
                        require(!sameSeries || quote.timestampBasis == TimestampBasis.VALUATION_DATE ||
                            oldTime != quote.timestamp || current.value?.toBigDecimal()?.compareTo(quote.value) == 0) {
                            "Provider changed a value without updating its timestamp"
                        }
                        val previous = current.value?.takeIf { sameSeries }?.toBigDecimal()
                        val rule = policy.copy(absoluteThreshold = current.absoluteThreshold?.toBigDecimal() ?: policy.absoluteThreshold,
                            percentThreshold = current.percentThreshold?.toBigDecimal() ?: policy.percentThreshold)
                        if (quote.notice == null && shouldNotify(quote.value, previous, rule)) {
                            val change = previous?.let { change(quote.value, it) }
                            pending += Alert(instrumentId = row.id, ticker = row.ticker, name = row.name,
                                current = quote.value.display(), previous = previous?.display(), absolute = change?.absolute?.display(),
                                percent = change?.percent?.display(), currency = quote.currency, kind = quote.kind.name,
                                dataTimestamp = quote.timestamp.toString(), checkedAt = now, source = quote.source, timestampBasis = quote.timestampBasis.name)
                        }
                        val advanced = oldTime == null || quote.timestamp > oldTime || current.value?.toBigDecimal()?.compareTo(quote.value) != 0
                        dao.update(current.copy(value = quote.value.display(), previous = if (!sameSeries || advanced) previous?.display() else current.previous,
                            kind = quote.kind.name, timestamp = quote.timestamp.toString(), quoteSource = quote.source,
                            delayMinutes = quote.delayMinutes, lastCheck = now, error = quote.notice,
                            timestampBasis = quote.timestampBasis.name, baselineKey = baselineKey))
                    }
                    pending.firstOrNull()?.let { event ->
                        val id = dao.insertAlert(event)
                        database.forwardDao().alert(AlertCenter.stock(event.copy(id=id)))
                        pending.clear(); pending += event.copy(id = id)
                    }
                    dao.trimHistory()
                }
                pending.firstOrNull()?.let { alert ->
                    val delivery = try { deliver(alert) } catch (e: SecurityException) { "Blocked by Android permission" }
                    dao.delivery(alert.id, delivery)
                }
                if (quote.notice == null) count.incrementAndGet()
                snapshotObserver?.invoke(original.instrument(),quote,provider)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                database.withTransaction {
                    tracked.forEach { row -> dao.getInstrument(row.listId, row.id)?.let {
                        dao.update(it.copy(lastCheck = Instant.now().toString(), error = e.message?.take(180) ?: "Data unavailable"))
                    } }
                }
            }
        } } }.awaitAll()
        }
        database.engineDao().let { d -> database.withTransaction {
            val previous=d.status() ?: EngineStatus()
            d.status(previous.copy(lastSuccess=if(count.get()>0) Instant.now().toEpochMilli() else previous.lastSuccess,
                message="${count.get()} instruments checked; saved data retained on failure"))
            d.pruneProviders((listOf(settings.providerUrl)+config.urls()).filter { it.isNotBlank() })
        } }
        count.get()
        } finally { if (provider is FreePublicProvider) provider.finishCheck() }
    }
}
