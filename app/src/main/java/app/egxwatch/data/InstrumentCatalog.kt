package app.egxwatch.data

import app.egxwatch.domain.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate

/** Identity metadata only; the bundled directory never contains prices. */
object InstrumentCatalog {
    const val STOCKS_URL = "https://scanner.tradingview.com/egypt/scan"
    const val STOCKS_QUERY = """{"filter":[{"left":"exchange","operation":"equal","right":"EGX"}],"columns":["name","description","currency","type","exchange"],"range":[0,1000]}"""
    val fundAliases = mapOf("thndr-t70-fund" to "T70", "ci-the-quant-fund" to "CTQ",
        "beltone-fadda-silver-fund" to "BFA", "az-gold-fund" to "AZG")
    fun fundId(slug: String) = fundAliases[slug]?.let { "EG:FUND:$it" } ?: "SNDUK:$slug"
    fun fundSlug(instrument: Instrument): String = fundAliases.entries.firstOrNull { it.value == instrument.ticker }?.key
        ?: instrument.id.removePrefix("SNDUK:").also { require(instrument.id.startsWith("SNDUK:")) }
    val bundled: List<Instrument> by lazy {
        val body = checkNotNull(javaClass.getResourceAsStream("/instrument-catalog.json")) { "Bundled catalogue missing" }
            .bufferedReader().use { it.readText() }
        val array = JSONObject(body).getJSONArray("instruments")
        (0 until array.length()).map { GatewayProvider.parseInstrument(array.getJSONObject(it)) }
    }
    fun stocks(body: String, verified: String = LocalDate.now().toString()): List<Instrument> {
        val root = JSONObject(body)
        val rows = root.getJSONArray("data")
        require(root.getInt("totalCount") == rows.length()) { "Incomplete stock directory; keeping saved catalogue" }
        val result = (0 until rows.length()).mapNotNull {
            val row = rows.getJSONObject(it); val data = row.getJSONArray("d")
            val ticker = data.getString(0); val type = data.getString(3)
            if (data.getString(4) != "EGX" || type !in setOf("stock", "fund")) return@mapNotNull null
            val currency = data.getString(2)
            require(currency.matches(Regex("[A-Z]{3}")) && data.getString(1).isNotBlank())
            require(row.getString("s") == "EGX:$ticker")
            Instrument("EGX:$ticker", ticker, data.getString(1), if (type == "fund") InstrumentType.ETF else InstrumentType.STOCK,
                currency, "TradingView · EGX identity directory", verified)
        }.distinctBy { it.id }
        require(result.isNotEmpty()) { "Empty stock directory" }
        return result
    }
    fun fundRows(body: String) = Jsoup.parse(body).select("tr").mapNotNull { row ->
        val cells = row.select("td")
        if (cells.size != 4) return@mapNotNull null
        val link = cells[0].selectFirst("a[href*=/eg/funds/]") ?: return@mapNotNull null
        val slug = link.attr("href").substringAfter("/eg/funds/").substringBefore('?').trimEnd('/')
        if (!slug.matches(Regex("[a-zA-Z0-9-]+"))) return@mapNotNull null
        Triple(slug, link.text(), cells)
    }
    fun funds(body: String, verified: String = LocalDate.now().toString()): List<Instrument> {
        val result = fundRows(body).mapNotNull { (slug, name, cells) ->
            val currency = Regex("\\b(EGP|USD|EUR|GBP)\\b").find(cells[3].text())?.value ?: return@mapNotNull null
            Instrument(fundId(slug), fundAliases[slug] ?: slug, name, InstrumentType.FUND, currency,
                "https://snduk.com/eg/funds/$slug?lang=en", verified)
        }.distinctBy { it.id }
        require(result.isNotEmpty()) { "Fund directory unavailable or format changed" }
        return result
    }
}
