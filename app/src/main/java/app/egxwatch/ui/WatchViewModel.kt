package app.egxwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.egxwatch.WatchApplication
import app.egxwatch.data.*
import app.egxwatch.domain.*
import app.egxwatch.monitor.MonitorScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WatchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WatchApplication
    private val repository = app.repository
    val lists = repository.dao.lists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val instruments = repository.dao.instruments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = repository.dao.settings().map { it ?: Settings() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
    val alerts = repository.dao.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val results = MutableStateFlow<List<Instrument>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val searching = MutableStateFlow(false)
    val market = MutableStateFlow(MarketStatus("Unknown", "Exchange status has not been checked.", null))
    private var searchJob: Job? = null
    init { run { repository.initialize(); refreshMarket() } }
    private fun run(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = e.message ?: "Something went wrong" }
    }
    fun search(query: String) {
        searchJob?.cancel()
        searchJob = run {
            searching.value = true
            results.value = emptyList()
            try { delay(300); results.value = repository.provider(repository.dao.getSettings() ?: Settings()).search(query) }
            finally { searching.value = false }
        }
    }
    fun add(list: Long, instrument: Instrument) = run { repository.add(list, instrument); message.value = "${instrument.ticker} added" }
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
    }
    fun testConnection(url: String, freeFeeds: Boolean) = run {
        val provider = repository.provider(Settings(providerUrl = url, freeFeeds = freeFeeds))
        val resolved = provider.search("CCAP")
        message.value = "Connected. ${resolved.size} search result(s) for CCAP."
        market.value = provider.marketStatus()
    }
    fun check() {
        if (busy.value) return
        run {
            busy.value = true
            try {
                val count = repository.check(false) { app.notifier.send(it) }
                message.value = "$count instrument(s) updated. See each instrument for availability."
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
