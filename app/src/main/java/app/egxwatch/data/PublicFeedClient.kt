package app.egxwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
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
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, FeedResponse>()
    private val failures = mutableMapOf<String, Pair<Instant, String>>()
    suspend fun get(url: String, post: String? = null, force: Boolean = false,
        validate: (String) -> Unit): FeedResponse = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = MessageDigest.getInstance("SHA-256").digest((url + (post ?: "")).toByteArray()).joinToString("") { "%02x".format(it) }
            val file = directory?.let { File(it, "$key.json") }
            val saved = memory[key] ?: runCatching {
                val json = JSONObject(file!!.readText())
                FeedResponse(json.getString("body"), Instant.parse(json.getString("fetchedAt"))).also { validate(it.body); memory[key] = it }
            }.getOrNull()
            val now = Instant.now()
            val failure = failures[key]?.takeIf { !force && now.isBefore(it.first.plusSeconds(30)) }
            if (failure != null) {
                return@withLock saved?.copy(warning = "Saved feed: ${failure.second}. Last download ${saved.fetchedAt}")
                    ?: throw IOException(failure.second)
            }
            if (!force && failures[key] == null && saved != null && now.isBefore(saved.fetchedAt.plusSeconds(60))) return@withLock saved
            try {
                val body = transport?.invoke(url, post) ?: download(url, post)
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
    private fun download(url: String, post: String?): String {
        val request = Request.Builder().url(url).header("User-Agent", "EGXWatch/1.1 (personal market monitor)")
            .header("Accept-Language", "en").apply { if (post != null) post(post.toRequestBody("application/json".toMediaType())) }.build()
        return client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code} from ${request.url.host}")
            val body = it.body ?: throw IOException("Empty response from ${request.url.host}")
            val source = body.source(); source.request(4 * 1024 * 1024 + 1L)
            require(source.buffer.size <= 4 * 1024 * 1024) { "Feed response too large" }
            source.buffer.readUtf8()
        }
    }
}
