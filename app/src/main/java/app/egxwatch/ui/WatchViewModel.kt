package app.egxwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.egxwatch.WatchApplication
import app.egxwatch.data.*
import app.egxwatch.domain.*
import app.egxwatch.monitor.GoldScheduler
import app.egxwatch.monitor.MonitorScheduler
import app.egxwatch.monitor.connectivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WatchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WatchApplication
    private val repository = app.repository
    val preferences=app.database.forwardDao().preferencesFlow().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    fun beginCollection()=run { val old=app.database.forwardDao().preferences() ?: ForwardPreferences();app.database.forwardDao().save(old.copy(onboardingSeen=true)) }
    fun offHours(value:Boolean)=run { val old=app.database.forwardDao().preferences() ?: ForwardPreferences();app.database.forwardDao().save(old.copy(egxOffHours=value)) }
    val storageStats=app.database.observationDao().storageStats().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),HistoryStats(0,null,null))
    fun storageBytes():Long { val file=app.getDatabasePath("egx-watch.db");return file.length()+java.io.File(file.path+"-wal").length()+java.io.File(file.path+"-shm").length() }
    fun eraseHistory(id:String?)=run { StorageRepository(app.database).erase(id);message.value="Collected history deleted; analytics will rebuild from new observations" }
    fun exportHistory(uri:android.net.Uri,password:CharArray)=run {
        try { withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri,"w")!!.use { StorageRepository(app.database).export(it,password) } };message.value="Encrypted market archive exported" }
        catch(e:Exception) { runCatching { android.provider.DocumentsContract.deleteDocument(app.contentResolver,uri) };throw e }
        finally { password.fill('\u0000') }
    }
    val centerAlerts=app.database.forwardDao().alerts().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun markRead(id:String)=run { app.database.forwardDao().read(id,true) }
    fun dismissAlert(id:String)=run { app.database.forwardDao().dismiss(id) }
    fun chartEvents(id:String)=app.database.forwardDao().chartEvents(id,0)
    val goldRules=app.database.forwardDao().rulesFlow().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun addGoldRule(rule:GoldRule)=run { GoldAlertEngine.validate(rule);app.database.forwardDao().save(rule) }
    fun deleteGoldRule(rule:GoldRule)=run { app.database.forwardDao().delete(rule) }
    val goldConfig=app.database.forwardDao().goldConfigFlow().map { it ?: GoldConfig() }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),GoldConfig())
    val goldStatus=app.database.forwardDao().goldStatusFlow().map { it ?: GoldStatus() }.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),GoldStatus())
    val goldBusy=MutableStateFlow(false)
    fun checkGold(background:Boolean=false)=run { if(goldBusy.value) return@run;goldBusy.value=true
        try { app.goldRepository.check(background) } finally { goldBusy.value=false } }
    fun saveGold(value:GoldConfig)=run { value.validate();app.database.forwardDao().save(value);GoldScheduler.apply(app,value);message.value="Gold monitoring saved" }
    val collections = app.database.observationDao().seriesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun latestObservation(id:String)=app.database.observationDao().latestFlow(id)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun points(id:String,range:LocalChartRange):Flow<List<ChartPoint>> = app.database.observationDao().seriesFlow()
        .map { rows -> rows.firstOrNull { it.instrumentId==id } }.distinctUntilChanged().flatMapLatest { series ->
            if(series==null) flowOf(emptyList()) else {
                val end=maxOf(System.currentTimeMillis(),series.lastProviderTime)
                val zone=java.time.ZoneId.of(if(id=="GLOBAL:XAUUSD") "America/New_York" else "Africa/Cairo")
                val start=maxOf(range.start(java.time.Instant.ofEpochMilli(end),zone),series.startedAt-4*86400000L)
                if(end-start>90L*86400000) app.database.observationDao().sessionChart(series.seriesKey,start,end).map { rows ->
                    rows.chunked(maxOf(1,(rows.size+499)/500)).map { group -> group.last().let { ChartPoint(it.lastTime,it.close.toDouble(),group.maxOf { r->r.high.toDouble() },group.minOf { r->r.low.toDouble() },group.sumOf { r->r.samples }) } }
                } else app.database.observationDao().chart(series.seriesKey,start,end,maxOf(60000L,(end-start)/500))
            }
        }.flowOn(Dispatchers.Default)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun sessionBars(id:String):Flow<List<ObservedSession>> = app.database.observationDao().seriesFlow().map { it.firstOrNull { s->s.instrumentId==id } }
        .flatMapLatest { s->if(s==null) flowOf(emptyList()) else app.database.observationDao().sessionChart(s.seriesKey,0,Long.MAX_VALUE) }
    fun volumePoints(id:String):Flow<List<ChartPoint>> = app.database.observationDao().seriesFlow().map { rows ->
        rows.firstOrNull { it.instrumentId==id }?.let { s->app.database.observationDao().recent(s.seriesKey,96).reversed().mapNotNull { o->o.volume?.let { ChartPoint(o.providerTime,it.toDouble(),it.toDouble(),it.toDouble(),1) } } } ?: emptyList()
    }.flowOn(Dispatchers.Default)
    val analyses = app.database.engineDao().analyses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val opportunities = app.database.engineDao().events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val engineConfig = app.database.engineDao().configFlow().map { it ?: EngineConfig() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineConfig())
    val engineStatus = app.database.engineDao().statusFlow().map { it ?: EngineStatus() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineStatus())
    val providerHealth = app.database.engineDao().healthFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveEngine(value:EngineConfig) = run { repository.saveEngine(value); message.value="Analytics and calendar settings saved" }
    fun autoCheck() = run { repository.check(true) { app.notifier.send(it) } }
    val lists = repository.dao.lists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val instruments = repository.dao.instruments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repository.dao.settings().map { it ?: Settings() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
    val alerts = repository.dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val results = MutableStateFlow<List<Instrument>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val searching = MutableStateFlow(false)
    val online = connectivity(application).map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val refreshingIds = MutableStateFlow<Set<String>>(emptySet())
    fun reconnected() { repository.networkChanged(); autoCheck() }
    fun retry(id: String) = run {
        if (id in refreshingIds.value) return@run
        refreshingIds.value += id
        try { repository.check(false, setOf(id)) { app.notifier.send(it) } }
        finally { refreshingIds.value -= id }
    }
    val directoryStatus = MutableStateFlow("Saved directory · choose what appears in your watchlist")
    val refreshingDirectory = MutableStateFlow(false)
    val connectionReport = MutableStateFlow<String?>(null)
    val testingConnection = MutableStateFlow(false)
    val market = MutableStateFlow(MarketStatus("Unknown", "Exchange status has not been checked.", null))
    private var searchJob: Job? = null
    private var currentQuery = ""
    init { run { repository.initialize();if(app.database.forwardDao().preferences()==null) app.database.forwardDao().save(ForwardPreferences()); AlertCenter.importExisting(app.database,repository.dao.history().first(),app.database.engineDao().events().first());autoCheck() } }
    private fun run(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = e.message ?: "Something went wrong" }
    }
    fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()
        searchJob = run {
            searching.value = true
            results.value = emptyList()
            try { delay(300); results.value = repository.provider(repository.dao.getSettings() ?: Settings()).search(query) }
            finally { searching.value = false }
        }
    }
    fun add(list: Long, instrument: Instrument) = run {
        repository.add(list, instrument)
        message.value = "${instrument.ticker} added · fetching latest published value"
        refreshingIds.value += instrument.id
        try { repository.check(false, setOf(instrument.id)) { app.notifier.send(it) } }
        finally { refreshingIds.value -= instrument.id }
    }
    fun refreshDirectory() {
        if (refreshingDirectory.value) return
        run {
            refreshingDirectory.value = true
            try {
                val provider = repository.provider(repository.dao.getSettings() ?: Settings())
                directoryStatus.value = if (provider is FreePublicProvider) provider.refreshDirectory()
                    else "Search the configured provider by ticker or name"
                search(currentQuery)
            } finally { refreshingDirectory.value = false }
        }
    }
    fun remove(instrument: TrackedInstrument) = run { repository.dao.remove(instrument) }
    fun createList(name: String) = run {
        require(name.trim().length in 1..60) { "Use a name between 1 and 60 characters" }
        repository.dao.insertList(Watchlist(name = name.trim()))
    }
    fun deleteList(list: Watchlist) = run { repository.dao.deleteList(list) }
    fun save(value: Settings) = run {
        repository.save(value)
        MonitorScheduler.apply(app, value)
        message.value = "Settings saved"
        refreshMarket()
        refreshDirectory()
    }
    fun testConnection(url: String, freeFeeds: Boolean) = run {
        if (testingConnection.value) return@run
        testingConnection.value = true
        connectionReport.value = "Fetching and validating actual prices and NAVs…"
        try {
            val provider = repository.provider(Settings(providerUrl = url, freeFeeds = freeFeeds))
            connectionReport.value = when (provider) {
                is FreePublicProvider -> provider.health()
                is DirectoryProvider -> "Offline identity directory. Configure an authorized gateway for prices and history."
                else -> {
                    val instrument = provider.resolve(provider.search("CCAP").first().id)
                    val quote = provider.quote(instrument)
                    validateQuote(instrument, quote)
                    "Connected · ${instrument.ticker} ${quote.value} ${quote.currency} · ${quote.kind} · ${quote.timestamp}"
                }
            }
            market.value = provider.marketStatus()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { connectionReport.value = "Connection failed: ${e.message}" }
        finally { testingConnection.value = false }
    }
    fun check() {
        if (busy.value) return
        run {
            busy.value = true
            try {
                val count = repository.check(false) { app.notifier.send(it) }
                val total = repository.dao.getInstruments().map { it.id }.distinct().size
                if (total > 0) message.value = "$count of $total checked successfully · saved values kept for unavailable sources"
                refreshMarket()
            } finally { busy.value = false }
        }
    }
    private suspend fun refreshMarket() {
        try { market.value = repository.provider(repository.dao.getSettings() ?: Settings()).marketStatus() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { market.value = MarketStatus("Unknown", "Market status unavailable from the provider.", null) }
    }
    fun thresholds(row: TrackedInstrument, absolute: String, percent: String) = run {
        val a = absolute.trim().takeIf { it.isNotEmpty() }
        val p = percent.trim().takeIf { it.isNotEmpty() }
        require(a == null || a.toBigDecimal().signum() > 0) { "Absolute threshold must be positive" }
        require(p == null || p.toBigDecimal().signum() > 0) { "Percentage threshold must be positive" }
        repository.dao.getInstrument(row.listId, row.id)?.let {
            repository.dao.update(it.copy(absoluteThreshold = a, percentThreshold = p))
        }
        message.value = "Instrument alert rules saved"
    }
    fun clearHistory() = run { repository.dao.clearHistory() }
}
