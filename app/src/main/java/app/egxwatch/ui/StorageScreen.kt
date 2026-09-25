package app.egxwatch.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
@Composable fun StorageScreen(vm:WatchViewModel) {
 val stats by vm.storageStats.collectAsStateWithLifecycle();val series by vm.collections.collectAsStateWithLifecycle()
 var password by remember { mutableStateOf("") };var delete by remember { mutableStateOf<String?>(null) }
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
  if(uri!=null) vm.exportHistory(uri,password.toCharArray());password=""
 }
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  item { Text("Your collected history",style=MaterialTheme.typography.headlineMedium)
   Text("Database: ${vm.storageBytes()/1024} KiB\n${stats.observations} observations collected\nOldest collection: ${localTime(stats.oldestAt)}\nNewest collection: ${localTime(stats.newestAt)}") }
  item { Text("Raw observations are retained for 90 days when a sampled session summary exists. Session aggregates preserve the longer view. Unusable observations older than 90 days are removed, retaining each series’ latest record.",style=MaterialTheme.typography.bodySmall) }
  item { Text("Encrypted export",style=MaterialTheme.typography.titleLarge)
   Text("AES-256-GCM archive of the market database, protected by your password. Keep the password separately; it cannot be recovered. Export is a portable logical database; in-app restore is not yet supported.",style=MaterialTheme.typography.bodySmall)
   OutlinedTextField(password,{password=it},label={Text("Export password (12+ characters)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
   Button(onClick={export.launch("EGX-Watch-${java.time.LocalDate.now()}.egxenc")},enabled=password.length>=12) { Text("Choose location & export") } }
  item { Text("Reset history",style=MaterialTheme.typography.titleLarge)
   Text("Deleting collection history resets charts and maturity. Your watchlist, latest saved quote and notification history remain.",style=MaterialTheme.typography.bodySmall)
   series.map { it.instrumentId }.distinct().forEach { id -> TextButton(onClick={delete=id}) { Text("Delete $id history") } }
   OutlinedButton(onClick={delete="ALL"}) { Text("Delete all collected history") } }
 }
 delete?.let { target -> AlertDialog(onDismissRequest={delete=null},title={Text("Delete collected history?")},text={Text("${if(target=="ALL") "All instruments" else target}: this permanently removes collected observations and resets analytics maturity. Export first if needed.")},confirmButton={TextButton(onClick={vm.eraseHistory(target.takeUnless { it=="ALL" });delete=null}) { Text("Delete history") }},dismissButton={TextButton(onClick={delete=null}) { Text("Cancel") }}) }
}
