package app.egxwatch.data

import app.egxwatch.domain.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.*

object FeedParsers {
    const val STOCK_QUERY = """{"filter":[{"left":"exchange","operation":"equal","right":"EGX"}],"columns":["name","description","currency","type","exchange","close","update_mode"],"range":[0,1000]}"""
    fun stock(instrument: Instrument, response: FeedResponse): Quote {
        val rows = JSONObject(response.body).getJSONArray("data")
        val row = (0 until rows.length()).map { rows.getJSONObject(it) }.singleOrNull { it.getString("s") == instrument.id }
            ?: error("No published price for this instrument in the current source")
        val d = row.getJSONArray("d")
        require(d.getString(0) == instrument.ticker && d.getString(2) == instrument.currency && d.getString(4) == "EGX") { "Stock identity or currency changed" }
        require(d.getString(3) == "stock" && instrument.type == InstrumentType.STOCK && !d.isNull(5)) { "No published stock price" }
        val delay = Regex("delayed_streaming_(\\d+)").matchEntire(d.optString(6))?.groupValues?.get(1)?.toInt()?.div(60)
        return Quote(instrument.id, d.get(5).toString().toBigDecimal(), instrument.currency, DataKind.INDICATIVE,
            response.fetchedAt, "TradingView · public screener" + (delay?.let { " · reports ${it}m delay" } ?: ""), delay,
            TimestampBasis.RETRIEVAL_TIME, response.warning)
    }
    fun etf(instrument: Instrument, response: FeedResponse): Quote {
        require(instrument.id == "EGX:EGX30ETF" && instrument.currency == "EGP")
        val document = Jsoup.parse(response.body)
        require(document.text().contains("EGX30ETF")) { "ETF issuer identity missing" }
        val box = document.select(".box-content").single { it.select("p").any { p -> p.text().contains("صافي قيمة اصول الوثيقة") } }
        val dateText = box.select("p").last()!!.text()
        val parts = Regex("(\\d{1,2})\\s+(\\S+)\\s+(\\d{4})").find(dateText) ?: error("ETF valuation date unavailable")
        val months = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        val month = months.indexOf(parts.groupValues[2]) + 1
        require(month > 0)
        val date = LocalDate.of(parts.groupValues[3].toInt(), month, parts.groupValues[1].toInt())
        return Quote(instrument.id, box.selectFirst("h2.number")!!.text().replace(",", "").toBigDecimal(), "EGP", DataKind.NAV,
            date.atStartOfDay(ZoneId.of("Africa/Cairo")).toInstant(), "EGX30ETF issuer · NAV, not exchange price",
            timestampBasis = TimestampBasis.VALUATION_DATE, notice = response.warning)
    }
}
