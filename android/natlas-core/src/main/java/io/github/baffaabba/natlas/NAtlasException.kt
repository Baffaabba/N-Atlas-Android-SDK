package io.github.baffaabba.natlas

/**
 * Every failure the SDK reports. Each subtype is something your app can act on.
 *
 * Coroutine cancellation is never wrapped: a cancelled call throws
 * [kotlin.coroutines.cancellation.CancellationException] as usual.
 */
public sealed class NAtlasException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** The server rejected the API key (HTTP 401, `invalid_api_key`). */
    public class Unauthorized(
        message: String = "The API key was rejected. Check apiKey in NAtlasConfig.",
    ) : NAtlasException(message)

    /**
     * The licence's free limit of 1,000 active users in 30 days was reached
     * (HTTP 403, `user_cap_reached`). A commercial licence comes from Awarri
     * (datasupport@awarri.com).
     */
    public class UserCapReached(
        message: String = "This app has reached the N-ATLAS licence limit of 1,000 active users " +
            "in 30 days. Contact Awarri (datasupport@awarri.com) for a commercial licence.",
    ) : NAtlasException(message)

    /**
     * Too many requests (HTTP 429, `rate_limited`).
     *
     * @property retryAfterSeconds Seconds the server asked us to wait, or `null` if it didn't say.
     */
    public class RateLimited(
        public val retryAfterSeconds: Long?,
        message: String = "Too many requests. " +
            (retryAfterSeconds?.let { "Try again in $it s." } ?: "Wait a moment and try again."),
    ) : NAtlasException(message)

    /** The request was wrong: a bad field, or an unknown model or language (HTTP 400). */
    public class InvalidRequest(message: String) : NAtlasException(message)

    /** The server couldn't decode the audio (HTTP 415, `unsupported_audio`). */
    public class UnsupportedAudio(
        message: String = "The server couldn't read this audio. Use wav, mp3, m4a, ogg, flac or webm.",
    ) : NAtlasException(message)

    /** The audio file is over the 25 MB limit (HTTP 413, `audio_too_large`). */
    public class AudioTooLarge(
        message: String = "The audio file is over 25 MB. Send a shorter or smaller file.",
    ) : NAtlasException(message)

    /**
     * The models are still loading (HTTP 503, `model_loading`).
     *
     * @property retryAfterSeconds Seconds the server asked us to wait, or `null` if it didn't say.
     */
    public class ModelLoading(
        public val retryAfterSeconds: Long?,
        message: String = "The N-ATLAS models are still loading. " +
            (retryAfterSeconds?.let { "Try again in $it s." } ?: "Try again in a minute."),
    ) : NAtlasException(message)

    /**
     * Any other server failure, including a reply the SDK couldn't read.
     *
     * @property httpCode The HTTP status, for example 500 or 503 (`backend_unavailable`).
     * @property code The error `code` from the server, if it sent one.
     */
    public class ServerError(
        public val httpCode: Int,
        public val code: String?,
        message: String,
    ) : NAtlasException(message)

    /** The server couldn't be reached, or the connection dropped. */
    public class Network(
        cause: Throwable,
        message: String = "We couldn't reach the N-ATLAS server. Check your connection and " +
            "baseUrl in NAtlasConfig, then try again.",
    ) : NAtlasException(message, cause)

    /** The server took longer than the configured timeout to answer. */
    public class Timeout(
        cause: Throwable? = null,
        message: String = "The N-ATLAS server took too long to answer. Try again, or raise " +
            "readTimeout in NAtlasConfig.",
    ) : NAtlasException(message, cause)

    /** The app doesn't have the `RECORD_AUDIO` permission. Ask the user for it, then try again. */
    public class MicPermissionDenied(
        message: String = "The app doesn't have microphone permission. Request RECORD_AUDIO " +
            "before starting live transcription.",
    ) : NAtlasException(message)

    /** The microphone couldn't be opened, or failed while recording. Another app may be using it. */
    public class MicUnavailable(
        message: String = "The microphone couldn't be opened. Another app may be using it.",
        cause: Throwable? = null,
    ) : NAtlasException(message, cause)
}
