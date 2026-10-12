package io.github.baffaabba.natlas_sdk

import io.github.baffaabba.natlas.ChatDelta
import io.github.baffaabba.natlas.ChatMessage
import io.github.baffaabba.natlas.ChatOptions
import io.github.baffaabba.natlas.ChatResult
import io.github.baffaabba.natlas.Language
import io.github.baffaabba.natlas.LiveEvent
import io.github.baffaabba.natlas.NAtlasException
import io.github.baffaabba.natlas.Role
import io.github.baffaabba.natlas.Transcript
import io.github.baffaabba.natlas.VoiceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Channel error codes. They are the simple names of the natlas-core `NAtlasException`
 * subclasses; `lib/src/bridge.dart` (`ErrorCodes`) uses the same strings.
 */
internal object ErrorCodes {
    const val ILLEGAL_ARGUMENT = "IllegalArgument"
    const val ILLEGAL_STATE = "IllegalState"
}

/**
 * Converts any failure into the [FlutterError] Dart maps to its `NAtlasException` twin.
 * The `when` over [NAtlasException] is exhaustive, so a new Kotlin subtype breaks this build
 * until it is mapped (and added to Dart).
 */
internal fun Throwable.toFlutterError(): FlutterError = when (this) {
    is FlutterError -> this
    is NAtlasException -> {
        val details: Map<String, Any?>? = when (this) {
            is NAtlasException.Unauthorized,
            is NAtlasException.UserCapReached,
            is NAtlasException.InvalidRequest,
            is NAtlasException.UnsupportedAudio,
            is NAtlasException.AudioTooLarge,
            is NAtlasException.Timeout,
            is NAtlasException.MicPermissionDenied,
            is NAtlasException.MicUnavailable,
            -> null
            is NAtlasException.RateLimited -> mapOf("retryAfterSeconds" to retryAfterSeconds)
            is NAtlasException.ModelLoading -> mapOf("retryAfterSeconds" to retryAfterSeconds)
            is NAtlasException.ServerError -> mapOf("httpCode" to httpCode, "code" to code)
            is NAtlasException.Network -> mapOf("cause" to cause?.toString())
        }
        FlutterError(javaClass.simpleName, message, details)
    }
    is IllegalArgumentException -> FlutterError(ErrorCodes.ILLEGAL_ARGUMENT, message, null)
    // natlas-core wraps every failure in NAtlasException; anything else is a bug and
    // surfaces in Dart as a StateError carrying the original exception text.
    else -> FlutterError(ErrorCodes.ILLEGAL_STATE, "natlas-core failed unexpectedly: $this", null)
}

internal fun <T> Flow<T>.mapEvents(transform: (T) -> SessionEventMsg): Flow<SessionEventMsg> =
    map { transform(it) }

internal fun LanguageMsg.toCore(): Language = when (this) {
    LanguageMsg.HAUSA -> Language.HAUSA
    LanguageMsg.IGBO -> Language.IGBO
    LanguageMsg.YORUBA -> Language.YORUBA
    LanguageMsg.ENGLISH -> Language.ENGLISH
}

internal fun Language.toMsg(): LanguageMsg = when (this) {
    Language.HAUSA -> LanguageMsg.HAUSA
    Language.IGBO -> LanguageMsg.IGBO
    Language.YORUBA -> LanguageMsg.YORUBA
    Language.ENGLISH -> LanguageMsg.ENGLISH
}

internal fun ChatMessageMsg.toCore(): ChatMessage = ChatMessage(
    role = when (role) {
        RoleMsg.SYSTEM -> Role.SYSTEM
        RoleMsg.USER -> Role.USER
        RoleMsg.ASSISTANT -> Role.ASSISTANT
    },
    content = content,
)

internal fun ChatOptionsMsg.toCore(): ChatOptions = ChatOptions(
    temperature = temperature,
    maxTokens = maxTokens.toInt(),
    repetitionPenalty = repetitionPenalty,
    topP = topP,
    stop = stop,
)

internal fun Transcript.toMsg(): TranscriptMsg = TranscriptMsg(
    text = text,
    language = language.toMsg(),
    durationSeconds = durationSeconds,
    model = model,
    segments = segments.map { SegmentMsg(start = it.start, end = it.end, text = it.text) },
)

internal fun ChatResult.toMsg(): ChatResultMsg = ChatResultMsg(
    text = text,
    finishReason = finishReason,
    usage = usage?.let {
        UsageMsg(
            promptTokens = it.promptTokens.toLong(),
            completionTokens = it.completionTokens.toLong(),
            totalTokens = it.totalTokens.toLong(),
        )
    },
)

internal fun ChatDelta.toMsg(): SessionEventMsg =
    ChatDeltaMsg(text = text, finishReason = finishReason)

internal fun LiveEvent.toMsg(): SessionEventMsg = when (this) {
    is LiveEvent.Ready -> LiveReadyMsg(model = model)
    is LiveEvent.Partial -> LivePartialMsg(text = text, start = start, end = end)
    is LiveEvent.Final -> LiveFinalMsg(text = text, start = start, end = end)
}

internal fun VoiceEvent.toMsg(): SessionEventMsg = when (this) {
    VoiceEvent.Listening -> VoiceListeningMsg()
    is VoiceEvent.Heard -> VoiceHeardMsg(text = text, isFinal = isFinal)
    VoiceEvent.Thinking -> VoiceThinkingMsg()
    is VoiceEvent.Reply -> VoiceReplyMsg(delta = delta)
    is VoiceEvent.Done -> VoiceDoneMsg(userText = userText, replyText = replyText)
}
