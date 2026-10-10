package io.github.baffaabba.natlas

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Settings for an [NAtlasClient]. Only [baseUrl] and [apiKey] are needed.
 *
 * Don't put the key in source code. Read it from `local.properties` (through
 * `BuildConfig`) or, better, fetch a per-app key from your own backend.
 *
 * @property baseUrl Address of the N-ATLAS server, for example `https://abc.trycloudflare.com`.
 *   A path prefix is allowed (`https://host/natlas`). A trailing slash is ignored.
 * @property apiKey The API key, sent as `Authorization: Bearer <key>`.
 * @property connectTimeout How long to wait to connect to the server.
 * @property readTimeout How long to wait for data from the server. Transcribing a long
 *   file or a long reply can take a while, so the default is generous.
 * @property maxRetries How many times to retry a call after a 429, a 503 or a network
 *   failure. Only safe calls are retried. `0` turns retries off.
 * @property userId A stable, anonymous id for the end user, sent as `X-NAtlas-User`. The
 *   server uses it only to count active users against the licence's 1,000-user cap. Leave it
 *   `null` and the SDK uses a random id it creates once per install.
 */
public data class NAtlasConfig(
    val baseUrl: String,
    val apiKey: String,
    val connectTimeout: Duration = 15.seconds,
    val readTimeout: Duration = 120.seconds,
    val maxRetries: Int = 2,
    val userId: String? = null,
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl is empty. Set it to your N-ATLAS server address." }
        require(maxRetries >= 0) { "maxRetries must be 0 or more." }
    }

    /** Hides the API key so it never ends up in logs. */
    override fun toString(): String =
        "NAtlasConfig(baseUrl=$baseUrl, apiKey=${if (apiKey.isEmpty()) "<empty>" else "<hidden>"}, " +
            "connectTimeout=$connectTimeout, readTimeout=$readTimeout, maxRetries=$maxRetries, " +
            "userId=$userId)"
}
