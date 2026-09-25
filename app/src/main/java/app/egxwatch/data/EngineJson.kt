package app.egxwatch.data

import app.egxwatch.domain.*
import org.json.*
import java.time.*

object EngineJson {
    fun quote(q:Quote):String = JSONObject().put("instrumentId",q.instrumentId).put("value",q.value.display()).put("currency",q.currency)
        .put("kind",q.kind.name).put("timestamp",q.timestamp.toString()).put("source",q.source).put("timestampBasis",q.timestampBasis.name)
        .put("delayMinutes",q.delayMinutes).put("notice",q.notice).put("open",q.fields.open?.display()).put("previousClose",q.fields.previousClose?.display())
        .put("high",q.fields.high?.display()).put("low",q.fields.low?.display()).put("volume",q.fields.volume)
        .put("bid",q.fields.bid?.display()).put("ask",q.fields.ask?.display()).toString()
    fun analysis(a:StructuredAnalysis):String=JSONObject().put("status",a.status).put("score",a.score).put("confidence",a.confidence)
        .put("components",JSONObject(a.components)).put("indicators",JSONObject(a.indicators)).put("drivers",JSONArray(a.drivers)).put("risks",JSONArray(a.risks)).toString()
    fun analysis(value:String):StructuredAnalysis {
        val j=JSONObject(value)
        fun strings(key:String)=j.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
        val c=j.getJSONObject("components");val i=j.getJSONObject("indicators")
        return StructuredAnalysis(j.getString("status"),if(j.has("score")) j.getInt("score") else null,j.getDouble("confidence"),
            c.keys().asSequence().associateWith(c::getInt),i.keys().asSequence().associateWith(i::getDouble),strings("drivers"),strings("risks"))
    }
    fun history(j:JSONObject):PriceHistory {
        val a=j.getJSONArray("candles");require(a.length()<=1000)
        fun JSONObject.number(key:String)=if(has(key)&&!isNull(key)) getDouble(key) else null
        val bars=(0 until a.length()).map { a.getJSONObject(it) }.map { c -> Candle(LocalDate.parse(c.getString("date")),c.getDouble("close"),c.number("open"),c.number("high"),c.number("low"),
            if(c.has("volume")&&!c.isNull("volume")) c.getLong("volume") else null) }
        val expected=j.getJSONArray("expectedDates");require(expected.length()<=1000)
        return PriceHistory(j.getString("instrumentId"),j.getString("currency"),j.getString("source"),DataKind.valueOf(j.getString("kind")),
            j.getBoolean("comparable"),Instant.parse(j.getString("asOf")),(0 until expected.length()).map { LocalDate.parse(expected.getString(it)) },bars)
    }
    fun history(h:PriceHistory):String=JSONObject().put("instrumentId",h.instrumentId).put("currency",h.currency).put("source",h.source).put("kind",h.kind.name)
        .put("comparable",h.comparable).put("asOf",h.asOf.toString()).put("expectedDates",JSONArray(h.expectedDates.map { it.toString() }))
        .put("candles",JSONArray(h.candles.map { b -> JSONObject().put("date",b.date.toString()).put("close",b.close).put("open",b.open).put("high",b.high).put("low",b.low).put("volume",b.volume) })).toString()
}
