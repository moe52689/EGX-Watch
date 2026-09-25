package app.egxwatch.data

import app.egxwatch.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Documented, keyless current-price endpoint; historical/keyed routes are never called. */
class GoldMarketProvider:MarketDataProvider {
    private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .callTimeout(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private var saved:Pair<Instant,Quote>?=null
    override suspend fun search(query:String)=listOf(GlobalGold.instrument).filter { it.ticker.contains(query,true) || it.name.contains(query,true) }
    override suspend fun resolve(id:String)=GlobalGold.instrument.also { require(it.id==id) }
    override suspend fun marketStatus()=MarketStatus("UNKNOWN","Provider API is available 24/7; this is not an exchange-open assertion",null)
    override suspend fun quote(instrument:Instrument):Quote {
        require(instrument.id==GlobalGold.instrument.id)
        saved?.takeIf { Instant.now()<it.first.plusSeconds(30) }?.let { return it.second }
        val body=suspendCancellableCoroutine<String> { continuation ->
            val call=client.newCall(Request.Builder().url("https://api.gold-api.com/price/XAU/USD").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) { if(continuation.isActive) continuation.resumeWith(Result.failure(IOException("Gold source unavailable"))) }
                override fun onResponse(call:Call,response:Response) {
                    try { val value=response.use {
                        if(!it.isSuccessful) throw ProviderHttpException(it.code,it.header("Retry-After")?.toLongOrNull())
                        val source=(it.body ?: throw IOException("Empty gold response")).source();source.request(65537)
                        require(source.buffer.size<=65536);source.buffer.readUtf8()
                    };if(continuation.isActive) continuation.resumeWith(Result.success(value))
                    } catch(e:Exception) { if(continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                }
            })
        }
        return parse(body).also { validateQuote(instrument,it);saved=Instant.now() to it }
    }
    companion object {
        fun parse(body:String):Quote {
            val j=JSONObject(body);require(j.getString("symbol")=="XAU" && j.getString("currency")=="USD")
            return Quote(GlobalGold.instrument.id,j.get("price").toString().toBigDecimal(),"USD",DataKind.SPOT,
                Instant.parse(j.getString("updatedAt")),"Gold-API · global spot USD/oz",timestampBasis=TimestampBasis.PROVIDER_SNAPSHOT)
        }
    }
}
