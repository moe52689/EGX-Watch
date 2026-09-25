package app.egxwatch.data

import app.egxwatch.domain.*
import org.json.JSONObject

/** Broker codes cross-checked against Thndr's published fund directory; IDs never change. */
object FundDirectory {
    val codes = mapOf(
        "misr-takaful-fund-mtf" to "MTF", "azimut-fixed-income-fund-edkhar-az" to "AZS",
        "azimut-nasser-islamic-money-fund" to "AZN", "beltone-b-secure-fund" to "BSC",
        "diamond-money-market-fund" to "ADM", "makaseb-fund-gig-shariah" to "PGM",
        "pfi-cashi-money-market-fund" to "PCM", "zaldi-star-fund" to "ZST",
        "stream-fixed-income-fund" to "CCS", "alahly-tamayoz-money-market-fund" to "ATD",
        "afaaq-fixed-income-fund" to "AAF", "el-fanar-money-market-fund" to "AEF",
        "bareeq-fixed-income-fund" to "ABR", "istsmar-w-aman-fixed-income-fund" to "AIS",
        "granite-egp-money-market-fund" to "GRA", "iskan-money-market-fund" to "AIM",
        "gosour-equity-fund" to "AGO", "momentum-cairo-capital-fund" to "CCM",
        "misr-equity-fund" to "CI30", "misr-shariah-equity-fund" to "CMS", "ci-ipo-fund" to "CIP", "ci-20-hd-fund" to "C20",
        "ci-real-estate-fund-cre" to "CRE", "ci-telecom-it-sectoral-fund" to "CTI",
        "CI-exporters-sectorial-fund" to "CEX", "ci-consumer-sectorial-fund" to "CCB",
        "ci-financial-fintech-sectoral-fund-cff" to "CFF", "beltone-wafra" to "BWA",
        "beltone-meya-100" to "BMM", "beltone-b-alpha-fund" to "BAL", "beltone-b35-fund" to "B35",
        "beltone-b70-fund" to "B70", "beltone-industrial-fund" to "BIN", "beltone-real-estate-fund" to "BRE",
        "beltone-consumer-fund" to "BCO", "beltone-financial-fund" to "BFI", "bdc-first-fund" to "BFF",
        "sahmy-70-fund-ni-capital" to "NCS", "naeem-misr-sharia-fund" to "NMF",
        "azimut-equity-opportunities-fund" to "AZO", "azimut-opportunities-shariah-az" to "ASO",
        "azimut-az-lv-equity-opportunities-fund" to "ALV", "nbk-al-mizan-balanced-fund" to "NAM",
        "zaldi-elmasry-fund" to "ZEM", "kenz-foras-equity-fund" to "AKO", "kenz-egx33-shariah-fund" to "AKS",
        "odin-equity-fund-trend" to "OTR", "al-ahly-dahab-fund" to "ADA", "sabayek-fund-beltone-gold" to "BSB",
        "ciam-gold-fund-gold-masr" to "CGO", "fadda-mubasher-fund" to "MSI")
    val issuerIds = mapOf(16 to "EG:FUND:AZG", 6 to "SNDUK:azimut-equity-opportunities-fund",
        18 to "SNDUK:azimut-opportunities-shariah-az", 23 to "SNDUK:azimut-az-lv-equity-opportunities-fund",
        5 to "SNDUK:azimut-fixed-income-fund-edkhar-az", 14 to "SNDUK:azimut-nasser-islamic-money-fund",
        21 to "SNDUK:thndr-savings-clouds-az", 12 to "SNDUK:menthum-money-market-fund",
        11 to "SNDUK:sanadi-fund-bank-nxt", 8 to "SNDUK:alkhabeer-fund", 1 to "SNDUK:mazaya-money-market-fund")
    fun issuerId(instrument: Instrument): Int? = issuerIds.entries.firstOrNull { it.value == instrument.id }?.key
        ?: instrument.id.takeIf { it.startsWith("AZIMUT:") }?.substringAfter(':')?.toIntOrNull()
    fun decorate(instrument: Instrument) = codes[instrument.id.removePrefix("SNDUK:")]?.let { instrument.copy(ticker = it) } ?: instrument
    fun issuer(body: String, date: String): List<Instrument> {
        val rows = JSONObject(body).getJSONObject("response").getJSONObject("funds").getJSONArray("dataList")
        return (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
            val id = row.getInt("id")
            Instrument(issuerIds[id] ?: "AZIMUT:$id", "AZ-$id", row.getString("name"), InstrumentType.FUND,
                row.getJSONObject("currency").getString("symbol"), "Azimut Egypt issuer directory", date)
        }.onEach { require(it.name.isNotBlank() && it.currency.matches(Regex("[A-Z]{3}"))) }
    }
}
