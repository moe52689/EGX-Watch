package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class FreePublicProvider(private val feeds: PublicFeedClient = PublicFeedClient()) : MarketDataProvider {
    @Volatile private var catalogue = DirectoryProvider.instruments
    override suspend fun search(query: String) = catalogue.filter {
        it.ticker.contains(query.trim(), true) || it.name.contains(query.trim(), true)
    }
    override suspend fun resolve(id: String) = catalogue.singleOrNull { it.id == id }
        ?: throw IOException("Instrument not in the validated directory. Refresh Discover and try again.")
    suspend fun refreshDirectory(): String = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()
        var stocks = catalogue.filter { it.type != InstrumentType.FUND }
        var funds = catalogue.filter { it.type == InstrumentType.FUND }
        try {
            val response = feeds.get(InstrumentCatalog.STOCKS_URL, InstrumentCatalog.STOCKS_QUERY) { InstrumentCatalog.stocks(it) }
            stocks = InstrumentCatalog.stocks(response.body, response.fetchedAt.atZone(ZoneOffset.UTC).toLocalDate().toString())
            response.warning?.let(notes::add)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { notes += "Stock directory: ${e.message}; using saved identities" }
        try {
            val response = fundFeed()
            funds = InstrumentCatalog.funds(response.body, response.fetchedAt.atZone(ZoneOffset.UTC).toLocalDate().toString())
            response.warning?.let(notes::add)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { notes += "Fund directory: ${e.message}; using saved identities" }
        catalogue = (DirectoryProvider.referenceInstruments + stocks + funds).distinctBy { it.id }.sortedBy { it.ticker }
        "${catalogue.count { it.type == InstrumentType.STOCK }} stocks · ${catalogue.count { it.type == InstrumentType.FUND }} funds · ${catalogue.count { it.type == InstrumentType.ETF }} ETFs" +
            if (notes.isEmpty()) " · Directory refreshed" else "\n" + notes.joinToString("\n")
    }
    private suspend fun stockFeed(force: Boolean = false) = feeds.get(EGXPILOT, force = force) {
        require(JSONObject(it).getJSONArray("stocks").length() > 0) { "Empty stock response" }
    }
    private suspend fun fundFeed(force: Boolean = false) = feeds.get(SNDUK, force = force) { InstrumentCatalog.funds(it) }
    override suspend fun quote(instrument: Instrument): Quote = withContext(Dispatchers.IO) {
        val quote = when {
            instrument.id == "EG:FUND:AZG" -> {
                val candidates = mutableListOf<Quote>(); val errors = mutableListOf<String>()
                try {
                    val response = feeds.get(AZIMUT) { parseAzimut(instrument, it) }
                    val nav = parseAzimut(instrument, response.body).copy(notice = response.warning)
                    validateQuote(instrument, nav); candidates += nav
                } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "Azimut: ${e.message}" }
                try {
                    val response = fundFeed()
                    val nav = parseSnduk(instrument, response.body).copy(notice = response.warning)
                    validateQuote(instrument, nav); candidates += nav
                } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "SNDUK: ${e.message}" }
                candidates.maxByOrNull { it.timestamp } ?: throw IOException(errors.joinToString("; "))
            }
            instrument.type == InstrumentType.FUND -> {
                val response = fundFeed()
                parseSnduk(instrument, response.body).copy(notice = response.warning)
            }
            instrument.id.startsWith("EGX:") -> {
                val response = stockFeed()
                parsePublicStock(instrument, response.body).copy(notice = response.warning)
            }
            else -> throw IOException("No public quote source for ${instrument.ticker}")
        }
        validateQuote(instrument, quote)
        quote
    }
    suspend fun health(): String = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        suspend fun inspect(name: String, block: suspend () -> String) {
            try { lines += "$name: ${block()}" } catch (e: CancellationException) { throw e }
            catch (e: Exception) { lines += "$name: FAILED · ${e.message}" }
        }
        inspect("EGXpilot") {
            val response = stockFeed(true)
            val sample = parsePublicStock(resolve("EGX:CCAP"), response.body); validateQuote(resolve("EGX:CCAP"), sample)
            "${response.warning ?: "Connected"} · ${JSONObject(response.body).getJSONArray("stocks").length()} source rows · CCAP ${sample.value} · snapshot ${sample.timestamp}"
        }
        inspect("SNDUK") {
            val response = fundFeed(true)
            val sample = parseSnduk(resolve("EG:FUND:T70"), response.body); validateQuote(resolve("EG:FUND:T70"), sample)
            "${response.warning ?: "Connected"} · ${InstrumentCatalog.funds(response.body).size} funds · T70 NAV ${sample.value}"
        }
        inspect("Azimut") {
            val instrument = resolve("EG:FUND:AZG")
            val response = feeds.get(AZIMUT, force = true) { parseAzimut(instrument, it) }
            val sample = parseAzimut(instrument, response.body); validateQuote(instrument, sample)
            "${response.warning ?: "Connected"} · AZG NAV ${sample.value}"
        }
        lines.joinToString("\n\n")
    }
    override suspend fun marketStatus() = MarketStatus("Unknown", "Public stock snapshots are indicative. Fund NAVs retain their valuation dates. Exchange status is not verified.", null)
    companion object {
        const val AZIMUT = "https://app.azimut.eg/api/fund/list?size=100&web=true"
        const val SNDUK = "https://snduk.com/eg/page/mutual-funds-prices-today?lang=en"
        const val EGXPILOT = "https://egxpilot.com/api/stocks/all"
        private fun valuationDate(value: String) = LocalDate.parse(value).atStartOfDay(ZoneId.of("Africa/Cairo")).toInstant()
        fun parseAzimut(instrument: Instrument, body: String): Quote {
            require(instrument.id == "EG:FUND:AZG")
            val funds = JSONObject(body).getJSONObject("response").getJSONObject("funds").getJSONArray("dataList")
            val fund = (0 until funds.length()).map { funds.getJSONObject(it) }.singleOrNull { it.getInt("id") == 16 }
                ?: throw IOException("AZG issuer fund ID 16 is missing")
            require(fund.getString("slug").matches(Regex("az-gold(?:-[0-9]+)?"))) { "AZG issuer identity changed" }
            require(fund.getJSONObject("currency").getString("symbol") == "EGP")
            val nav = fund.getJSONObject("last_nav"); require(nav.getInt("fund_id") == 16)
            return Quote(instrument.id, nav.get("nav").toString().toBigDecimal(), "EGP", DataKind.NAV,
                valuationDate(nav.getString("date")), "Azimut Egypt · issuer NAV", timestampBasis = TimestampBasis.VALUATION_DATE)
        }
        fun parseSnduk(instrument: Instrument, body: String): Quote {
            val slug = InstrumentCatalog.fundSlug(instrument)
            val rows = InstrumentCatalog.fundRows(body).filter { it.first == slug }
            require(rows.size == 1) { "Fund NAV table changed or instrument is missing" }
            val cells = rows.single().third
            val date = LocalDate.parse(cells[2].text(), DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US))
            val value = cells[3].text().replace('\u00a0', ' ').trim()
            require(value.matches(Regex("${Regex.escape(instrument.currency)}\\s+[0-9,]+(?:\\.[0-9]+)?"))) { "NAV currency/value missing" }
            return Quote(instrument.id, value.removePrefix(instrument.currency).trim().replace(",", "").toBigDecimal(), instrument.currency, DataKind.NAV,
                valuationDate(date.toString()), "SNDUK · published fund NAV", timestampBasis = TimestampBasis.VALUATION_DATE)
        }
        fun parsePublicStock(instrument: Instrument, body: String): Quote {
            require(instrument.id == "EGX:${instrument.ticker}" && instrument.type != InstrumentType.FUND)
            val stocks = JSONObject(body).getJSONArray("stocks")
            val row = (0 until stocks.length()).map { stocks.getJSONObject(it) }.singleOrNull { it.getString("Symbol") == instrument.ticker }
                ?: throw IOException("${instrument.ticker}: no price in the current free stock feed. Last saved value is retained.")
            require(row.getString("StockName").isNotBlank()) { "Stock identity missing" }
            if (instrument.ticker == "CCAP") require(row.getString("StockName").contains("Qalaa", true)) { "Stock identity changed" }
            if (instrument.ticker == "BINV") require(row.getString("StockName").contains("B Investments", true)) { "Stock identity changed" }
            return Quote(instrument.id, row.get("LastPrice").toString().toBigDecimal(), instrument.currency, DataKind.INDICATIVE,
                Instant.parse(row.getString("CreatedAt")), "EGXpilot · indicative, exchange timing unverified",
                timestampBasis = TimestampBasis.PROVIDER_SNAPSHOT)
        }
    }
}
