package io.github.baffaabba.natlas_sdk

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.github.baffaabba.natlas.NAtlasClient
import io.github.baffaabba.natlas.NAtlasConfig
import io.github.baffaabba.natlas.NAtlasPlatformApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Flutter side of natlas-core. Every call is forwarded to `io.github.baffaabba.natlas`;
 * this class only converts Pigeon messages and manages handles.
 *
 * Threading: Pigeon calls arrive on the main thread, and everything here (maps, event
 * sinks) is touched only from the main thread. natlas-core moves network and audio work to
 * its own dispatchers.
 */
class NatlasSdkPlugin : FlutterPlugin, NAtlasHostApi {
    private lateinit var context: Context
    private var messenger: BinaryMessenger? = null
    private var scope = newScope()

    private val clients = HashMap<Long, NAtlasClient>()
    private var nextClientId = 1L
    private val sessions = HashMap<String, Session>()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        messenger = binding.binaryMessenger
        if (!scope.isActiveScope()) scope = newScope()
        NAtlasHostApi.setUp(binding.binaryMessenger, this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        NAtlasHostApi.setUp(binding.binaryMessenger, null)
        sessions.values.toList().forEach { it.dispose() }
        sessions.clear()
        clients.clear()
        scope.cancel() // aborts every call and stream, releasing the microphone
        messenger = null
    }

    // ---------------------------------------------------------------- clients

    @OptIn(NAtlasPlatformApi::class)
    override fun createClient(config: ConfigMsg): Long {
        val client = try {
            NAtlasClient(
                NAtlasConfig(
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    connectTimeout = config.connectTimeoutMs.milliseconds,
                    readTimeout = config.readTimeoutMs.milliseconds,
                    maxRetries = config.maxRetries.toInt(),
                    userId = config.userId,
                ),
                config.userAgent,
            )
        } catch (e: Throwable) {
            throw e.toFlutterError()
        }
        val id = nextClientId++
        clients[id] = client
        return id
    }

    override fun closeClient(clientId: Long) {
        clients.remove(clientId)
        sessions.values.filter { it.clientId == clientId }.forEach { it.dispose() }
    }

    private fun client(id: Long): NAtlasClient =
        clients[id] ?: throw FlutterError(
            ErrorCodes.ILLEGAL_STATE,
            "This NAtlasClient has been closed. Create a new one.",
            null,
        )

    // ------------------------------------------------------------- one-shot calls

    override suspend fun transcribe(
        clientId: Long,
        path: String,
        language: LanguageMsg,
    ): TranscriptMsg = runCall {
        client(clientId).asr.transcribe(File(path), language.toCore()).toMsg()
    }

    override suspend fun chatComplete(
        clientId: Long,
        messages: List<ChatMessageMsg>,
        options: ChatOptionsMsg,
    ): ChatResultMsg = runCall {
        client(clientId).chat.complete(messages.map { it.toCore() }, options.toCore()).toMsg()
    }

    override fun hasMicPermission(): Boolean = NAtlasClient.hasMicPermission(context)

    // ------------------------------------------------------------------ streams

    override fun startChatStream(
        clientId: Long,
        messages: List<ChatMessageMsg>,
        options: ChatOptionsMsg,
    ): String {
        val flow = client(clientId).chat
            .stream(messages.map { it.toCore() }, options.toCore())
        return openSession(clientId, flow.mapEvents { it.toMsg() }, stop = null)
    }

    override fun startLive(clientId: Long, language: LanguageMsg): String {
        val live = client(clientId).asr.live(language.toCore())
        return openSession(clientId, live.events.mapEvents { it.toMsg() }, stop = live::stop)
    }

    override fun startVoice(
        clientId: Long,
        language: LanguageMsg,
        history: List<ChatMessageMsg>,
        options: ChatOptionsMsg,
        autoStop: Boolean,
    ): String {
        val turn = client(clientId).voice.converse(
            language.toCore(),
            history = history.map { it.toCore() },
            options = options.toCore(),
            autoStop = autoStop,
        )
        return openSession(clientId, turn.events.mapEvents { it.toMsg() }, stop = turn::stop)
    }

    override fun stopSession(sessionId: String) {
        // Unknown id: the session already finished or was cancelled; nothing to stop.
        val session = sessions[sessionId] ?: return
        val stop = session.stop
            ?: throw FlutterError(ErrorCodes.ILLEGAL_STATE, "This stream has no stop(). Cancel it.", null)
        stop()
    }

    override fun cancelSession(sessionId: String) {
        sessions[sessionId]?.dispose()
    }

    private fun openSession(
        clientId: Long,
        events: Flow<SessionEventMsg>,
        stop: (() -> Unit)?,
    ): String {
        val m = messenger ?: throw FlutterError(ErrorCodes.ILLEGAL_STATE, "Plugin is detached.", null)
        val id = UUID.randomUUID().toString()
        val session = Session(id, clientId, events, stop, m)
        sessions[id] = session
        SessionEventsStreamHandler.register(m, session, id)
        return id
    }

    /**
     * One stream. The Kotlin flow is collected only while Dart listens; Dart cancelling the
     * subscription (onCancel) cancels the collecting coroutine, which closes the HTTP call or
     * WebSocket and releases the microphone inside natlas-core.
     */
    private inner class Session(
        val id: String,
        val clientId: Long,
        private val events: Flow<SessionEventMsg>,
        val stop: (() -> Unit)?,
        private val messenger: BinaryMessenger,
    ) : SessionEventsStreamHandler() {
        private var job: Job? = null
        private var disposed = false

        override fun onListen(p0: Any?, sink: PigeonEventSink<SessionEventMsg>) {
            if (job != null || disposed) {
                sink.error(ErrorCodes.ILLEGAL_STATE, "This stream can only be listened to once.", null)
                sink.endOfStream()
                return
            }
            job = scope.launch {
                try {
                    events.collect { sink.success(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val fe = e.toFlutterError()
                    sink.error(fe.code, fe.message, fe.details)
                }
                // Exactly one terminal event: done follows completion or the error.
                sink.endOfStream()
            }
        }

        override fun onCancel(p0: Any?) {
            dispose()
        }

        /** Cancels the coroutine and unregisters the channel. Safe to call twice. */
        fun dispose() {
            if (disposed) return
            disposed = true
            job?.cancel()
            sessions.remove(id)
            messenger.setMessageHandler(channelName(id), null)
        }
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Runs a one-shot call as a child of the plugin scope (so detaching the engine cancels it)
     * and turns any SDK error into the FlutterError the Dart side maps one-to-one.
     */
    private suspend fun <T> runCall(block: suspend () -> T): T =
        withContext(scope.coroutineContext) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw e.toFlutterError()
            }
        }

    private companion object {
        fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        fun CoroutineScope.isActiveScope(): Boolean = coroutineContext[Job]?.isActive == true

        /** Must match the channel name Pigeon generates for `NAtlasEventApi.sessionEvents`. */
        fun channelName(instance: String) =
            "dev.flutter.pigeon.natlas_sdk.NAtlasEventApi.sessionEvents.$instance"
    }
}
