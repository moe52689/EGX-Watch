package app.egxwatch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun EgxSetupAndDiagnostics(vm:WatchViewModel,onSetup:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config by vm.engineConfig.collectAsStateWithLifecycle()
    val status by vm.engineStatus.collectAsStateWithLifecycle()
    if(settings.providerUrl.isBlank() && config.urls().isEmpty()) {
        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("EGX · setup required",style=MaterialTheme.typography.titleMedium)
            Text("Your watchlist and saved observations are safe. No authorized current-price source is configured. Refresh cannot retrieve EGX quotes until a compatible gateway is added.",style=MaterialTheme.typography.bodySmall)
            Text("Gold Watch has its own connection. No purchased historical data is needed.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onSetup) { Text("Configure EGX gateway") }
        } }
    } else {
        Text(status.message,style=MaterialTheme.typography.bodySmall)
        RefreshDiagnostics(vm,false)
    }
}
@Composable fun RefreshDiagnostics(vm:WatchViewModel,gold:Boolean) {
    val rows by (if(gold) vm.goldDiagnostics else vm.egxDiagnostics).collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick={expanded=!expanded}) { Text("Refresh diagnostics · ${rows.size} source results") }
        if(expanded) {
            if(rows.isEmpty()) Text("Run a manual refresh to see per-source validation results.",style=MaterialTheme.typography.bodySmall)
            rows.forEach { row ->
                HorizontalDivider()
                Text("${row.instrumentId} · ${row.state}",style=MaterialTheme.typography.labelLarge)
                Text(row.provider,style=MaterialTheme.typography.bodySmall)
                Text(row.detail,style=MaterialTheme.typography.bodySmall)
                row.dataTimestamp?.let { Text("Data: $it",style=MaterialTheme.typography.labelSmall) }
                Text("Checked: ${localTime(row.at)}",style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
