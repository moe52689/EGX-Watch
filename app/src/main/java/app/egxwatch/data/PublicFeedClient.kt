package app.egxwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

data class FeedResponse(val body: String, val fetchedAt: Instant, val warning: String? = null)

/** Validated responses survive process death. Failed refreshes never replace last good data. */
class PublicFeedClient(private val directory: File? = null,
    private val transport: (suspend (String, String?) -> String)? = null) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val memory = java.util.concurrent.ConcurrentHashMap<String, FeedResponse>()
    private val failures = java.util.concurrent.ConcurrentHashMap<String, Pair<Instant, String>>()
    fun networkChanged() { failures.clear() }
    suspend fun beginCheck() {
        // Explicit checks bypass the short reuse window once, while still batching a source across instruments.
        checked.clear()
        bypassCache = true
    }
    @Volatile private var bypassCache = false
    private val checked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun finishCheck() { bypassCache = false; checked.clear() }
    suspend fun get(url: String, post: String? = null, force: Boolean = false,
        validate: (String) -> Unit): FeedResponse = withContext(Dispatchers.IO) {
        locks.computeIfAbsent(url) { Mutex() }.withLock {
            val key = MessageDigest.getInstance("SHA-256").digest((url + (post ?: "")).toByteArray()).joinToString("") { "%02x".format(it) }
            val file = directory?.let { File(it, "$key.json") }
            val saved = memory[key] ?: runCatching {
                val json = JSONObject(file!!.readText())
                FeedResponse(json.getString("body"), Instant.parse(json.getString("fetchedAt"))).also { validate(it.body); memory[key] = it }
            }.getOrNull()
            val now = Instant.now()
            val refresh = force || (bypassCache && checked.add(key))
            val failure = failures[key]?.takeIf { !refresh && now.isBefore(it.first.plusSeconds(60)) }
            if (failure != null) {
                return@withLock saved?.copy(warning = "Saved feed: ${failure.second}. Last download ${saved.fetchedAt}")
                    ?: throw IOException(failure.second)
            }
            if (!refresh && failures[key] == null && saved != null && now.isBefore(saved.fetchedAt.plusSeconds(60))) return@withLock saved
            try {
                val body = try { transport?.invoke(url, post) ?: download(url, post) }
                catch (e: IOException) {
                    if (e !is java.net.UnknownHostException && e !is java.net.ConnectException && e !is java.net.SocketTimeoutException) throw e
                    delay(750)
                    transport?.invoke(url, post) ?: download(url, post)
                }
                validate(body)
                val result = FeedResponse(body, Instant.now())
                memory[key] = result; failures.remove(key)
                if (file != null) runCatching {
                    file.parentFile?.mkdirs()
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(JSONObject().put("body", body).put("fetchedAt", result.fetchedAt.toString()).toString())
                    java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                result
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val reason = e.message?.take(140) ?: "Network unavailable"
                failures[key] = Instant.now() to reason
                saved?.copy(warning = "Saved feed: $reason. Last download ${saved.fetchedAt}") ?: throw IOException(reason, e)
            }
        }
    }
    private suspend fun download(url: String, post: String?): String = suspendCancellableCoroutine { continuation ->
        val address = url.toHttpUrl()
        require(address.host != "scanner.tradingview.com") { "Automated TradingView access is disabled under source terms" }
        require(address.isHttps && address.port == 443 && address.username.isEmpty() && address.password.isEmpty() && address.fragment == null &&
            address.host in setOf("scanner.tradingview.com", "snduk.com", "app.azimut.eg", "www.egx30etf.com")) { "Unapproved public-feed endpoint" }
        val request = Request.Builder().url(address).header("User-Agent", "EGXWatch/1.2 (personal market monitor)")
            .header("Accept-Language", "en").apply { if (post != null) post(post.toRequestBody("application/json".toMediaType())) }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} from ${request.url.host}")
                        val body = it.body ?: throw IOException("Empty response from ${request.url.host}")
                        val source = body.source(); source.request(4 * 1024 * 1024 + 1L)
                        require(source.buffer.size <= 4 * 1024 * 1024) { "Feed response too large" }
                        source.buffer.readUtf8()
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
}
