package io.github.baffaabba.natlas

import io.github.baffaabba.natlas.internal.Errors
import io.github.baffaabba.natlas.internal.HttpCore
import io.github.baffaabba.natlas.internal.SseParser
import io.github.baffaabba.natlas.internal.WireChatRequest
import io.github.baffaabba.natlas.internal.WireChatResponse
import io.github.baffaabba.natlas.internal.WireJson
import io.github.baffaabba.natlas.internal.WireMessage
import io.github.baffaabba.natlas.internal.decodeOrThrow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** Chat with the N-ATLaS language model (`NCAIR1/N-ATLaS`). Get it from [NAtlasClient.chat]. */
public class ChatApi internal constructor(private val core: HttpCore) {

    /**
     * Sends [messages] and waits for the whole reply.
     *
     * Retried after a 429, a 503 or a network failure (see [NAtlasConfig.maxRetries]).
     *
     * @param messages The conversation so far, oldest first. Must not be empty.
     * @param options How N-ATLaS writes the reply. The defaults follow the model card.
     * @throws NAtlasException On any failure.
     */
    public suspend fun complete(
        messages: List<ChatMessage>,
        options: ChatOptions = ChatOptions(),
    ): ChatResult {
        val request = buildRequest(messages, options, stream = false)
        val raw = core.retrying { core.execute(request) }
        val response = decodeOrThrow<WireChatResponse>(raw.body, raw.code)
        response.error?.let { throw Errors.fromWire(it, raw.code, null) }
        val choice = response.choices.firstOrNull() ?: throw Errors.malformed(raw.code)
        return ChatResult(
            text = choice.message?.content.orEmpty(),
            finishReason = choice.finishReason,
            usage = response.usage?.let { Usage(it.promptTokens, it.completionTokens, it.totalTokens) },
        )
    }

    /**
     * Sends [messages] and streams the reply as it is written.
     *
     * The flow is cold: the request starts when you collect it. It completes after the last
     * piece, or throws [NAtlasException] (including when the connection drops mid-reply).
     * Cancelling the collector cancels the request. Opening the stream is retried after a
     * 429, a 503 or a network failure; once text has arrived nothing is retried.
     *
     * @param messages The conversation so far, oldest first. Must not be empty.
     * @param options How N-ATLaS writes the reply. The defaults follow the model card.
     */
    public fun stream(
        messages: List<ChatMessage>,
        options: ChatOptions = ChatOptions(),
    ): Flow<ChatDelta> = flow {
        val request = buildRequest(messages, options, stream = true)
        coroutineScope {
            // Blocking reads don't see coroutine cancellation, so cancel the call itself.
            val current = AtomicReference<Call?>(null)
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    current.get()?.cancel()
                }
            }
            try {
                val response = core.retrying {
                    val call = core.client.newCall(request)
                    current.set(call)
                    ensureActive()
                    core.open(call)
                }
                response.use { r ->
                    val source = r.body.source()
                    val parser = SseParser()
                    var finished = false
                    while (!finished) {
                        val line = try {
                            source.readUtf8Line()
                        } catch (e: IOException) {
                            ensureActive()
                            throw Errors.fromIo(e)
                        }
                        val data = (if (line == null) parser.finish() else parser.feed(line))
                        if (data != null) {
                            if (data.trim() == "[DONE]") {
                                finished = true
                            } else {
                                val chunk = decodeOrThrow<WireChatResponse>(data, r.code)
                                chunk.error?.let { throw Errors.fromWire(it, null, null) }
                                val choice = chunk.choices.firstOrNull()
                                if (choice != null) {
                                    val text = choice.delta?.content.orEmpty()
                                    if (text.isNotEmpty() || choice.finishReason != null) {
                                        emit(ChatDelta(text, choice.finishReason))
                                    }
                                }
                            }
                        }
                        if (line == null && !finished) {
                            throw NAtlasException.Network(
                                IOException("Stream ended without [DONE]"),
                                "The connection dropped before N-ATLaS finished its reply. Try again.",
                            )
                        }
                    }
                }
            } finally {
                current.set(null)
                watcher.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(messages: List<ChatMessage>, options: ChatOptions, stream: Boolean): Request {
        if (messages.isEmpty()) {
            throw NAtlasException.InvalidRequest("messages is empty. Send at least one ChatMessage.")
        }
        val wire = WireChatRequest(
            messages = messages.map { WireMessage(it.role.wire, it.content) },
            stream = stream,
            temperature = options.temperature,
            topP = options.topP,
            maxTokens = options.maxTokens,
            stop = options.stop.takeIf { it.isNotEmpty() },
            repetitionPenalty = options.repetitionPenalty,
        )
        val json = WireJson.encodeToString(WireChatRequest.serializer(), wire)
        return Request.Builder()
            .url(core.url("v1/chat/completions"))
            .apply { if (stream) header("Accept", "text/event-stream") }
            .post(json.toRequestBody(JSON))
            .build()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
