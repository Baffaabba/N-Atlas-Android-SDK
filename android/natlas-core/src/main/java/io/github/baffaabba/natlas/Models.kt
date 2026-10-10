package io.github.baffaabba.natlas

/**
 * The text of a transcribed audio file.
 *
 * @property text Everything that was said, as one string.
 * @property language The language the audio was transcribed in.
 * @property durationSeconds Length of the audio in seconds, or `null` if the server didn't say.
 * @property model The NCAIR model that did the work, for example `NCAIR1/Hausa-ASR`.
 * @property segments Timed pieces of the text. Empty if the server sent none.
 */
public data class Transcript(
    val text: String,
    val language: Language,
    val durationSeconds: Double?,
    val model: String,
    val segments: List<Segment>,
)

/**
 * A timed piece of a [Transcript].
 *
 * @property start Start time in seconds from the beginning of the audio.
 * @property end End time in seconds from the beginning of the audio.
 * @property text What was said in this piece.
 */
public data class Segment(val start: Double, val end: Double, val text: String)

/** Who wrote a [ChatMessage]. */
public enum class Role(internal val wire: String) {
    /** Instructions that set how N-ATLaS behaves. */
    SYSTEM("system"),

    /** The person using your app. */
    USER("user"),

    /** N-ATLaS. */
    ASSISTANT("assistant"),
}

/**
 * One message in a conversation with N-ATLaS.
 *
 * @property role Who wrote it.
 * @property content The text.
 */
public data class ChatMessage(val role: Role, val content: String) {
    /** Shortcuts for building messages. */
    public companion object {
        /** A system message: instructions for N-ATLaS. */
        public fun system(content: String): ChatMessage = ChatMessage(Role.SYSTEM, content)

        /** A message from the user, for example `ChatMessage.user("Sannu! Yaya kake?")`. */
        public fun user(content: String): ChatMessage = ChatMessage(Role.USER, content)

        /** An earlier reply from N-ATLaS, to keep the conversation going. */
        public fun assistant(content: String): ChatMessage = ChatMessage(Role.ASSISTANT, content)
    }
}

/**
 * How N-ATLaS writes its reply. The defaults follow the N-ATLaS model card.
 *
 * @property temperature Randomness, 0 to 2. Low values give steadier answers.
 * @property maxTokens The longest reply, in tokens.
 * @property repetitionPenalty Above 1 discourages repeating the same words.
 * @property topP Nucleus sampling, or `null` for the server default.
 * @property stop Strings that end the reply early. Empty for none.
 */
public data class ChatOptions(
    val temperature: Double = 0.1,
    val maxTokens: Int = 512,
    val repetitionPenalty: Double = 1.12,
    val topP: Double? = null,
    val stop: List<String> = emptyList(),
)

/**
 * A complete reply from N-ATLaS.
 *
 * @property text The reply.
 * @property finishReason Why it ended: `stop` (finished) or `length` (hit [ChatOptions.maxTokens]).
 * @property usage Token counts, if the server sent them.
 */
public data class ChatResult(val text: String, val finishReason: String?, val usage: Usage?)

/**
 * Token counts for one chat call.
 *
 * @property promptTokens Tokens in the messages you sent.
 * @property completionTokens Tokens in the reply.
 * @property totalTokens The sum of both.
 */
public data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int)

/**
 * A piece of a streamed reply.
 *
 * @property text New text to append. Can be empty on the last piece.
 * @property finishReason Set only on the last piece: `stop` or `length`.
 */
public data class ChatDelta(val text: String, val finishReason: String? = null)
