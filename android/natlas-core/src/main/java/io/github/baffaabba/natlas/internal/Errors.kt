package io.github.baffaabba.natlas.internal

import io.github.baffaabba.natlas.NAtlasException
import okhttp3.Headers
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Maps HTTP statuses, wire error codes and I/O failures to [NAtlasException] (docs/API.md "Errors"). */
internal object Errors {

    /** Maps a non-2xx HTTP response. [body] may be anything, including HTML from a proxy. */
    fun fromHttp(httpCode: Int, body: String?, retryAfterSeconds: Long?): NAtlasException {
        val error = body?.let { parseError(it) }
        return fromWire(error, httpCode, retryAfterSeconds)
    }

    /** Maps an error object. [httpCode] is `null` when it came in a WebSocket `error` frame. */
    fun fromWire(error: WireError?, httpCode: Int?, retryAfterSeconds: Long?): NAtlasException {
        val serverMessage = error?.message?.takeIf { it.isNotBlank() }
        when (error?.code) {
            "invalid_api_key" -> return NAtlasException.Unauthorized()
            "user_cap_reached" -> return NAtlasException.UserCapReached()
            "rate_limited" -> return NAtlasException.RateLimited(retryAfterSeconds)
            "invalid_request" -> return NAtlasException.InvalidRequest(invalidMessage(serverMessage))
            "audio_too_large" -> return NAtlasException.AudioTooLarge()
            "unsupported_audio" -> return NAtlasException.UnsupportedAudio()
            "model_loading" -> return NAtlasException.ModelLoading(retryAfterSeconds)
        }
        return when (httpCode) {
            400 -> NAtlasException.InvalidRequest(invalidMessage(serverMessage))
            401 -> NAtlasException.Unauthorized()
            413 -> NAtlasException.AudioTooLarge()
            415 -> NAtlasException.UnsupportedAudio()
            429 -> NAtlasException.RateLimited(retryAfterSeconds)
            else -> serverError(httpCode ?: httpCodeFor(error?.code), error?.code, serverMessage)
        }
    }

    /** Maps an OkHttp failure. */
    fun fromIo(e: IOException): NAtlasException = when {
        e is SocketTimeoutException -> NAtlasException.Timeout(e)
        e is InterruptedIOException && e.message == "timeout" -> NAtlasException.Timeout(e)
        else -> NAtlasException.Network(e)
    }

    /** The server answered 2xx but the body isn't what the protocol says. */
    fun malformed(httpCode: Int, cause: Throwable? = null): NAtlasException =
        NAtlasException.ServerError(
            httpCode,
            "bad_response",
            "The server sent a reply the SDK couldn't read. Check that baseUrl points to an " +
                "N-ATLAS server." + (cause?.message?.let { " ($it)" } ?: ""),
        )

    fun retryAfter(headers: Headers): Long? =
        headers["Retry-After"]?.trim()?.toLongOrNull()?.coerceAtLeast(0)

    fun parseError(body: String): WireError? = try {
        WireJson.decodeFromString(WireErrorEnvelope.serializer(), body).error
    } catch (expected: IllegalArgumentException) {
        null // Not JSON (for example an HTML page from a tunnel or proxy).
    }

    private fun invalidMessage(serverMessage: String?): String =
        serverMessage ?: "The server rejected the request as invalid."

    private fun httpCodeFor(code: String?): Int = when (code) {
        "backend_unavailable" -> 503
        else -> 500
    }

    private fun serverError(httpCode: Int, code: String?, serverMessage: String?): NAtlasException {
        val detail = serverMessage?.let { " Server said: $it" } ?: ""
        val message = when (httpCode) {
            404 -> "There is no N-ATLAS API at this address (HTTP 404). Check baseUrl in NAtlasConfig."
            403 -> "The server refused the request (HTTP 403).$detail"
            502, 504, 521, 522, 523, 530 ->
                "The server address answered, but N-ATLAS isn't running behind it (HTTP $httpCode). " +
                    "If you use a Colab tunnel, check the session is still running and baseUrl is current."
            503 -> "The N-ATLAS server is busy or not ready (HTTP 503). Try again soon.$detail"
            else -> "The N-ATLAS server failed (HTTP $httpCode). Try again later.$detail"
        }
        return NAtlasException.ServerError(httpCode, code, message)
    }
}
