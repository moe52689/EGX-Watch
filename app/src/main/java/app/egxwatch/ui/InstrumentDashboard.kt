package app.egxwatch.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.egxwatch.data.*
import app.egxwatch.domain.*
import com.patrykandpatrick.vico.compose.cartesian.*
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.core.cartesian.axis.*
import com.patrykandpatrick.vico.core.cartesian.data.*
import java.time.*

@Composable fun MaturityCard(record:AnalysisResult?) {
    val a=record?.analysis()
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Analytics · ${a?.maturity?.replace('_',' ') ?: "INITIALIZING"}",color=MarketPalette.analytics(),fontWeight=FontWeight.Bold)
        Text("Collected since ${localTime(a?.startedAt)}",style=MaterialTheme.typography.bodySmall)
        Text("${a?.observations ?: 0} valid observations · ${a?.sessions ?: 0} sampled sessions",style=MaterialTheme.typography.bodySmall)
        Text("Collected-span completeness: ${((a?.completeness ?: 0.0)*100).toInt()}%",style=MaterialTheme.typography.bodySmall)
        if(record!=null && record.quote().freshness(Instant.now()) !in setOf(Freshness.LIVE,Freshness.DELAYED)) Text("Saved assessment · source data is stale or unavailable",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelLarge)
        a?.score?.let { Text("Opportunity $it/100 · ${(a.confidence*100).toInt()}% coverage confidence",style=MaterialTheme.typography.titleMedium) }
        if(a?.available?.isNotEmpty()==true) Text("Available\n"+a.available.joinToString("\n") { "✓ $it" },style=MaterialTheme.typography.bodySmall)
        Text("Building history\n"+(a?.building ?: LocalIndicatorRequirements.requirements.keys.toList()).joinToString("\n") { "○ $it" },style=MaterialTheme.typography.bodySmall)
        Text("Unobserved trading is unknown. These are sampled-session indicators, not official exchange daily candles.",style=MaterialTheme.typography.labelSmall)
    } }
}
@Composable fun LocalHistoryPanel(vm:WatchViewModel,id:String,currency:String,record:AnalysisResult?) {
    val events by remember(id) { vm.chartEvents(id) }.collectAsStateWithLifecycle(emptyList())
    var selectedEvent by remember { mutableStateOf<CenterAlert?>(null) }
    var range by remember(id) { mutableStateOf(LocalChartRange.TODAY) }
    var candles by remember(id) { mutableStateOf(false) }
    var overlays by remember(id) { mutableStateOf(false) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    val current=collections.firstOrNull { it.instrumentId==id }
    val points by remember(id,range) { vm.points(id,range) }.collectAsStateWithLifecycle(emptyList())
    val sessions by remember(id) { vm.sessionBars(id) }.collectAsStateWithLifecycle(emptyList())
    val volumes by remember(id) { vm.volumePoints(id) }.collectAsStateWithLifecycle(emptyList())
    val eligible=record?.analysis()?.indicators?.filterKeys { it in setOf("Observed session SMA20","Observed support/resistance20","Observed support20") } ?: emptyMap()
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Your collected price history",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            LocalChartRange.entries.forEach { option -> FilterChip(selected=range==option,onClick={range=option},
                enabled=option.available(current?.startedAt,current?.lastReceivedAt,current?.observations ?: 0),label={Text(option.label)}) }
        }
        Text("Disabled ranges need more local history. Current provider series only.",style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(candles,{candles=!candles},enabled=sessions.count { it.samples>=2 }>=2,label={Text("Observed candles")})
            FilterChip(overlays,{overlays=!overlays},enabled=eligible.isNotEmpty(),label={Text("Latest levels")})
        }
        val start=range.start(Instant.now(),ZoneId.of(if(id=="GLOBAL:XAUUSD") "America/New_York" else "Africa/Cairo"))
        if(candles) ObservedCandles(sessions.filter { it.lastTime>=start })
        else PriceChart(points,currency,color=if(id=="GLOBAL:XAUUSD") MarketPalette.gold() else MarketPalette.information(),overlays=if(overlays) eligible else emptyMap(),eventTimes=events.filter { it.timestamp>=start }.map { it.timestamp },onEvent={ stamp->selectedEvent=events.firstOrNull { it.timestamp==stamp };selectedEvent?.let { vm.markRead(it.id) } })
        if(events.any { it.timestamp>=start }) {
            Text("◆ Alert markers · select an event below",style=MaterialTheme.typography.labelSmall)
            events.filter { it.timestamp>=start }.take(10).forEach { event -> TextButton(onClick={selectedEvent=event;vm.markRead(event.id)}) { Text("◆ ${event.title} · ${localTime(event.timestamp)}") } }
        }
        selectedEvent?.let { event -> AlertDialog(onDismissRequest={selectedEvent=null},title={Text(event.title)},text={Text("${event.body}\n${event.provider ?: "System"}\n${localTime(event.timestamp)}")},confirmButton={TextButton(onClick={selectedEvent=null}) { Text("Close") }}) }
        if(overlays) eligible.forEach { (name,value)->Text("Latest $name: $value",style=MaterialTheme.typography.labelSmall,color=MarketPalette.analytics()) }
        Text("Touch for price/time · pinch to zoom · drag to pan. Gaps contain no invented observations.",style=MaterialTheme.typography.labelSmall)
        if(volumes.size>=2) {
            Text("Provider cumulative volume · resets may occur between sessions",style=MaterialTheme.typography.labelSmall)
            PriceChart(volumes.takeLast(48),"volume",compact=true,color=MarketPalette.information())
        }
    }
}
@Composable private fun ObservedCandles(rows:List<ObservedSession>) {
    if(rows.size<2) { Text("Building observed candles for this range");return }
    val producer=remember { CartesianChartModelProducer() }
    LaunchedEffect(rows) { producer.runTransaction {
        candlestickSeries(rows.map { LocalDate.parse(it.sessionDate).toEpochDay() },rows.map { it.open.toDouble() },rows.map { it.close.toDouble() },rows.map { it.low.toDouble() },rows.map { it.high.toDouble() })
    } }
    CartesianChartHost(rememberCartesianChart(rememberCandlestickCartesianLayer(),startAxis=VerticalAxis.rememberStart(),
        bottomAxis=HorizontalAxis.rememberBottom(valueFormatter=CartesianValueFormatter { _,value,_->LocalDate.ofEpochDay(value.toLong()).toString() })),producer,Modifier.fillMaxWidth().height(240.dp))
    Text("Sampled session open/high/low/close; not official exchange OHLC.",style=MaterialTheme.typography.labelSmall)
}
