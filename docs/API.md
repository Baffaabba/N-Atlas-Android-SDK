# N-ATLAS SDK API (v1)

This file is the single contract for the server (`server/`), the Kotlin
library (`android/natlas-core`) and the Flutter plugin (`flutter/natlas_sdk`).
Change it first, in the same change as the code.

Status: **v0.1 draft, 2026-10-10.**

---

## Part 1: Wire protocol

The protocol is OpenAI-compatible wherever OpenAI has an equivalent, so tools
such as the `openai` Python client and curl examples work unchanged. It adds
only what OpenAI lacks: an ASR `language` routed to the right NCAIR model, and
live microphone transcription over WebSocket.

### Auth and headers

| Header | Required | Meaning |
| --- | --- | --- |
| `Authorization: Bearer <key>` | yes (except `/health`) | Per-app API key |
| `X-NAtlas-User: <opaque id>` | no | Stable, anonymous end-user id. Used only to count active users against the licence cap (1,000 a rolling 30 days). The SDK generates a random UUID per install. |
| `User-Agent: natlas-android/<ver>` or `natlas-flutter/<ver>` | sent by the SDK | Diagnostics |

Every response carries `X-NAtlas-Attribution: N-ATLAS is an initiative of
the Federal Ministry of Communications, Innovation and Digital Economy, and
powered by Awarri Technologies.` HTTP responses also carry `X-Request-Id`
(quote it in bug reports). The WebSocket upgrade response carries the
attribution header too.

When a request has `X-NAtlas-User` and that id is new in the last 30 days
while the server is already at the cap, the request fails with 403
`user_cap_reached`. Users already counted keep working. Requests without the
header are not counted. The server stores only a SHA-256 of the id.

### Models

| Model id | Serves | Hugging Face repo |
| --- | --- | --- |
| `NCAIR1/N-ATLaS` | chat | `NCAIR1/N-ATLaS` (served at whatever quantisation the server reports in `/health`) |
| `NCAIR1/Hausa-ASR` | transcription, `language=ha` | same |
| `NCAIR1/Igbo-ASR` | transcription, `language=ig` | same |
| `NCAIR1/Yoruba-ASR` | transcription, `language=yo` | same |
| `NCAIR1/NigerianAccentedEnglish` | transcription, `language=en` | same |

No other model is ever served. An unknown model or language is a 400. A
supported language whose model this server does not enable is also a 400
(`invalid_request`, the message names the enabled models).

### `GET /health` (no auth)

```json
{
  "status": "ok",
  "version": "0.1.0",
  "models": {
    "NCAIR1/N-ATLaS": {"loaded": true, "state": "loaded", "backend": "transformers", "quantization": "4bit-nf4"},
    "NCAIR1/Hausa-ASR": {"loaded": true, "state": "loaded", "backend": "transformers"}
  },
  "users": {"active_30d": 12, "cap": 1000},
  "attribution": "N-ATLAS is an initiative of ..."
}
```

`status` is `ok`, `loading` (some models still loading) or `degraded` (some
models failed; see `models[*].error`). Only the models this server enables
are listed. `state` is `unloaded` (loads on first use), `loading`, `loaded`
or `failed`. A request that needs a model still loading waits briefly, then
gets 503 `model_loading` with `Retry-After`; a request for a failed model
gets 503 `backend_unavailable`. Requests for other, loaded models work
normally. `backend` is `fake` on a development server that returns canned
text instead of running N-ATLAS. `users` shows the licence counter so the cap
is visible before it is hit.

### `GET /v1/models`

OpenAI list shape: `{"object": "list", "data": [{"id": "NCAIR1/N-ATLaS", "object": "model", "owned_by": "NCAIR1"}, ...]}`.

### `POST /v1/chat/completions`

OpenAI request shape. Supported fields: `model` (default `NCAIR1/N-ATLaS`),
`messages` (`system` | `user` | `assistant`, string `content`), `stream`,
`temperature` (default **0.1**), `top_p`, `max_tokens` (default 512),
`stop`, and the extension `repetition_penalty` (default **1.12**). These
defaults follow the model card. Unknown fields are ignored.

Details: `content` may also be an OpenAI array of `{"type": "text"}` parts
(other part types are a 400). `max_completion_tokens` is accepted as an alias
of `max_tokens`; the server caps `max_tokens` (2048 by default) and rejects
prompt + `max_tokens` above its context limit (8,192 tokens by default, the
model card's advice) with a 400. `n` must be 1. `stop` is a string or up to
4 strings; output is cut before the first match and `finish_reason` is
`stop`. `stream_options: {"include_usage": true}` adds a final chunk with
empty `choices` and `usage`, before `[DONE]`, as in OpenAI.

Non-streaming response: the OpenAI `chat.completion` object with `usage`.

Streaming (`"stream": true`): `text/event-stream`. Each event is
`data: <chat.completion.chunk JSON>\n\n`. The first chunk has
`delta.role = "assistant"`. The last chunk has `finish_reason` set
(`stop` | `length`). The stream ends with `data: [DONE]\n\n`. An error after
the stream has started is sent as `data: {"error": {...}}\n\n` followed by
`data: [DONE]`. Errors found before the first token (bad request, model
loading, prompt too long) are normal HTTP errors, not SSE.

Some proxies buffer SSE. Cloudflare quick tunnels (`*.trycloudflare.com`) do
not support SSE according to Cloudflare's docs, so behind one the whole
stream may arrive at once. The format is unchanged, so clients still work.

### `POST /v1/audio/transcriptions`

`multipart/form-data`:

| Field | Required | Notes |
| --- | --- | --- |
| `file` | yes | wav, mp3, m4a, ogg, flac, webm. Max 25 MB. |
| `language` | one of these two | `ha`, `ig`, `yo`, `en` |
| `model` | one of these two | An ASR model id from the table. If both are given they must agree. |
| `response_format` | no | `json` (default), `verbose_json`, `text` (plain-text body) |
| `temperature` | no | 0 to 1, default 0 |

`json` response: `{"text": "...", "language": "ha", "duration": 12.4, "model": "NCAIR1/Hausa-ASR"}`.
`verbose_json` adds `"task": "transcribe"` and `"segments": [{"id": 0, "start": 0.0, "end": 4.2, "text": "..."}]`.

Audio longer than 30 s is split on silence on the server and transcribed in
windows of at most 30 s, cut at the quietest point in the last 5 s of each
window; there is one segment per window and segments carry absolute times.
Any sample rate and channel count is accepted and converted to 16 kHz mono.
Silence may transcribe to `""`; that is a success, not an error.

### `WS /v1/audio/live?language=ha` (live microphone)

Auth through the `Authorization` header on the upgrade request (and
`X-NAtlas-User`, counted as for HTTP). `model=<ASR id>` may be given instead
of, or with, `language`, with the same rules as transcription.

The server always accepts the upgrade. Problems found at connect time (bad
key, unknown language, user cap, model loading) arrive as a single `error`
frame followed by a close, never as an HTTP status on the upgrade.

Close codes: `1000` after `done`; after an `error` frame, `4000 + HTTP
status` of the error (`4400`, `4401`, `4403`, `4429`, `4503`, `4500`), so a
client that misses the frame can still tell what happened.

Client → server:
- **Binary frames:** raw PCM, 16-bit little-endian, 16 kHz, mono. Any frame
  size; 100–200 ms (3,200–6,400 bytes) is recommended.
- **Text frame** `{"type": "stop"}`: no more audio. The server finalises the
  current utterance, sends its `final`, then `done`, and closes. Any other
  text frame is an `invalid_request` error.
- Frames need not hold whole samples; an odd byte is carried to the next
  frame.

Server → client (text frames, JSON):

| `type` | Fields | Meaning |
| --- | --- | --- |
| `ready` | `model`, `sample_rate` | Model is loaded; start sending audio. |
| `partial` | `text`, `start`, `end` | Current utterance so far. Replaced by the next `partial` or `final`. |
| `final` | `text`, `start`, `end` | Utterance finished (silence detected, or `stop`). Never changes. |
| `error` | `error: {message, type, code}` | Fatal; the server closes after sending it. |
| `done` | | All audio processed; the server closes normally. |

How it works, stated honestly: the server buffers audio and uses voice
activity detection (VAD) to find utterance boundaries. About once a second it
re-transcribes the open utterance with Whisper and sends a `partial`. When
VAD sees about 0.8 s of silence, or the utterance reaches 30 s, it sends a
`final`. This is near-real-time chunked transcription. Whisper is not a true
streaming model.

Details of the reference server: VAD is energy-based (30 ms frames, above
-45 dBFS and 8 dB above a running noise floor). An utterance starts 0.3 s
before the first speech frame. `start`/`end` are seconds of audio since the
connection opened (audio time, not wall-clock). A blip with under 0.25 s of
speech and no `partial` yet is dropped as noise. Partials are skipped while
audio is queued, so a slow server sends fewer partials rather than falling
behind. A `final` may have empty `text`. Silence-only sessions get just
`done`.

### Errors

The OpenAI error shape: `{"error": {"message": "...", "type": "...", "code": "...", "param": null}}`.
`param` names the offending field when there is one. `type` follows OpenAI
(`invalid_request_error`, `authentication_error`, `permission_error`,
`rate_limit_error`, `server_error`); clients should switch on `code`.
Unknown paths are 404 `not_found`.

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `invalid_request` | Bad field, unknown model or language |
| 401 | `invalid_api_key` | Missing or wrong key |
| 403 | `user_cap_reached` | The licence's 1,000 active-user cap was reached |
| 413 | `audio_too_large` | File over 25 MB |
| 415 | `unsupported_audio` | Format that can't be decoded |
| 429 | `rate_limited` | Includes a `Retry-After` header (seconds). Per API key; the reference server allows 120 requests a minute by default |
| 503 | `model_loading` / `backend_unavailable` | Includes `Retry-After` when known |
| 500 | `internal_error` | Bug; the message says nothing sensitive |

On WebSocket, the same codes arrive in an `error` frame.

---

## Part 2: Kotlin API (`io.github.baffaabba.natlas`)

Coordinates: `io.github.baffaabba:natlas-core:<version>`. minSdk 24.

```kotlin
val client = NAtlasClient(
    NAtlasConfig(
        baseUrl = "https://<your-server>",
        apiKey = BuildConfig.NATLAS_API_KEY,
    )
)

// 1. Transcribe a file
val transcript: Transcript = client.asr.transcribe(File(path), Language.HAUSA)

// 2. Live microphone (needs RECORD_AUDIO)
val live: LiveTranscription = client.asr.live(Language.HAUSA)
scope.launch {
    live.events.collect { e ->
        when (e) {
            is LiveEvent.Ready -> {}
            is LiveEvent.Partial -> show(e.text)
            is LiveEvent.Final -> append(e.text)
        }
    }   // completes normally after stop(); throws NAtlasException on failure
}
live.stop()          // finish: flush the last utterance, then the flow completes
// cancelling the collecting coroutine aborts immediately and releases the mic

// 3. Chat
val reply: ChatResult = client.chat.complete(listOf(ChatMessage.user("Sannu! Yaya kake?")))
client.chat.stream(messages).collect { delta -> print(delta.text) }

// 4. Voice pipeline: mic → ASR → N-ATLaS → text
val turn: VoiceTurn = client.voice.converse(Language.HAUSA, history = messages)
turn.events.collect { e ->
    when (e) {
        is VoiceEvent.Listening -> {}
        is VoiceEvent.Heard -> showPartial(e.text, e.isFinal)
        is VoiceEvent.Thinking -> {}
        is VoiceEvent.Reply -> appendReply(e.delta)
        is VoiceEvent.Done -> save(e.userText, e.replyText)
    }
}
turn.stop()          // user finished speaking (or set autoStop = true to stop on silence)
```

### Entry points (exact signatures, as built in `android/natlas-core` 0.1.0)

```kotlin
class NAtlasClient(config: NAtlasConfig) {
    val config: NAtlasConfig
    val asr: AsrApi
    val chat: ChatApi
    val voice: VoiceApi
    companion object {
        const val ATTRIBUTION: String
        const val VERSION: String            // "0.1.0"
        fun hasMicPermission(context: Context): Boolean
    }
}

class AsrApi {
    suspend fun transcribe(file: File, language: Language): Transcript
    fun live(language: Language): LiveTranscription          // not suspend; starts on collect
}
class LiveTranscription {
    val events: Flow<LiveEvent>     // cold; collect once; IllegalStateException on a 2nd collect
    fun stop()                      // idempotent, any thread; before collect -> events completes empty
}

class ChatApi {
    suspend fun complete(messages: List<ChatMessage>, options: ChatOptions = ChatOptions()): ChatResult
    fun stream(messages: List<ChatMessage>, options: ChatOptions = ChatOptions()): Flow<ChatDelta>
}

class VoiceApi {
    fun converse(
        language: Language,
        history: List<ChatMessage> = emptyList(),
        options: ChatOptions = ChatOptions(),
        autoStop: Boolean = false,   // true: stop listening at the first non-empty Final
    ): VoiceTurn
}
class VoiceTurn {
    val events: Flow<VoiceEvent>    // cold; collect once
    fun stop()
}
```

For wrapper SDKs only (the Flutter plugin), behind the opt-in annotation
`@NAtlasPlatformApi`: `NAtlasClient(config: NAtlasConfig, userAgent: String)`,
so the plugin can send `User-Agent: natlas-flutter/<ver>`.

### Types

- `Language` enum: `HAUSA("ha")`, `IGBO("ig")`, `YORUBA("yo")`,
  `ENGLISH("en")` (Nigerian-accented English). `Language.code`,
  `Language.asrModel`, `Language.fromCode(code): Language?`.
- `NAtlasConfig(baseUrl: String, apiKey: String, connectTimeout: Duration = 15.seconds,
  readTimeout: Duration = 120.seconds, maxRetries: Int = 2, userId: String? = null)`
  (`kotlin.time.Duration`). `userId = null` means a random UUID created once
  per install and kept in SharedPreferences (the library captures the app
  context with a tiny internal `ContentProvider`, so no `Context` is needed).
  `toString()` hides the key. An invalid `baseUrl` throws
  `IllegalArgumentException` when the client is created.
- `Transcript(text, language: Language, durationSeconds: Double?, model, segments: List<Segment>)`;
  `Segment(start: Double, end: Double, text)`. `durationSeconds` is null if
  the server omits it. The SDK always asks for `response_format=verbose_json`.
- `Role` enum: `SYSTEM`, `USER`, `ASSISTANT`.
- `ChatMessage(role: Role, content)` with `ChatMessage.system/user/assistant(...)`.
- `ChatOptions(temperature = 0.1, maxTokens = 512, repetitionPenalty = 1.12, topP: Double? = null, stop: List<String> = emptyList())`.
- `ChatResult(text, finishReason: String?, usage: Usage?)`;
  `Usage(promptTokens, completionTokens, totalTokens)`;
  `ChatDelta(text, finishReason: String? = null)`. The stream skips the
  role-only first chunk; the last delta may have empty `text` and a
  `finishReason`.
- `LiveEvent`: `Ready(model)`, `Partial(text, start, end)`, `Final(text, start, end)`.
- `VoiceEvent`: `Listening` (object), `Heard(text, isFinal)`, `Thinking`
  (object), `Reply(delta)`, `Done(userText, replyText)`. `Heard.text` is
  everything heard so far in the turn (finals joined with spaces, plus the
  current partial); replace, don't append. If nothing was heard the turn
  ends with `Done("", "")` and N-ATLaS is not called.
- `NAtlasClient.ATTRIBUTION`: the licence attribution string.
- `NAtlasClient.hasMicPermission(context)`.

### Live session behaviour

- Collecting `events` opens the microphone first (so permission problems
  fail before any network call), then the WebSocket. Audio recorded before
  `ready` is buffered and sent after it, so the first words are not lost.
- `stop()` stops the microphone at once, sends the buffered audio and
  `{"type":"stop"}`, then waits for `final` + `done`. If the server doesn't
  finish within `readTimeout`, the flow throws `Timeout`.
- An `error` frame throws the mapped `NAtlasException`. A close code
  `4000 + status` without a frame maps like that HTTP status. A rejected
  upgrade (HTTP status, from other servers or proxies) maps like HTTP. Any
  other close without `done` is `Network`. Opening the WebSocket is not
  retried in v1.
- `Final` events are passed through even when `text` is empty (the server
  may send them); `VoiceTurn` ignores empty finals.

### Errors: `sealed class NAtlasException : Exception`

`Unauthorized`, `UserCapReached`, `RateLimited(retryAfterSeconds)`,
`InvalidRequest(message)`, `UnsupportedAudio`, `AudioTooLarge`,
`ModelLoading(retryAfterSeconds)`, `ServerError(httpCode, code, message)`,
`Network(cause)`, `Timeout`, `MicPermissionDenied`, `MicUnavailable`.
`CancellationException` is never wrapped.

Mapping details: the wire `code` wins over the HTTP status when both are
known. `403` other than `user_cap_reached` is `ServerError(403, …)`. `503
backend_unavailable` is `ServerError(503, "backend_unavailable", …)`; only
`model_loading` is `ModelLoading`. A 2xx body the SDK can't parse (or a
chat response with no choices) is `ServerError(httpCode, "bad_response", …)`.
A chat stream that ends without `[DONE]` is `Network`. WebSocket `error`
frames map by `code` the same way.

Retries: 429 and 503, plus network errors on idempotent calls
(`transcribe`, non-streaming `complete`, and opening a stream before any
event). Exponential backoff with jitter (base 0.5 s, doubling, capped at
8 s), honouring `Retry-After` (plus up to 250 ms jitter), up to
`maxRetries`. A `Retry-After` over 30 s is not waited out: the error is
thrown so the app can decide. `Timeout` is not retried.

### Threading

All calls are `suspend` or cold `Flow`. Network on OkHttp threads, mic capture
on `Dispatchers.IO`. Safe to call from the main thread.

---

## Part 3: Dart API (`package:natlas_sdk`)

Same names and concepts. `Flow` becomes `Stream` and `suspend` becomes `Future`.

```dart
final client = NAtlasClient(NAtlasConfig(
  baseUrl: const String.fromEnvironment('NATLAS_BASE_URL'),
  apiKey: const String.fromEnvironment('NATLAS_API_KEY'),
));

final t = await client.asr.transcribe('/path/clip.wav', Language.hausa);

final live = await client.asr.live(Language.hausa);
live.events.listen((e) => switch (e) {
  LiveReady() => null,
  LivePartial(:final text) => showPartial(text),
  LiveFinal(:final text) => append(text),
});
await live.stop();          // cancel the subscription to abort

await for (final d in client.chat.stream([ChatMessage.user('Sannu!')])) { print(d.text); }

final turn = await client.voice.converse(Language.hausa, history: messages);
```

Errors are a sealed `NAtlasException` hierarchy that mirrors Kotlin one-to-one.

Naming rule for nested Kotlin types: the parent's prefix plus the Kotlin
name. `LiveEvent.Ready` → `LiveReady`, `VoiceEvent.Heard` → `VoiceHeard`,
`NAtlasException.Timeout` → `NAtlasTimeout` (plain `Timeout` clashes with
`flutter_test`). Enum values and constants are lowerCamel
(`Language.hausa`, `NAtlasClient.attribution`, `NAtlasClient.version`).

Differences from Kotlin, stated plainly:
- `transcribe` takes a file path (`String`), not a `File`.
- `asr.live()` and `voice.converse()` return a `Future` (the plugin creates
  the Kotlin session first); recording still starts only when you listen.
- `NAtlasClient.hasMicPermission()` needs no `Context`.
- Dart only: `client.close()` releases the Kotlin client held by the plugin.
- `Future`s (`transcribe`, `complete`) cannot be cancelled from Dart.
- Channel errors that are not `NAtlasException` (Kotlin
  `IllegalArgumentException`, e.g. a blank `baseUrl`) arrive as Dart
  `ArgumentError`; anything else as `StateError`.

The Flutter plugin is Android-only in v1. All work is delegated to
`natlas-core` through Pigeon. Streams are carried over an `EventChannel` keyed
by a session id. Cancelling a Dart subscription cancels the Kotlin coroutine.
