package app.egxwatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.egxwatch.data.*
import app.egxwatch.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

object MarketPalette {
    @Composable fun positive()=if(MaterialTheme.colorScheme.background.red<.5f) Color(0xFF70DAB0) else Color(0xFF006B45)
    @Composable fun negative()=if(MaterialTheme.colorScheme.background.red<.5f) Color(0xFFFFB4AB) else Color(0xFFAD242D)
    @Composable fun gold()=if(MaterialTheme.colorScheme.background.red<.5f) Color(0xFFE8C66B) else Color(0xFF785800)
    @Composable fun analytics()=if(MaterialTheme.colorScheme.background.red<.5f) Color(0xFFC9BBFF) else Color(0xFF6243A8)
    @Composable fun information()=if(MaterialTheme.colorScheme.background.red<.5f) Color(0xFFADC9FF) else Color(0xFF24599B)
}
fun localTime(value:Long?)=value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM · HH:mm")) } ?: "Awaiting data"

@Composable fun DashboardOverview(vm:WatchViewModel,count:Int,onStatus:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle();val config by vm.engineConfig.collectAsStateWithLifecycle()
    val status by vm.engineStatus.collectAsStateWithLifecycle();val records by vm.analyses.collectAsStateWithLifecycle()
    val opportunities=records.count { it.instrumentId!=GlobalGold.instrument.id && (it.analysis().score ?: -1)>=config.minimumScore && it.analysis().confidence>=config.minimumConfidence && it.quote().freshness(Instant.now()) in setOf(Freshness.LIVE,Freshness.DELAYED) }
    Card(onClick=onStatus,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("MARKET MONITOR",style=MaterialTheme.typography.labelMedium,color=MarketPalette.information())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("EGX monitoring",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text(if(!settings.enabled) "PAUSED" else if(config.calendar(settings).isOpen(Instant.now())) "ACTIVE" else "SESSION CLOSED",style=MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("$count",style=MaterialTheme.typography.headlineSmall);Text("instruments",style=MaterialTheme.typography.labelSmall) }
                Column { Text("$opportunities",style=MaterialTheme.typography.headlineSmall,color=MarketPalette.analytics());Text("qualified scores",style=MaterialTheme.typography.labelSmall) }
                Column { Text(localTime(status.lastFresh),style=MaterialTheme.typography.labelLarge);Text("last fresh update",style=MaterialTheme.typography.labelSmall) }
            }
            Text("View collection & provider status →",style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable fun MarketInstrumentCard(row:TrackedInstrument,vm:WatchViewModel,analysis:AnalysisResult?,onClick:()->Unit) {
    val points by remember(row.id) { vm.points(row.id,LocalChartRange.FIVE_DAYS) }.collectAsStateWithLifecycle(emptyList())
    val observation by remember(row.id) { vm.latestObservation(row.id) }.collectAsStateWithLifecycle(null)
    val movement=observation?.changePercent?.toBigDecimalOrNull()
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(row.ticker,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(row.type,style=MaterialTheme.typography.labelSmall,color=MarketPalette.information())
            }
            Text(row.name,style=MaterialTheme.typography.bodySmall,maxLines=2)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(row.value?.let { "$it ${row.currency}" } ?: "Awaiting quote",style=MaterialTheme.typography.titleLarge)
                Text(movement?.let { "${if(it.signum()>0) "+" else ""}${it.display()}%" } ?: "Daily change —",
                    color=if(movement==null || movement.signum()==0) MaterialTheme.colorScheme.onSurfaceVariant else if(movement.signum()<0) MarketPalette.negative() else MarketPalette.positive(),style=MaterialTheme.typography.labelLarge)
            }
            PriceChart(points.takeLast(48),row.currency,compact=true)
            val a=analysis?.analysis()
            Text(a?.score?.let { "Opportunity $it/100 · ${(a.confidence*100).toInt()}% data confidence" } ?: "Building history",color=MarketPalette.analytics(),style=MaterialTheme.typography.labelLarge)
            Text("${a?.maturity?.replace('_',' ') ?: "INITIALIZING"} · ${a?.sessions ?: 0} sampled sessions",style=MaterialTheme.typography.labelSmall)
            Text(observation?.let { "${GatewayProvider.parseQuote(org.json.JSONObject(it.payload)).freshness(Instant.now())} · ${localTime(it.providerTime)}" } ?: row.timestamp?.let { "Saved observation · $it" } ?: "No observation collected",style=MaterialTheme.typography.labelSmall)
            Text("${row.quoteSource ?: "No source"} · analysis ${localTime(analysis?.analyzedAt)}",style=MaterialTheme.typography.labelSmall)
            row.error?.let { Text(feedProblem(it,row.value!=null),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
        }
    }
}
