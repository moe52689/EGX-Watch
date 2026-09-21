package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Offline identity directory only. It contains no prices and makes no live-validation claim. */
class DirectoryProvider : MarketDataProvider {
    companion object {
        val referenceInstruments = listOf(
            Instrument("EGX:CCAP", "CCAP", "Qalaa for Financial Investments", InstrumentType.STOCK,
                source = "https://www.arabfinance.com/en/Home/CompanyProfile/CCAP", verifiedAt = "2026-09-20"),
            Instrument("EGX:BINV", "BINV", "B Investments Holding", InstrumentType.STOCK,
                source = "https://www.arabfinance.com/en/Home/CompanyProfile/BINV", verifiedAt = "2026-09-20"),
            Instrument("EG:FUND:T70", "T70", "Thndr EGX70 Fund", InstrumentType.FUND,
                source = "https://support.thndr.app/en/articles/651226-mutual-fund-cutoff-times", verifiedAt = "2026-09-20"),
            Instrument("EG:FUND:CTQ", "CTQ", "CI The Quant Fund", InstrumentType.FUND,
                source = "https://support.thndr.app/en/articles/651226-mutual-fund-cutoff-times", verifiedAt = "2026-09-20"),
            Instrument("EG:FUND:AZG", "AZG", "AZ Gold Fund", InstrumentType.FUND,
                source = "https://support.thndr.app/en/articles/651226-mutual-fund-cutoff-times", verifiedAt = "2026-09-20"),
            Instrument("EG:FUND:BFA", "BFA", "Beltone Fadda Fund", InstrumentType.FUND,
                source = "https://support.thndr.app/en/articles/651226-mutual-fund-cutoff-times", verifiedAt = "2026-09-20"),
            Instrument("EGX:EGX30ETF", "EGX30ETF", "EGX 30 Index ETF", InstrumentType.ETF,
                source = "https://www.egx30etf.com/", verifiedAt = "2026-09-20")
        )
        val instruments: List<Instrument> by lazy { (referenceInstruments + InstrumentCatalog.bundled).distinctBy { it.id }.sortedBy { it.ticker } }
    }
    override suspend fun search(query: String) = instruments.filter {
        it.ticker.contains(query.trim(), true) || it.name.contains(query.trim(), true)
    }
    override suspend fun resolve(id: String) = instruments.singleOrNull { it.id == id }
        ?: throw IOException("Instrument is not in the verified directory. Connect a provider to search more instruments.")
    override suspend fun quote(instrument: Instrument): Quote = throw IOException("Connect a data provider to receive prices and NAVs.")
    override suspend fun marketStatus() = MarketStatus("Unknown", "Connect a provider for exchange status and holiday information.", null)
}

/** Provider credentials belong to the server. This client only stores a public HTTPS base URL. */
class GatewayProvider(baseUrl: String) : MarketDataProvider {
    private val base = validateBaseUrl(baseUrl).toHttpUrl()
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private suspend fun get(vararg path: String, query: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val url = base.newBuilder().apply {
            path.forEach { addPathSegment(it) }
            query?.let { addQueryParameter("q", it) }
        }.build()
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Provider returned HTTP ${response.code}. Check connection and coverage.")
            val body = response.body ?: throw IOException("Empty provider response")
            val source = body.source()
            source.request(1_048_577)
            val bytes = source.buffer.readByteArray()
            require(bytes.size <= 1_048_576) { "Provider response too large" }
            JSONObject(String(bytes, Charsets.UTF_8))
        }
    }
    override suspend fun search(query: String): List<Instrument> {
        val array = get("instruments", query = query.trim()).getJSONArray("instruments")
        require(array.length() <= 500) { "Too many search results; refine your query" }
        return (0 until array.length()).map { parseInstrument(array.getJSONObject(it)) }
    }
    override suspend fun resolve(id: String): Instrument = parseInstrument(get("instruments", id)).also {
        require(it.id == id) { "Provider returned a different instrument" }
    }
    override suspend fun quote(instrument: Instrument) = parseQuote(get("quotes", instrument.id)).also { validateQuote(instrument, it) }
    override suspend fun marketStatus(): MarketStatus {
        val json = get("market-status")
        val state = json.getString("state")
        require(state in setOf("OPEN", "CLOSED", "HALTED", "UNKNOWN"))
        return MarketStatus(state.lowercase().replaceFirstChar { it.uppercase() }, json.getString("detail"), Instant.parse(json.getString("timestamp")))
    }
    companion object {
        fun validateBaseUrl(input: String): String {
            val url = input.trim().toHttpUrl()
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "Use an HTTPS base URL without credentials, query parameters, or fragments"
            }
            return url.toString().trimEnd('/') + "/"
        }
        fun parseInstrument(json: JSONObject): Instrument {
            require(json.getBoolean("validated")) { "Provider has not validated this instrument" }
            val result = Instrument(json.getString("id"), json.getString("ticker"), json.getString("name"),
                InstrumentType.valueOf(json.getString("type")), json.getString("currency"),
                json.getString("source"), json.getString("verifiedAt"))
            require(result.id.isNotBlank() && result.id.length <= 160 && result.ticker.isNotBlank() && result.ticker.length <= 160)
            require(result.name.isNotBlank() && result.name.length <= 200 && result.source.isNotBlank())
            require(result.currency.matches(Regex("[A-Z]{3}")))
            java.time.LocalDate.parse(result.verifiedAt)
            return result
        }
        fun parseQuote(json: JSONObject) = Quote(json.getString("instrumentId"), json.getString("value").toBigDecimal(),
            json.getString("currency"), DataKind.valueOf(json.getString("kind")), Instant.parse(json.getString("timestamp")),
            json.getString("source"), if (json.has("delayMinutes") && !json.isNull("delayMinutes")) json.getInt("delayMinutes") else null,
            TimestampBasis.valueOf(json.optString("timestampBasis", "EXCHANGE")))
}
}
