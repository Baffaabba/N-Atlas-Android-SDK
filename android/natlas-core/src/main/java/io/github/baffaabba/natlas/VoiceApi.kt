package io.github.baffaabba.natlas

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The voice pipeline: the user speaks, N-ATLAS writes down what they said, and N-ATLaS
 * replies in text. Get it from [NAtlasClient.voice].
 *
 * N-ATLAS has no text-to-speech model yet, so replies come back as text.
 */
public class VoiceApi internal constructor(
    private val asr: AsrApi,
    private val chat: ChatApi,
) {
    /**
     * Prepares one spoken turn. Nothing starts until you collect [VoiceTurn.events].
     * The app must hold `RECORD_AUDIO` (see [NAtlasClient.hasMicPermission]).
     *
     * @param language The language the user speaks: `ha`, `ig`, `yo` or `en`.
     * @param history Earlier messages of the conversation, oldest first. The user's words
     *   are added after them as a new user message.
     * @param options How N-ATLaS writes the reply.
     * @param autoStop If `true`, listening stops at the first pause (the first non-empty
     *   final utterance). If `false`, it stops when you call [VoiceTurn.stop].
     */
    public fun converse(
        language: Language,
        history: List<ChatMessage> = emptyList(),
        options: ChatOptions = ChatOptions(),
        autoStop: Boolean = false,
    ): VoiceTurn = VoiceTurn(asr.live(language), chat, history, options, autoStop)
}

/**
 * One spoken turn with N-ATLaS. Get one from [VoiceApi.converse].
 *
 * Collect [events] to start. Call [stop] when the user is done speaking. Cancel the
 * collecting coroutine to abort; the microphone is released in every case.
 */
public class VoiceTurn internal constructor(
    private val live: LiveTranscription,
    private val chat: ChatApi,
    private val history: List<ChatMessage>,
    private val options: ChatOptions,
    private val autoStop: Boolean,
) {
    /**
     * Events of this turn, in order: [VoiceEvent.Listening], [VoiceEvent.Heard] (any number),
     * [VoiceEvent.Thinking], [VoiceEvent.Reply] (any number), then exactly one
     * [VoiceEvent.Done]. Throws [NAtlasException] on failure. Can be collected only once.
     */
    public val events: Flow<VoiceEvent> = flow {
        val finals = mutableListOf<String>()
        live.events.collect { event ->
            when (event) {
                is LiveEvent.Ready -> emit(VoiceEvent.Listening)
                is LiveEvent.Partial -> emit(VoiceEvent.Heard(join(finals + event.text), isFinal = false))
                is LiveEvent.Final -> {
                    if (event.text.isNotBlank()) finals += event.text
                    emit(VoiceEvent.Heard(join(finals), isFinal = true))
                    if (autoStop && event.text.isNotBlank()) live.stop()
                }
            }
        }

        val userText = join(finals)
        if (userText.isEmpty()) {
            // Nothing was said: end the turn without asking N-ATLaS.
            emit(VoiceEvent.Done(userText = "", replyText = ""))
            return@flow
        }

        emit(VoiceEvent.Thinking)
        val reply = StringBuilder()
        chat.stream(history + ChatMessage.user(userText), options).collect { delta ->
            if (delta.text.isNotEmpty()) {
                reply.append(delta.text)
                emit(VoiceEvent.Reply(delta.text))
            }
        }
        // A text-to-speech stage would slot in here, before Done, once N-ATLAS ships one.
        emit(VoiceEvent.Done(userText, reply.toString()))
    }

    /** The user has finished speaking: stop listening and get the reply. Safe to call more than once. */
    public fun stop() {
        live.stop()
    }

    private fun join(parts: List<String>): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}
