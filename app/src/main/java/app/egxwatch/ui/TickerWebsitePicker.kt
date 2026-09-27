package app.egxwatch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

/** Browser shortcuts only. These URLs never enter the market-data repository. */
@Composable fun TickerWebsitePicker() {
    val browser = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("Choose website or custom gateway") }
    var error by remember { mutableStateOf<String?>(null) }
    val websites = listOf(
        "EGX" to "https://www.egx.com.eg/en/prices.aspx",
        "Mubasher" to "https://english.mubasher.info/markets/EGX/",
        "TradingView" to "https://www.tradingview.com/markets/stocks-egypt/"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Public ticker websites", style = MaterialTheme.typography.titleMedium)
        Text("Public pages open in your browser. They do not supply in-app quotes or alerts. Selecting a website does not change your gateway or refresh saved prices.", style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                websites.forEach { (name, url) ->
                    DropdownMenuItem(text = { Text("$name · open website") }, onClick = {
                        expanded = false
                        selected = name
                        error = null
                        try { browser.openUri(url) }
                        catch (_: android.content.ActivityNotFoundException) { error = "No browser available. Install or enable a browser to open this website." }
                        catch (_: IllegalArgumentException) { error = "No browser available. Install or enable a browser to open this website." }
                        catch (_: SecurityException) { error = "Android blocked opening the browser." }
                    })
                }
                DropdownMenuItem(text = { Text("Custom authorized gateway") }, onClick = {
                    expanded = false
                    selected = "Custom authorized gateway"
                    error = null
                })
            }
        }
        if (selected == "Custom authorized gateway") {
            Text("Enter your authorized HTTPS API base URL below, then save Settings. A public website URL is not a compatible gateway.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
