package io.github.baffaabba.natlas.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON settings for the wire protocol (docs/API.md Part 1). */
internal val WireJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

internal const val CHAT_MODEL: String = "NCAIR1/N-ATLaS"

@Serializable
internal data class WireError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

@Serializable
internal data class WireErrorEnvelope(val error: WireError? = null)

// ---- Chat ----

@Serializable
internal data class WireMessage(val role: String, val content: String)

@Serializable
internal data class WireChatRequest(
    val model: String = CHAT_MODEL,
    val messages: List<WireMessage>,
    val stream: Boolean,
    val temperature: Double,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int,
    val stop: List<String>? = null,
    @SerialName("repetition_penalty") val repetitionPenalty: Double,
)

@Serializable
internal data class WireUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
internal data class WireChoice(
    val message: WireMessageOut? = null,
    val delta: WireDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class WireMessageOut(val role: String? = null, val content: String? = null)

@Serializable
internal data class WireDelta(val role: String? = null, val content: String? = null)

@Serializable
internal data class WireChatResponse(
    val choices: List<WireChoice> = emptyList(),
    val usage: WireUsage? = null,
    val error: WireError? = null,
)

// ---- Transcription ----

@Serializable
internal data class WireSegment(
    val id: Int? = null,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val text: String = "",
)

@Serializable
internal data class WireTranscript(
    val text: String,
    val language: String? = null,
    val duration: Double? = null,
    val model: String? = null,
    val segments: List<WireSegment>? = null,
)

// ---- Live WebSocket (server -> client) ----

@Serializable
internal data class WireLiveMessage(
    val type: String,
    val model: String? = null,
    @SerialName("sample_rate") val sampleRate: Int? = null,
    val text: String? = null,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val error: WireError? = null,
)
