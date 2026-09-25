package app.egxwatch.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
@Composable fun AlertsScreen(vm:WatchViewModel,onOpen:(String)->Unit) {
 val alerts by vm.centerAlerts.collectAsStateWithLifecycle();var filter by remember { mutableStateOf("ALL") }
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { Text("Alerts",style=MaterialTheme.typography.headlineLarge)
   Text("${alerts.count { !it.read }} unread · latest 500 visible events",style=MaterialTheme.typography.bodySmall)
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    listOf("ALL","STOCKS","GOLD","SYSTEM").forEach { category -> FilterChip(filter==category,{filter=category},label={Text(category)}) }
   }
   Text("Configure stock thresholds in instrument details and analytics settings; gold rules are in the Gold tab.",style=MaterialTheme.typography.bodySmall)
  }
  val visible=alerts.filter { filter=="ALL" || it.category==filter }
  if(visible.isEmpty()) item { Text("No alerts in this category yet.") }
  items(visible,key={it.id}) { event ->
   OutlinedCard(onClick={vm.markRead(event.id);event.instrumentId?.let(onOpen)},modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
     Text("${if(event.read) "Read" else "New"} · ${event.category}",style=MaterialTheme.typography.labelSmall)
     Text(event.title,style=MaterialTheme.typography.titleMedium)
     Text(event.body,style=MaterialTheme.typography.bodyMedium)
     Text("Data ${localTime(event.timestamp)} · ${event.provider ?: "System"}",style=MaterialTheme.typography.labelSmall)
     Row { TextButton(onClick={vm.markRead(event.id)}) { Text("Mark read") };TextButton(onClick={vm.dismissAlert(event.id)}) { Text("Dismiss") } }
    }
   }
  }
 }
}
