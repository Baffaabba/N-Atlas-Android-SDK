package io.github.baffaabba.natlas

import io.github.baffaabba.natlas.internal.AudioSource
import io.github.baffaabba.natlas.internal.AudioSourceFactory
import io.github.baffaabba.natlas.internal.Errors
import io.github.baffaabba.natlas.internal.FRAME_BYTES
import io.github.baffaabba.natlas.internal.HttpCore
import io.github.baffaabba.natlas.internal.WireJson
import io.github.baffaabba.natlas.internal.WireLiveMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A live transcription of the microphone. Get one from [AsrApi.live].
 *
 * Collect [events] to start. Call [stop] when the user is done speaking: the server
 * finishes the last utterance, sends its [LiveEvent.Final], and the flow completes. To abort
 * at once, cancel the coroutine that collects [events]. The microphone is released in every
 * case: after [stop], on cancel and on error.
 *
 * One object is one session: [events] can be collected only once.
 */
public class LiveTranscription internal constructor(
    private val core: HttpCore,
    private val language: Language,
    private val audioFactory: AudioSourceFactory,
) {
    private val collected = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val stopSignal = CompletableDeferred<Unit>()

    /**
     * Events of this session: [LiveEvent.Ready], then any number of [LiveEvent.Partial] and
     * [LiveEvent.Final].
     *
     * Collecting opens the microphone and connects to the server. The flow completes normally
     * after [stop], and throws [NAtlasException] on failure (for example
     * [NAtlasException.MicPermissionDenied] or [NAtlasException.Unauthorized]).
     *
     * @throws IllegalStateException If collected a second time.
     */
    public val events: Flow<LiveEvent> = flow { runSession() }

    /**
     * Ends the session gracefully: the microphone stops now, the server transcribes what is
     * left, and [events] completes after the last [LiveEvent.Final]. Safe to call more than
     * once, and from any thread. Calling it before collecting makes [events] complete empty.
     */
    public fun stop() {
        stopRequested.set(true)
        stopSignal.complete(Unit)
    }

    private suspend fun FlowCollector<LiveEvent>.runSession() {
        check(collected.compareAndSet(false, true)) {
            "LiveTranscription.events can be collected only once. Call asr.live() for a new session."
        }
        if (stopRequested.get()) return
        val source = audioFactory.create()
        try {
            // Open the mic before connecting, so a permission problem fails fast.
            withContext(Dispatchers.IO) { source.start() }
            connectAndStream(source)
        } finally {
            source.release()
        }
    }

    private sealed class Incoming {
        class Text(val text: String) : Incoming()
        class Closed(val code: Int, val reason: String) : Incoming()
        class Failure(val error: Throwable, val httpCode: Int?, val body: String?, val retryAfter: Long?) :
            Incoming()
        object StopTimedOut : Incoming()
    }

    private suspend fun FlowCollector<LiveEvent>.connectAndStream(source: AudioSource): Unit = coroutineScope {
        val incoming = Channel<Incoming>(Channel.UNLIMITED)
        val url = core.url("v1/audio/live").newBuilder()
            .addQueryParameter("language", language.code)
            .build()
        val socket = core.client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    incoming.trySend(Incoming.Text(text))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    incoming.trySend(Incoming.Closed(code, reason))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val upgradeFailed = response != null && response.code != 101
                    val body = if (upgradeFailed) {
                        try {
                            response?.body?.string()
                        } catch (expected: IOException) {
                            null
                        }
                    } else {
                        null
                    }
                    incoming.trySend(
                        Incoming.Failure(
                            error = t,
                            httpCode = if (upgradeFailed) response?.code else null,
                            body = body,
                            retryAfter = response?.headers?.let { Errors.retryAfter(it) },
                        ),
                    )
                }
            },
        )

        val ready = CompletableDeferred<Unit>()
        val frames = Channel<ByteString>(Channel.UNLIMITED)
        var finishedNormally = false

        try {
            // Microphone -> frames. Runs until stop(), end of audio, or cancellation.
            launch(Dispatchers.IO) {
                val buffer = ByteArray(FRAME_BYTES)
                try {
                    while (isActive && !stopRequested.get()) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n > 0) frames.send(buffer.toByteString(0, n))
                    }
                } finally {
                    source.release()
                    frames.close()
                }
            }

            // Frames -> server, once the server is ready. Audio recorded before `ready` is
            // buffered, so the first words are not lost. Then tell the server we're done.
            launch {
                ready.await()
                for (frame in frames) socket.send(frame)
                socket.send(STOP_MESSAGE)
            }

            // stop() must not wait forever for a server that never answers.
            launch {
                stopSignal.await()
                delay(core.config.readTimeout)
                incoming.trySend(Incoming.StopTimedOut)
            }

            while (true) {
                when (val message = incoming.receive()) {
                    is Incoming.Text -> {
                        val event = try {
                            WireJson.decodeFromString(WireLiveMessage.serializer(), message.text)
                        } catch (e: IllegalArgumentException) {
                            throw Errors.malformed(101, e)
                        }
                        when (event.type) {
                            "ready" -> {
                                ready.complete(Unit)
                                emit(LiveEvent.Ready(event.model ?: language.asrModel))
                            }
                            "partial" -> emit(LiveEvent.Partial(event.text.orEmpty(), event.start, event.end))
                            "final" -> emit(LiveEvent.Final(event.text.orEmpty().trim(), event.start, event.end))
                            "error" -> throw Errors.fromWire(event.error, null, null)
                            "done" -> {
                                finishedNormally = true
                                break
                            }
                            else -> Unit // Unknown types are ignored, for forward compatibility.
                        }
                    }
                    is Incoming.Closed -> {
                        // After an error frame the server closes with 4000 + HTTP status. We
                        // normally threw on the frame already; this covers a missed frame.
                        if (message.code in 4000..4999) {
                            throw Errors.fromHttp(message.code - 4000, null, null)
                        }
                        if (stopRequested.get() && message.code == NORMAL_CLOSURE) {
                            finishedNormally = true
                            break
                        }
                        throw NAtlasException.Network(
                            IOException("WebSocket closed: ${message.code} ${message.reason}"),
                            "The server ended the live session early. Try again.",
                        )
                    }
                    is Incoming.Failure -> {
                        val httpCode = message.httpCode
                        throw when {
                            httpCode != null -> Errors.fromHttp(httpCode, message.body, message.retryAfter)
                            message.error is IOException -> Errors.fromIo(message.error)
                            else -> NAtlasException.Network(message.error)
                        }
                    }
                    Incoming.StopTimedOut -> throw NAtlasException.Timeout(
                        message = "The server didn't finish the live session in time after stop(). " +
                            "Try again, or raise readTimeout in NAtlasConfig.",
                    )
                }
            }
        } finally {
            if (finishedNormally) socket.close(NORMAL_CLOSURE, null) else socket.cancel()
            frames.cancel()
            incoming.cancel()
            // Stops the mic reader and helpers; coroutineScope waits for them to finish,
            // so the microphone is released before the flow completes.
            coroutineContext.cancelChildren()
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val STOP_MESSAGE = "{\"type\":\"stop\"}"
    }
}
