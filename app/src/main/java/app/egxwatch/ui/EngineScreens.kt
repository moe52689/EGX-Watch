package app.egxwatch.ui

import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.egxwatch.data.*
import app.egxwatch.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun time(value:Long?)=value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("Africa/Cairo")).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm")) } ?: "Not yet"
@Composable private fun Section(title:String,body:String) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text(body,style=MaterialTheme.typography.bodyMedium)
    } }
}
@Composable fun AnalysisSummary(record:AnalysisResult?) {
    val a=record?.analysis()
    Text(if(a?.score==null) a?.status ?: "INSUFFICIENT DATA · analysis awaiting verified history" else
        "Opportunity ${a.score}/100 · confidence ${(a.confidence*100).toInt()}%",style=MaterialTheme.typography.labelLarge)
    record?.let {
        Text("Analyzed ${time(it.analyzedAt)} · ${it.quote().freshness(Instant.now())}",style=MaterialTheme.typography.labelSmall)
        it.quote().fields.previousClose?.takeIf { p->p.signum()>0 }?.let { previous ->
            Text("Daily change ${change(it.quote().value,previous).percent?.display()}%",style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun OpportunityScreen(record:AnalysisResult?) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(record==null) item { Section("Analysis unavailable","A verified snapshot and sufficient historical data are required. Connect an authorized gateway in Settings.") }
        else {
            val a=record.analysis();val q=record.quote();val freshness=q.freshness(Instant.now())
            item { Text(record.ticker,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text(record.name,style=MaterialTheme.typography.titleMedium) }
            item { Section("Verified observation", "${q.value.display()} ${q.currency} · ${q.kind}\n${freshness}\nSource: ${q.source}\nData: ${q.timestamp}\nAnalysis: ${time(record.analyzedAt)}\nAge: ${Duration.between(q.timestamp,Instant.now()).toMinutes().coerceAtLeast(0)} minutes") }
            if(freshness in setOf(Freshness.STALE,Freshness.UNAVAILABLE)) item { Section("Historical assessment","This assessment is not a current opportunity. Its source observation is stale or unverified.") }
            item { Section("Opportunity assessment",a.score?.let { "$it / 100\nConfidence ${(a.confidence*100).toInt()}% · data coverage, not return probability\n${a.state.name.replace('_',' ')}" } ?: a.status) }
            items(a.components.entries.toList()) { (name,value) ->
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("$name · $value / 100");LinearProgressIndicator(progress={value/100f},modifier=Modifier.fillMaxWidth())
                }
            }
            item { Section("Why was this detected?",a.drivers.joinToString("\n") { "• $it" }.ifEmpty { "No qualified signals" }) }
            item { Section("Risks & missing context",a.risks.joinToString("\n") { "• $it" }.ifEmpty { "Insufficient information to assess risk" }) }
            if(a.indicators.isNotEmpty()) item { Section("Reproducible calculations",a.indicators.entries.joinToString("\n") { "${it.key}: ${String.format(Locale.US,"%.4f",it.value)}" }) }
            item { Section("How to read this",StructuredExplanationEngine().explain(a)+"\nRisk resilience is higher when historical risk is lower. Missing valuation and market-context scores are not assumed neutral. Daily indicators use completed observations; NAVs are not executable prices.") }
        }
    }
}
@Composable fun MonitoringStatusScreen(vm:WatchViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle();val config by vm.engineConfig.collectAsStateWithLifecycle()
    val status by vm.engineStatus.collectAsStateWithLifecycle();val health by vm.providerHealth.collectAsStateWithLifecycle()
    val instruments by vm.instruments.collectAsStateWithLifecycle();val analyses by vm.analyses.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle();val context=LocalContext.current
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(30000);now=Instant.now() } }
    val calendar=config.calendar(settings);val open=calendar.isOpen(now)
    val nominal=status.lastAttempt?.let { Instant.ofEpochMilli(it).plusSeconds(settings.interval*60) } ?: now
    val next=calendar.nextOpen(maxOf(now,nominal))
    val restricted=Build.VERSION.SDK_INT>=28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
    val optimized=!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Monitoring status",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold) }
        item { Section("EGX monitoring",when { !settings.enabled->"PAUSED";settings.providerUrl.isBlank()&&config.urls().isEmpty()->"NEEDS PROVIDER";!open->"WAITING FOR SESSION";else->"ACTIVE · Android schedules checks" }) }
        item { Section("Session", "Configured window: ${if(open) "OPEN" else "CLOSED"}\nExchange status: UNKNOWN · no authoritative calendar connected\nCairo ${settings.start}–${settings.end}\nLocal holidays and exception windows apply.") }
        item { Section("Check activity","Last attempt: ${time(status.lastAttempt)}\nLast successful check: ${time(status.lastSuccess)}\nLast fresh observation: ${time(status.lastFresh)}\nNext eligible check estimate: ${if(settings.enabled) time(next?.toEpochMilli()) else "Paused"}\n${status.message}\nNetwork: ${if(online==true) "Connected" else if(online==false) "Offline" else "Unknown"}") }
        val urls=(listOf(settings.providerUrl)+config.urls()).filter { it.isNotBlank() }.distinct()
        if(urls.isEmpty()) item { Section("Provider","Not configured. Offline identities and saved observations remain available.") }
        items(urls) { url ->
            val h=health.firstOrNull { it.provider==url }
            Section(if(url==urls.first()) "Primary provider" else "Fallback provider", "${android.net.Uri.parse(url).host}\n${h?.status ?: "Not checked"}\nLast success: ${time(h?.lastSuccess)}"+
                if((h?.retryAt ?: 0)>now.toEpochMilli()) "\nCooldown until ${time(h?.retryAt)}" else "")
        }
        item { Section("Analytics & alerts","Watchlist: ${instruments.map { it.id }.distinct().size} instruments\nQuantitative engine: ${analyses.count { it.analysis().status=="READY" }} assessments available\nOpportunity alerts: ${if(config.enabled) "Enabled" else "Paused"}\nAndroid notifications: ${if(NotificationManagerCompat.from(context).areNotificationsEnabled()) "Enabled" else "Blocked"}\nAI explanation: local, structured features only") }
        item { Section("Android background execution", "Background restricted: $restricted\nBattery optimization applies: $optimized\nPeriodic work is inexact and may be delayed by Doze, battery restrictions, or network loss. Force-stop prevents work until reopening. These settings indicate restrictions; they do not prove why a check was delayed.") }
    }
}
@Composable fun EngineSettingsScreen(config:EngineConfig,settings:Settings,onSave:(EngineConfig)->Unit) {
    var draft by remember(config) { mutableStateOf(config) }
    var score by remember(config) { mutableStateOf(config.minimumScore.toString()) };var confidence by remember(config) { mutableStateOf(config.minimumConfidence.toString()) }
    var cooldown by remember(config) { mutableStateOf(config.cooldownMinutes.toString()) };var material by remember(config) { mutableStateOf(config.materialChange.toString()) };var limit by remember(config) { mutableStateOf(config.dailyLimit.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Analytics & calendar",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold) }
        item { Section("Data requirements","Use an authorized HTTPS gateway for quotes and completed daily history. No vendor keys belong in the app. Missing or stale history cannot produce an opportunity alert.") }
        item { OutlinedTextField(draft.fallbackUrls,{draft=draft.copy(fallbackUrls=it)},Modifier.fillMaxWidth(),label={Text("Fallback HTTPS URLs · one per line, up to 2")}) }
        item { Text("Local session exceptions",style=MaterialTheme.typography.titleLarge);Text("These override the configured weekly window. Holidays take priority. Keep this list current with exchange announcements.") }
        item { OutlinedTextField(draft.holidays,{draft=draft.copy(holidays=it)},Modifier.fillMaxWidth(),label={Text("Holiday dates · YYYY-MM-DD, comma separated")}) }
        item { OutlinedTextField(draft.exceptions,{draft=draft.copy(exceptions=it)},Modifier.fillMaxWidth(),label={Text("Exceptions · YYYY-MM-DD HH:mm-HH:mm per line")}) }
        item { Row { Text("Opportunity notifications",Modifier.weight(1f));Switch(draft.enabled,{draft=draft.copy(enabled=it)}) } }
        item { OutlinedTextField(score,{score=it},Modifier.fillMaxWidth(),label={Text("Minimum score · 0–100")}) }
        item { OutlinedTextField(confidence,{confidence=it},Modifier.fillMaxWidth(),label={Text("Minimum confidence · 0–1")}) }
        item { OutlinedTextField(cooldown,{cooldown=it},Modifier.fillMaxWidth(),label={Text("Per-security cooldown · minutes")}) }
        item { OutlinedTextField(material,{material=it},Modifier.fillMaxWidth(),label={Text("Material score change · 1–100")}) }
        item { OutlinedTextField(limit,{limit=it},Modifier.fillMaxWidth(),label={Text("Daily alert limit · 1–100")}) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.quietStart,{draft=draft.copy(quietStart=it)},Modifier.weight(1f),label={Text("Quiet start")})
            OutlinedTextField(draft.quietEnd,{draft=draft.copy(quietEnd=it)},Modifier.weight(1f),label={Text("Quiet end")})
        };Text("Cairo HH:mm · equal times disable quiet hours. Price-change notification rules remain separate.",style=MaterialTheme.typography.bodySmall) }
        item { error?.let { Text(it,color=MaterialTheme.colorScheme.error) };Button(onClick={
            try { val value=draft.copy(minimumScore=score.toInt(),minimumConfidence=confidence.toDouble(),cooldownMinutes=cooldown.toLong(),materialChange=material.toInt(),dailyLimit=limit.toInt())
                value.rules();value.urls();value.calendar(settings);onSave(value);error=null
            } catch(_:Exception) { error="Check the URLs, dates, HH:mm windows and numerical ranges." }
        },modifier=Modifier.fillMaxWidth()) { Text("Save analytics settings") } }
    }
}
