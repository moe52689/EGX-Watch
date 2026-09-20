package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** No API keys; public website feeds, not exchange-entitled real-time market data. */
class FreePublicProvider : MarketDataProvider {
    private val directory = DirectoryProvider()
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val cache = mutableMapOf<String, Pair<Instant, String>>()
    private val lock = Mutex()
    private suspend fun fetch(url: String): String = lock.withLock {
        val cached = cache[url]
        if (cached != null && Duration.between(cached.first, Instant.now()).seconds < 60) return@withLock cached.second
        val value = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).header("User-Agent", "EGXWatch/1.0 (personal market monitor)").build()).execute().use {
                if (!it.isSuccessful) throw IOException("Public feed returned HTTP ${it.code}; try again later")
                val body = it.body ?: throw IOException("Public feed has no response")
                // Read at most 4 MiB; a changed/error page must never become a price.
                val output = java.io.ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var length = input.read(buffer)
                    while (length != -1) {
                        require(output.size() + length <= 4 * 1024 * 1024) { "Public feed response too large" }
                        output.write(buffer, 0, length); length = input.read(buffer)
                    }
                }
                output.toString("UTF-8")
            }
        }
        cache[url] = Instant.now() to value
        value
    }
    override suspend fun search(query: String) = directory.search(query)
    override suspend fun resolve(id: String) = directory.resolve(id)
    override suspend fun quote(instrument: Instrument): Quote {
        val quote = when (instrument.id) {
            "EG:FUND:AZG" -> parseAzimut(instrument, fetch(AZIMUT))
            "EG:FUND:T70", "EG:FUND:CTQ", "EG:FUND:BFA" -> parseSnduk(instrument, fetch(SNDUK))
            "EGX:CCAP", "EGX:BINV" -> parsePublicStock(instrument, fetch(EGXPILOT))
            else -> throw IOException("No verified free feed mapping for ${instrument.ticker}. Connect a gateway with coverage.")
        }
        validateQuote(instrument, quote)
        return quote
    }
    override suspend fun marketStatus() = MarketStatus("Unknown", "Free feeds do not verify exchange hours or holidays. Stock prices are indicative; fund values are published NAVs.", null)
    companion object {
        const val AZIMUT = "https://app.azimut.eg/api/fund/list?size=100&web=true"
        const val SNDUK = "https://snduk.com/eg/page/mutual-funds-prices-today?lang=en"
        const val EGXPILOT = "https://egxpilot.com/api/stocks/all"
        private fun valuationDate(value: String) = LocalDate.parse(value).atStartOfDay(ZoneId.of("Africa/Cairo")).toInstant()
        fun parseAzimut(instrument: Instrument, body: String): Quote {
            require(instrument.id == "EG:FUND:AZG")
            val funds = JSONObject(body).getJSONObject("response").getJSONObject("funds").getJSONArray("dataList")
            val fund = (0 until funds.length()).map { funds.getJSONObject(it) }.single { it.getInt("id") == 16 && it.getString("slug") == "az-gold-2" }
            require(fund.getJSONObject("currency").getString("symbol") == "EGP")
            val nav = fund.getJSONObject("last_nav")
            require(nav.getInt("fund_id") == 16)
            return Quote(instrument.id, nav.get("nav").toString().toBigDecimal(), "EGP", DataKind.NAV,
                valuationDate(nav.getString("date")), "Azimut Egypt · issuer NAV", timestampBasis = TimestampBasis.VALUATION_DATE)
        }
        fun parseSnduk(instrument: Instrument, body: String): Quote {
            val slug = when (instrument.ticker) {
                "T70" -> "thndr-t70-fund"; "CTQ" -> "ci-the-quant-fund"; "BFA" -> "beltone-fadda-silver-fund"
                else -> throw IOException("No SNDUK mapping")
            }
            val rows = Jsoup.parse(body).select("tr").filter { row -> row.select("a").any { it.attr("href") == "/eg/funds/$slug?lang=en" } }
            require(rows.size == 1) { "Fund NAV table changed or instrument is missing" }
            val cells = rows.single().select("td")
            require(cells.size == 4) { "Fund NAV table format changed" }
            val date = LocalDate.parse(cells[2].text(), DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US))
            val value = cells[3].text().replace('\u00a0', ' ').trim()
            require(value.matches(Regex("EGP\\s+[0-9,]+(?:\\.[0-9]+)?"))) { "NAV currency/value missing" }
            return Quote(instrument.id, value.removePrefix("EGP").trim().replace(",", "").toBigDecimal(), "EGP", DataKind.NAV,
                valuationDate(date.toString()), "SNDUK · published fund NAV", timestampBasis = TimestampBasis.VALUATION_DATE)
        }
        fun parsePublicStock(instrument: Instrument, body: String): Quote {
            require(instrument.id in setOf("EGX:CCAP", "EGX:BINV")) { "Unverified public stock mapping" }
            val json = JSONObject(body)
            val stocks = json.getJSONArray("stocks")
            val row = (0 until stocks.length()).map { stocks.getJSONObject(it) }.single { it.getString("Symbol") == instrument.ticker }
            val name = row.getString("StockName")
            require(if (instrument.ticker == "CCAP") name.contains("Qalaa", true) else name.contains("B Investments", true)) { "Public stock identity changed" }
            return Quote(instrument.id, row.get("LastPrice").toString().toBigDecimal(), instrument.currency, DataKind.INDICATIVE,
                Instant.parse(row.getString("CreatedAt")), "EGXpilot · indicative, exchange timing unverified",
                timestampBasis = TimestampBasis.PROVIDER_SNAPSHOT)
        }
    }
}
