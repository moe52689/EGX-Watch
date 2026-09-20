package app.egxwatch

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.egxwatch.data.*
import app.egxwatch.domain.*
import app.egxwatch.ui.WatchViewModel
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WatchApp(openHistory = intent.getBooleanExtra("history", false)) }
    }
}
private val Green = Color(0xFF91E4B5)
private val DarkColors = darkColorScheme(primary = Green, secondary = Color(0xFFBCCDBF),
    background = Color(0xFF0C1411), surface = Color(0xFF111D17), surfaceVariant = Color(0xFF203029),
    primaryContainer = Color(0xFF224C39), onPrimary = Color(0xFF053821), onPrimaryContainer = Color(0xFFD0F8DF),
    secondaryContainer = Color(0xFF304D3D), onSecondaryContainer = Color(0xFFDBF0E1),
    surfaceContainer = Color(0xFF18271F), surfaceContainerHigh = Color(0xFF203029),
    surfaceContainerHighest = Color(0xFF293B30), onSurface = Color(0xFFE1EAE2), onSurfaceVariant = Color(0xFFBFCFC3))
private val LightColors = lightColorScheme(primary = Color(0xFF17633F), secondary = Color(0xFF526457),
    background = Color(0xFFF5F8F3), surface = Color(0xFFF9FCF7), surfaceVariant = Color(0xFFE0EAE0),
    primaryContainer = Color(0xFFC8F1D5), onPrimaryContainer = Color(0xFF123A25),
    secondaryContainer = Color(0xFFD5E8D9), onSecondaryContainer = Color(0xFF263D2D),
    surfaceContainer = Color(0xFFECF2EA), surfaceContainerHigh = Color(0xFFE5EDE2),
    surfaceContainerHighest = Color(0xFFDDE8DA))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchApp(openHistory: Boolean = false, vm: WatchViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val instruments by vm.instruments.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val market by vm.market.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(if (openHistory) 2 else 0) }
    var listId by rememberSaveable { mutableLongStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var newList by remember { mutableStateOf(false) }
    var deleteList by remember { mutableStateOf(false) }
    val currentList = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull()
    val rows = instruments.filter { it.listId == currentList?.id }
    val detail = rows.firstOrNull { it.id == detailId }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    val dark = when (settings.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            TopAppBar(title = {
                Column { Text(if (detail != null) detail.ticker else "EGX Watch", fontWeight = FontWeight.Bold)
                    Text(if (detail != null) "Instrument details" else "YOUR MARKET. IN FOCUS.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary) }
            }, navigationIcon = {
                if (detail != null) IconButton(onClick = { detailId = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            }, actions = {
                if (detail == null) IconButton(onClick = vm::check, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, "Check now")
                }
            })
        }, bottomBar = {
            if (detail == null) NavigationBar {
                val labels = listOf("Watchlist", "Discover", "History", "Settings")
                val icons = listOf(Icons.AutoMirrored.Outlined.ShowChart, Icons.Outlined.Search, Icons.Outlined.Notifications, Icons.Outlined.Settings)
                labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index,
                    onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label) }) }
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    detail != null -> InstrumentDetail(detail, onRemove = { vm.remove(detail); detailId = null }, onSave = { a, p -> vm.thresholds(detail, a, p) })
                    tab == 0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("THE EGYPTIAN MARKET", style = MaterialTheme.typography.labelMedium)
                                    Text("A clearer view of\nyour investments.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SmallTag("${rows.size} instruments")
                                        SmallTag(if (settings.enabled) "Every ${settings.interval} min" else "Monitoring paused")
                                    }
                                }
                            }
                        }
                        item { MarketCard(market) }
                        if (settings.providerUrl.isBlank()) item {
                            if (settings.freeFeeds) InfoCard("Free public feeds", "CCAP & BINV: indicative snapshots, delay unknown. AZG, T70, CTQ & BFA: dated fund NAVs. Tap refresh to check.")
                            else InfoCard("Start with a data connection", "Connect a provider or enable free public feeds in Settings.", "Set up provider") { tab = 3 }
                        }
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Watchlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { newList = true }) { Icon(Icons.Outlined.Add, null); Text("New") }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                lists.forEach { list -> FilterChip(selected = currentList?.id == list.id, onClick = { listId = list.id }, label = { Text(list.name) }) }
                            }
                        }
                        if (rows.isEmpty()) item { InfoCard("Make this watchlist yours", "Search a ticker or a fund name. Every addition is checked against the selected provider’s directory.", "Find instruments") { tab = 1 } }
                        items(rows, key = { it.id }) { row -> InstrumentCard(row) { detailId = row.id } }
                        item {
                            Button(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth(), enabled = currentList != null) { Icon(Icons.Outlined.Add, null); Text("Add instrument") }
                            if (currentList != null) TextButton(onClick = { deleteList = true }) { Text("Delete this watchlist") }
                        }
                    }
                    tab == 1 -> SearchScreen(vm, currentList) { newList = true }
                    tab == 2 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item { Text("Your notification history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Latest 500 alerts · newest first", style = MaterialTheme.typography.bodySmall) }
                        if (alerts.isEmpty()) item { InfoCard("All quiet for now", "Alerts appear after a verified value changes. The first successful check establishes a baseline unless every-check alerts are enabled.") }
                        items(alerts, key = { it.id }) { alert ->
                            Card { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${alert.ticker} · ${alert.name}", fontWeight = FontWeight.Bold)
                                Text("${alert.current} ${alert.currency}", style = MaterialTheme.typography.headlineSmall)
                                Text("${alert.kind} · Previous ${alert.previous ?: "—"} ${alert.currency}")
                                Text("Change ${alert.absolute ?: "—"} ${alert.currency} / ${alert.percent ?: "N/A"}%")
                                Text(dataTime(alert.dataTimestamp, alert.timestampBasis), style = MaterialTheme.typography.bodySmall)
                                Text("Checked: ${formatTime(alert.checkedAt)}", style = MaterialTheme.typography.bodySmall)
                                Text("${alert.source}\n${alert.delivery}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }
                        }
                        if (alerts.isNotEmpty()) item { TextButton(onClick = vm::clearHistory) { Text("Clear history") } }
                    }
                    else -> SettingsScreen(settings, vm)
                }
            }
        }
        if (newList) {
            var name by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { newList = false }, title = { Text("Create watchlist") }, text = {
                OutlinedTextField(name, { name = it }, label = { Text("Watchlist name") }, singleLine = true)
            }, confirmButton = { TextButton(onClick = { vm.createList(name); newList = false }, enabled = name.trim().length in 1..60) { Text("Create") } },
                dismissButton = { TextButton(onClick = { newList = false }) { Text("Cancel") } })
        }
        if (deleteList && currentList != null) AlertDialog(onDismissRequest = { deleteList = false }, title = { Text("Delete ${currentList.name}?") },
            text = { Text("This removes its tracked instruments. Notification history is retained.") },
            confirmButton = { TextButton(onClick = { vm.deleteList(currentList); deleteList = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteList = false }) { Text("Cancel") } })
    }
}

@Composable private fun SmallTag(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .6f), shape = RoundedCornerShape(9.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun InfoCard(title: String, text: String, action: String? = null, onClick: () -> Unit = {}) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onClick) { Text(action) }
    } }
}
@Composable private fun MarketCard(status: MarketStatus) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Public, null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text("EGX · ${status.state}", style = MaterialTheme.typography.titleSmall)
            Text(status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status.timestamp?.let { Text("As of ${formatTime(it.toString())}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
@Composable private fun InstrumentCard(row: TrackedInstrument, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(row.ticker.take(2), fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.ticker, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(row.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                SmallTag(row.type)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(row.value?.let { "$it ${row.currency}" } ?: "—", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(dataLabel(row), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(row.timestamp?.let { dataTime(it, row.timestampBasis) } ?: "No verified ${if (row.type == "FUND") "NAV" else "quote"} yet", style = MaterialTheme.typography.bodySmall)
            if (row.timestamp != null && Duration.between(Instant.parse(row.timestamp), Instant.now()).toHours() > 24)
                Text("Older observation · not a current quote", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            if (row.error != null) Text(row.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
@Composable private fun SearchScreen(vm: WatchViewModel, list: Watchlist?, newList: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    LaunchedEffect(query) { vm.search(query) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Discover your next watch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Stocks, ETFs & investment funds", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Ticker or full name") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }) }
        item { Text(if (list == null) "Create a watchlist to add instruments." else "Adding to ${list.name}", style = MaterialTheme.typography.bodySmall)
            if (list == null) TextButton(onClick = newList) { Text("Create watchlist") }
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!searching && results.isEmpty()) item { InfoCard("No matching instruments", "Check the ticker or connect a provider with broader coverage. Unrecognized symbols cannot be added.") }
        items(results, key = { it.id }) { instrument ->
            OutlinedCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(instrument.ticker, fontWeight = FontWeight.Bold); SmallTag(instrument.type.name) }
                Text(instrument.name, style = MaterialTheme.typography.titleMedium)
                Text("${instrument.currency} · Directory verified ${instrument.verifiedAt}", style = MaterialTheme.typography.bodySmall)
                Text(instrument.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { list?.let { vm.add(it.id, instrument) } }, enabled = list != null) { Text("Validate & add") }
            } }
        }
    }
}
@Composable private fun InstrumentDetail(row: TrackedInstrument, onRemove: () -> Unit, onSave: (String, String) -> Unit) {
    var absolute by remember(row.id) { mutableStateOf(row.absoluteThreshold ?: "") }
    var percent by remember(row.id) { mutableStateOf(row.percentThreshold ?: "") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(row.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${row.type} · ${row.currency}", color = MaterialTheme.colorScheme.primary) }
        item { InstrumentCard(row) {} }
        item {
            InfoCard("Value comparison", "Previous: ${row.previous ?: "—"} ${row.currency}\n" +
                if (row.previous != null && row.value != null) change(row.value.toBigDecimal(), row.previous.toBigDecimal()).let {
                    "Change: ${it.absolute.display()} ${row.currency}\nPercentage: ${it.percent?.display() ?: "N/A"}%"
                } else "Waiting for two comparable observations.")
        }
        item { InfoCard("Data provenance", "${row.quoteSource ?: "No value source connected"}\nLast check: ${row.lastCheck?.let(::formatTime) ?: "Never"}\nIdentity source: ${row.identitySource}\nDirectory verified: ${row.verifiedAt}") }
        item { Text("Alert rules for ${row.ticker}", style = MaterialTheme.typography.titleLarge)
            Text("Blank fields inherit global settings. Either threshold triggers an alert; comparisons use the previous successful check.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedTextField(absolute, { absolute = it }, Modifier.fillMaxWidth(), label = { Text("Absolute change (${row.currency})") }, singleLine = true) }
        item { OutlinedTextField(percent, { percent = it }, Modifier.fillMaxWidth(), label = { Text("Percentage change (%)") }, singleLine = true) }
        item { Button(onClick = { onSave(absolute, percent) }, Modifier.fillMaxWidth()) { Text("Save alert rules") }
            TextButton(onClick = onRemove) { Text("Remove from watchlist") } }
    }
}

@Composable private fun SettingsScreen(settings: Settings, vm: WatchViewModel) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var interval by remember(settings) { mutableStateOf(settings.interval.toString()) }
    var validation by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.message.value = if (it) "Notifications allowed" else "Notifications blocked; alert history still records events"
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Make it work for you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Data connection", style = MaterialTheme.typography.titleLarge)
            Text("Free public feeds need no key. A custom HTTPS gateway takes priority and can cover any validated stock, ETF or fund.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Use free public feeds", Modifier.weight(1f)); Switch(draft.freeFeeds, { draft = draft.copy(freeFeeds = it) }) }
            Text("Public feeds may change or be delayed. Exchange timing is unverified for indicative prices. API keys belong on your gateway server.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedTextField(draft.providerUrl, { draft = draft.copy(providerUrl = it.trim()) }, Modifier.fillMaxWidth(),
            label = { Text("Provider base URL") }, placeholder = { Text("https://your-server.example/v1/") }, singleLine = true)
            TextButton(onClick = { vm.testConnection(draft.providerUrl, draft.freeFeeds) }) { Text("Test connection") } }
        item { HorizontalDivider(); Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Background monitoring", style = MaterialTheme.typography.titleMedium); Text("Android may delay checks to save battery.", style = MaterialTheme.typography.bodySmall) }
            Switch(draft.enabled, { draft = draft.copy(enabled = it) })
        } }
        item { Text("Check interval", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L to "15 min", 30L to "30 min", 60L to "1 hour", 120L to "2 hours").forEach { (minutes, label) ->
                    FilterChip(selected = interval == minutes.toString(), onClick = { interval = minutes.toString() }, label = { Text(label) })
                }
            }
            OutlinedTextField(interval, { interval = it }, Modifier.fillMaxWidth(), label = { Text("Custom interval in minutes (15 or more)") }, singleLine = true)
        }
        item { Text("Monitoring days · Cairo time", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7 to "Sun", 1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat").forEach { (day, label) ->
                    val selected = day.toString() in draft.days.split(',')
                    FilterChip(selected, onClick = {
                        val days = draft.days.split(',').filter { it.isNotBlank() }.toMutableSet()
                        if (selected) days.remove(day.toString()) else days.add(day.toString())
                        draft = draft.copy(days = days.sorted().joinToString(","))
                    }, label = { Text(label) })
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(draft.start, { draft = draft.copy(start = it) }, Modifier.weight(1f), label = { Text("Start HH:mm") }, singleLine = true)
            OutlinedTextField(draft.end, { draft = draft.copy(end = it) }, Modifier.weight(1f), label = { Text("End HH:mm") }, singleLine = true)
        }; Text("Equal times = all day. Overnight windows belong to the starting day. Manual checks ignore this schedule.", style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(); Text("Notifications", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify on every successful check", Modifier.weight(1f)); Switch(draft.everyCheck, { draft = draft.copy(everyCheck = it) })
            }
            Text("Every-check alerts include unchanged values and bypass thresholds. Errors never generate price alerts.", style = MaterialTheme.typography.bodySmall)
            if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow Android notifications") }
        }
        item { OutlinedTextField(draft.absoluteThreshold, { draft = draft.copy(absoluteThreshold = it) }, Modifier.fillMaxWidth(), label = { Text("Global absolute threshold (quote currency)") }, singleLine = true) }
        item { OutlinedTextField(draft.percentThreshold, { draft = draft.copy(percentThreshold = it) }, Modifier.fillMaxWidth(), label = { Text("Global percentage threshold (%)") }, singleLine = true)
            Text("Leave both blank for any value change. If set, either threshold can trigger. Set per-instrument rules in details.", style = MaterialTheme.typography.bodySmall) }
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("System", "Light", "Dark").forEach { theme ->
                FilterChip(draft.theme == theme, { draft = draft.copy(theme = theme) }, label = { Text(theme) })
            } }
        }
        item { validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                try {
                    val value = draft.copy(interval = interval.toLong(), start = LocalTime.parse(draft.start).toString(), end = LocalTime.parse(draft.end).toString())
                    value.policy()
                    if (value.providerUrl.isNotBlank()) GatewayProvider.validateBaseUrl(value.providerUrl)
                    validation = null; vm.save(value)
                } catch (_: Exception) { validation = "Check the HTTPS URL, interval (15–525600), selected days, HH:mm times, and positive thresholds." }
            }, Modifier.fillMaxWidth()) { Text("Save settings") }
        }
        item { Text("EGX Watch 1.0 · Values are provided for monitoring. Fund NAVs are published valuations, not live exchange quotes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
private fun dataLabel(row: TrackedInstrument): String = when (row.kind) {
    "LIVE" -> "LIVE QUOTE"; "DELAYED" -> "DELAYED ${row.delayMinutes} MIN"; "NAV" -> "FUND NAV"; "INDICATIVE" -> "INDICATIVE · DELAY UNKNOWN"; else -> if (row.type == "FUND") "FUND NAV" else "QUOTE UNAVAILABLE"
}
private fun dataTime(value: String, basis: String): String = when (basis) {
    "VALUATION_DATE" -> "NAV date: ${Instant.parse(value).atZone(ZoneId.of("Africa/Cairo")).toLocalDate()} (time not published)"
    "PROVIDER_SNAPSHOT" -> "Provider snapshot: ${formatTime(value)} · exchange time unknown"
    else -> "Data: ${formatTime(value)}"
}
private fun formatTime(value: String): String = try {
    Instant.parse(value).atZone(ZoneId.of("Africa/Cairo")).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm z"))
} catch (_: Exception) { value }
