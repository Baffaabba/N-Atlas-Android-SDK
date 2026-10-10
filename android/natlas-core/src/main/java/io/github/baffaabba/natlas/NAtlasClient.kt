package io.github.baffaabba.natlas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import io.github.baffaabba.natlas.internal.AppContext
import io.github.baffaabba.natlas.internal.AudioRecordSource
import io.github.baffaabba.natlas.internal.AudioSourceFactory
import io.github.baffaabba.natlas.internal.HttpCore
import io.github.baffaabba.natlas.internal.InstallId
import kotlinx.coroutines.delay

/**
 * Marks API meant for SDKs that wrap natlas-core, such as the N-ATLAS Flutter plugin.
 * Apps don't need it.
 */
@RequiresOptIn(
    message = "For SDKs that wrap natlas-core (such as the Flutter plugin). Apps don't need this.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class NAtlasPlatformApi

/**
 * The entry point of the N-ATLAS SDK.
 *
 * ```kotlin
 * val client = NAtlasClient(NAtlasConfig(baseUrl = "https://your-server", apiKey = BuildConfig.NATLAS_API_KEY))
 * val transcript = client.asr.transcribe(File(path), Language.HAUSA)
 * ```
 *
 * One client holds one connection pool. Create it once and share it; it is safe to use from
 * any thread. Every call is a `suspend` function or a cold `Flow`, so it is safe to call
 * from the main thread.
 *
 * The SDK only talks to the N-ATLAS models (the `NCAIR1` models) on the server you configure. It never
 * falls back to another provider.
 */
public class NAtlasClient internal constructor(
    /** The settings this client was created with. */
    public val config: NAtlasConfig,
    userAgent: String,
    audioSourceFactory: AudioSourceFactory?,
    sleeper: suspend (Long) -> Unit,
) {
    /** Creates a client for the server in [config]. */
    public constructor(config: NAtlasConfig) : this(config, DEFAULT_USER_AGENT, null, { delay(it) })

    /**
     * Creates a client that sends [userAgent] (for example `natlas-flutter/0.1.0`) instead of
     * `natlas-android/<version>`. For wrapper SDKs only.
     */
    @NAtlasPlatformApi
    public constructor(config: NAtlasConfig, userAgent: String) :
        this(config, userAgent, null, { delay(it) })

    private val core = HttpCore(
        config = config,
        userAgent = userAgent,
        userIdProvider = { InstallId.get(AppContext.value) },
        sleeper = sleeper,
    )

    private val audio: AudioSourceFactory =
        audioSourceFactory ?: AudioSourceFactory { AudioRecordSource(AppContext.value) }

    /** Speech-to-text: transcribe a file, or the microphone live. */
    public val asr: AsrApi = AsrApi(core, audio)

    /** Chat with the N-ATLaS language model. */
    public val chat: ChatApi = ChatApi(core)

    /** One spoken turn: microphone, then transcription, then a reply from N-ATLaS. */
    public val voice: VoiceApi = VoiceApi(asr, chat)

    /** Constants and helpers. */
    public companion object {
        /**
         * The attribution the N-ATLAS licence requires on every public surface (README,
         * About screen, docs, video).
         */
        public const val ATTRIBUTION: String =
            "N-ATLAS is an initiative of the Federal Ministry of Communications, Innovation " +
                "and Digital Economy, and powered by Awarri Technologies."

        /** The version of this SDK, for example `0.1.0`. */
        public const val VERSION: String = BuildConfig.SDK_VERSION

        internal const val DEFAULT_USER_AGENT: String = "natlas-android/$VERSION"

        /**
         * Returns `true` if the app may use the microphone (`RECORD_AUDIO` is granted).
         * The SDK never asks for the permission itself; your app asks when it needs it.
         */
        public fun hasMicPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}
