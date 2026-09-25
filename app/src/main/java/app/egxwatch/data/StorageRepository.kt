package app.egxwatch.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream

class StorageRepository(private val db:WatchDatabase) {
 suspend fun erase(id:String?)=db.withTransaction {
  val ids=if(id==null) db.observationDao().allSeries().map { it.instrumentId }.distinct() else listOf(id)
  ids.forEach { security ->
   db.observationDao().deleteObservations(security);db.observationDao().deleteSessions(security);db.observationDao().deleteSeries(security)
   listOf("analyses","market_snapshots","historical_prices","opportunity_state").forEach { table ->
    db.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE instrumentId=?",arrayOf(security))
   }
  }
  if(id==null || id=="GLOBAL:XAUUSD") db.openHelper.writableDatabase.execSQL("DELETE FROM gold_rule_state")
 }
 /** Consistent logical SQLite export; streams rows under a Room transaction rather than loading tables. */
 suspend fun export(output:OutputStream,password:CharArray)=withContext(Dispatchers.IO) {
  db.withTransaction {
   MarketArchive.write(output,password) { encrypted ->
    val writer=encrypted.bufferedWriter(Charsets.UTF_8)
    writer.appendLine(JSONObject().put("format","EGX logical market database").put("version",5).put("createdAt",java.time.Instant.now().toString()).toString())
    val sqlite=db.openHelper.writableDatabase
    val tables=listOf("watchlists","instruments","settings","engine_config","market_observations","observed_sessions","collection_series","analyses","market_snapshots","alerts","opportunity_events","opportunity_state","provider_health","engine_status","gold_config","gold_status","gold_rules","gold_rule_state","alert_center","forward_preferences")
    tables.forEach { table ->
     sqlite.query("SELECT * FROM $table").use { cursor ->
      while(cursor.moveToNext()) {
       val row=JSONObject()
       cursor.columnNames.forEachIndexed { index,name -> row.put(name,when(cursor.getType(index)) {
        android.database.Cursor.FIELD_TYPE_NULL->JSONObject.NULL
        android.database.Cursor.FIELD_TYPE_INTEGER->cursor.getLong(index)
        android.database.Cursor.FIELD_TYPE_FLOAT->cursor.getDouble(index)
        else->cursor.getString(index)
       }) }
       writer.appendLine(JSONObject().put("table",table).put("row",row).toString())
      }
     }
    };writer.flush()
   }
  }
 }
}
