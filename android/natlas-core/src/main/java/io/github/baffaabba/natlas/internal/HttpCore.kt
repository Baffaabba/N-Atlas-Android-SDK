package io.github.baffaabba.natlas.internal

import io.github.baffaabba.natlas.NAtlasConfig
import io.github.baffaabba.natlas.NAtlasException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/** A fully read HTTP response. */
internal class RawResponse(val code: Int, val body: String, val headers: Headers)

/**
 * The shared HTTP layer of one NAtlasClient: one OkHttpClient, auth headers, error mapping
 * and retries.
 */
internal class HttpCore(
    val config: NAtlasConfig,
    userAgent: String,
    userIdProvider: () -> String,
    /** Waits between retries. Replaced in tests so they don't sleep. */
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val random: Random = Random.Default,
) {
    val baseUrl: HttpUrl = parseBaseUrl(config.baseUrl)

    private val userId: String by lazy { config.userId ?: userIdProvider() }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .writeTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        // Keeps live sessions alive through tunnels and notices dead connections.
        .pingInterval(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("X-NAtlas-User", userId)
                .header("User-Agent", userAgent)
                .build()
            chain.proceed(request)
        }
        .build()

    /** `<baseUrl>/<path>`, keeping any path prefix of the base URL. */
    fun url(path: String): HttpUrl = baseUrl.newBuilder().addPathSegments(path).build()

    /** Runs [request] and reads the whole body. Throws [NAtlasException] for non-2xx or I/O failure. */
    suspend fun execute(request: Request): RawResponse {
        val raw = executeRaw(request)
        if (raw.code !in 200..299) throw Errors.fromHttp(raw.code, raw.body, Errors.retryAfter(raw.headers))
        return raw
    }

    private suspend fun executeRaw(request: Request): RawResponse =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(Errors.fromIo(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val raw = response.use { RawResponse(it.code, it.body.string(), it.headers) }
                        cont.resume(raw)
                    } catch (e: IOException) {
                        cont.resumeWithException(Errors.fromIo(e))
                    }
                }
            })
        }

    /**
     * Opens [request] and returns the response with its body unread, on the calling thread.
     * The caller must close it. Throws [NAtlasException] for non-2xx or I/O failure.
     */
    fun open(call: Call): Response {
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw Errors.fromIo(e)
        }
        if (response.code !in 200..299) {
            val body = response.use { r ->
                try {
                    r.body.string()
                } catch (expected: IOException) {
                    null
                }
            }
            throw Errors.fromHttp(response.code, body, Errors.retryAfter(response.headers))
        }
        return response
    }

    /** Runs [block], retrying it per docs/API.md: 429, 503 and network failures, up to maxRetries. */
    suspend fun <T> retrying(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: NAtlasException) {
                // A cancelled call fails with an I/O error; report the cancellation, don't retry.
                currentCoroutineContext().ensureActive()
                val wait = retryDelayMs(e, attempt) ?: throw e
                sleeper(wait)
                attempt++
            }
        }
    }

    /** How long to wait before retry number [attempt] + 1, or `null` to give up. */
    internal fun retryDelayMs(e: NAtlasException, attempt: Int): Long? {
        if (attempt >= config.maxRetries) return null
        val retryAfter: Long? = when (e) {
            is NAtlasException.RateLimited -> e.retryAfterSeconds
            is NAtlasException.ModelLoading -> e.retryAfterSeconds
            is NAtlasException.ServerError -> if (e.httpCode == 503) null else return null
            is NAtlasException.Network -> null
            else -> return null
        }
        if (retryAfter != null) {
            // A long wait is the app's call, not ours: report it instead of blocking.
            if (retryAfter > MAX_RETRY_AFTER_SECONDS) return null
            return retryAfter * 1000 + random.nextLong(0, 250)
        }
        val ceiling = minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl attempt)
        return ceiling / 2 + random.nextLong(0, ceiling / 2 + 1)
    }

    internal companion object {
        const val BASE_BACKOFF_MS: Long = 500
        const val MAX_BACKOFF_MS: Long = 8_000
        const val MAX_RETRY_AFTER_SECONDS: Long = 30

        fun parseBaseUrl(raw: String): HttpUrl {
            val trimmed = raw.trim().trimEnd('/')
            return trimmed.toHttpUrlOrNull()
                ?: throw IllegalArgumentException(
                    "baseUrl \"$raw\" is not a valid http(s) address. " +
                        "Use something like https://your-server.example.com",
                )
        }
    }
}

/** Decodes [body] or throws a [NAtlasException.ServerError] saying the reply couldn't be read. */
internal inline fun <reified T> decodeOrThrow(body: String, httpCode: Int): T = try {
    WireJson.decodeFromString<T>(body)
} catch (e: IllegalArgumentException) {
    throw Errors.malformed(httpCode, e)
}
