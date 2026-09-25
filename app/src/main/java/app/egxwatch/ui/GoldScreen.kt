package app.egxwatch.ui



import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import app.egxwatch.data.*

import app.egxwatch.domain.*

import java.time.Instant

import org.json.JSONObject



@Composable fun GoldSummary(vm:WatchViewModel,onOpen:()->Unit) {

 val status by vm.goldStatus.collectAsStateWithLifecycle()

 val q=remember(status.payload) { status.payload?.let { GatewayProvider.parseQuote(JSONObject(it)) } }

 OutlinedCard(onClick=onOpen,modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {

  Text("GLOBAL GOLD · XAU/USD",color=MarketPalette.gold(),style=MaterialTheme.typography.labelLarge)

  Text(q?.let { "${it.value.display()} USD / troy oz" } ?: "Start collecting gold prices",style=MaterialTheme.typography.titleLarge)

  Text(q?.let { "${if(status.message.contains("unavailable",true) || it.freshness(Instant.now())==Freshness.STALE) "STALE · saved spot" else "Saved provider snapshot"} · ${localTime(it.timestamp.toEpochMilli())}" } ?: "Independent gold monitoring",style=MaterialTheme.typography.bodySmall)

 } }

}

@Composable fun GoldScreen(vm:WatchViewModel) {

 val config by vm.goldConfig.collectAsStateWithLifecycle();val status by vm.goldStatus.collectAsStateWithLifecycle()

 val busy by vm.goldBusy.collectAsStateWithLifecycle();val analyses by vm.analyses.collectAsStateWithLifecycle()

 val analysis=analyses.firstOrNull { it.instrumentId==GlobalGold.instrument.id }

 var setup by remember { mutableStateOf(false) }

 val q=remember(status.payload) { status.payload?.let { GatewayProvider.parseQuote(JSONObject(it)) } }

 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {

  item { Text("Global gold",style=MaterialTheme.typography.headlineLarge,color=MarketPalette.gold())

   Text("XAU/USD · spot USD per troy ounce. Independent from Egyptian retail gold and gold-fund NAVs.") }

  item { GoldSummary(vm) {};Text(status.message,style=MaterialTheme.typography.bodySmall)

   q?.let { Text("Source: ${it.source}\nProvider update: ${localTime(it.timestamp.toEpochMilli())}\nLast received: ${localTime(status.lastSuccess)}",style=MaterialTheme.typography.bodySmall) }

   Text("${if(config.enabled) "MONITORING" else "PAUSED"} · ${config.interval} min · ${config.zone}",style=MaterialTheme.typography.labelLarge)

   Row { Button(onClick={vm.checkGold()},enabled=!busy) { Text(if(busy) "Checking…" else "Refresh gold") }

    TextButton(onClick={setup=!setup}) { Text("Configure") } } }

  if(setup) item { GoldConfiguration(config) { vm.saveGold(it);setup=false } }

  item { LocalHistoryPanel(vm,GlobalGold.instrument.id,"USD/oz",analysis) }

  item { MaturityCard(analysis) }

  item { GoldRules(vm) }

  item { Text("No historical subscription required. Charts start with observations collected on this device. Provider availability does not guarantee fresh weekend or holiday prices.",style=MaterialTheme.typography.bodySmall) }

 }

}

@Composable private fun GoldConfiguration(config:GoldConfig,onSave:(GoldConfig)->Unit) {

 var enabled by remember(config) { mutableStateOf(config.enabled) };var free by remember(config) { mutableStateOf(config.freeProvider) }

 var url by remember(config) { mutableStateOf(config.providerUrl) };var fallback by remember(config) { mutableStateOf(config.fallbackUrl) }

 var interval by remember(config) { mutableStateOf(config.interval.toString()) };var days by remember(config) { mutableStateOf(config.days) }

 var start by remember(config) { mutableStateOf(config.start) };var end by remember(config) { mutableStateOf(config.end) }

 var zone by remember(config) { mutableStateOf(config.zone) };var holidays by remember(config) { mutableStateOf(config.holidays) };var error by remember { mutableStateOf<String?>(null) }

 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {

  Row { Switch(enabled,{enabled=it});Text("Monitor gold in the background",Modifier.padding(12.dp)) }

  Row { Switch(free,{free=it});Text("Use keyless Gold-API current feed",Modifier.padding(12.dp)) }

  OutlinedTextField(url,{url=it},label={Text("Optional primary HTTPS gateway")},modifier=Modifier.fillMaxWidth())

  OutlinedTextField(fallback,{fallback=it},label={Text("Optional fallback HTTPS gateway")},modifier=Modifier.fillMaxWidth())

  OutlinedTextField(interval,{interval=it},label={Text("Interval minutes (minimum 15)")})

  OutlinedTextField(days,{days=it},label={Text("Days: Monday=1 … Sunday=7")})

  OutlinedTextField(start,{start=it},label={Text("Start HH:mm")});OutlinedTextField(end,{end=it},label={Text("End HH:mm · same = full day")})

  OutlinedTextField(zone,{zone=it},label={Text("Time zone, e.g. UTC")});OutlinedTextField(holidays,{holidays=it},label={Text("Closed dates YYYY-MM-DD, comma separated")})

  Text("Android schedules are approximate. No secret API keys belong in gateway URLs.",style=MaterialTheme.typography.bodySmall)

  error?.let { Text(it,color=MaterialTheme.colorScheme.error) }

  Button(onClick={try { val next=config.copy(enabled=enabled,freeProvider=free,providerUrl=url.trim(),fallbackUrl=fallback.trim(),interval=interval.toLong(),days=days,start=start,end=end,zone=zone,holidays=holidays);next.validate();onSave(next) } catch(_:Exception) { error="Check HTTPS URLs, interval, dates and time zone" }}) { Text("Save gold configuration") }

 }

}



@Composable private fun GoldRules(vm:WatchViewModel) {

 val rules by vm.goldRules.collectAsStateWithLifecycle()

 var type by remember { mutableStateOf(GoldAlertType.PRICE_ABOVE) };var threshold by remember { mutableStateOf("") }

 var period by remember { mutableStateOf("60") };var cooldown by remember { mutableStateOf("60") };var error by remember { mutableStateOf<String?>(null) }

 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {

  Text("Gold alerts",style=MaterialTheme.typography.titleLarge)

  Text("A first observation sets the baseline. Alerts require a new crossing; cooldowns survive restarts.",style=MaterialTheme.typography.bodySmall)

  GoldAlertType.entries.forEach { value -> FilterChip(type==value,{type=value},label={Text(value.name.replace('_',' '))}) }

  OutlinedTextField(threshold,{threshold=it},label={Text("Threshold USD/oz or percent (0 for highs/lows)")})

  OutlinedTextField(period,{period=it},label={Text("Lookback minutes: 15–10080")})

  OutlinedTextField(cooldown,{cooldown=it},label={Text("Cooldown minutes: 15–10080")})

  Text("Rapid movement needs 20 comparable observed intervals and a move beyond 3 sample standard deviations; threshold is a minimum percent move.",style=MaterialTheme.typography.bodySmall)

  error?.let { Text(it,color=MaterialTheme.colorScheme.error) }

  Button(onClick={try { val rule=GoldRule(type=type.name,threshold=threshold.toDouble(),periodMinutes=period.toLong(),cooldownMinutes=cooldown.toLong());GoldAlertEngine.validate(rule);vm.addGoldRule(rule);error=null } catch(_:Exception) { error="Enter a valid positive threshold and interval" }}) { Text("Add alert rule") }

  rules.forEach { rule -> Row { Text("${rule.type.replace('_',' ')} · ${rule.threshold}",Modifier.weight(1f));TextButton(onClick={vm.deleteGoldRule(rule)}) { Text("Delete") } } }

}

}

