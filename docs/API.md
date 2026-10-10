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
powered by Awarri Technologies.`

### Models

| Model id | Serves | Hugging Face repo |
| --- | --- | --- |
| `NCAIR1/N-ATLaS` | chat | `NCAIR1/N-ATLaS` (served at whatever quantisation the server reports in `/health`) |
| `NCAIR1/Hausa-ASR` | transcription, `language=ha` | same |
| `NCAIR1/Igbo-ASR` | transcription, `language=ig` | same |
| `NCAIR1/Yoruba-ASR` | transcription, `language=yo` | same |
| `NCAIR1/NigerianAccentedEnglish` | transcription, `language=en` | same |

No other model is ever served. An unknown model or language is a 400.

### `GET /health` (no auth)

```json
{
  "status": "ok",
  "version": "0.1.0",
  "models": {
    "NCAIR1/N-ATLaS": {"loaded": true, "backend": "transformers", "quantization": "4bit-nf4"},
    "NCAIR1/Hausa-ASR": {"loaded": true, "backend": "transformers"}
  },
  "attribution": "N-ATLAS is an initiative of ..."
}
```

`status` is `ok`, `loading` (models still loading; other endpoints return 503)
or `degraded` (some models failed; see `models[*].error`).

### `GET /v1/models`

OpenAI list shape: `{"object": "list", "data": [{"id": "NCAIR1/N-ATLaS", "object": "model", "owned_by": "NCAIR1"}, ...]}`.

### `POST /v1/chat/completions`

OpenAI request shape. Supported fields: `model` (default `NCAIR1/N-ATLaS`),
`messages` (`system` | `user` | `assistant`, string `content`), `stream`,
`temperature` (default **0.1**), `top_p`, `max_tokens` (default 512),
`stop`, and the extension `repetition_penalty` (default **1.12**). These
defaults follow the model card. Unknown fields are ignored.

Non-streaming response: the OpenAI `chat.completion` object with `usage`.

Streaming (`"stream": true`): `text/event-stream`. Each event is
`data: <chat.completion.chunk JSON>\n\n`. The first chunk has
`delta.role = "assistant"`. The last chunk has `finish_reason` set
(`stop` | `length`). The stream ends with `data: [DONE]\n\n`. An error after
the stream has started is sent as `data: {"error": {...}}\n\n` followed by
`data: [DONE]`.

### `POST /v1/audio/transcriptions`

`multipart/form-data`:

| Field | Required | Notes |
| --- | --- | --- |
| `file` | yes | wav, mp3, m4a, ogg, flac, webm. Max 25 MB. |
| `language` | one of these two | `ha`, `ig`, `yo`, `en` |
| `model` | one of these two | An ASR model id from the table. If both are given they must agree. |
| `response_format` | no | `json` (default), `verbose_json`, `text` |
| `temperature` | no | default 0 |

`json` response: `{"text": "...", "language": "ha", "duration": 12.4, "model": "NCAIR1/Hausa-ASR"}`.
`verbose_json` adds `"segments": [{"id": 0, "start": 0.0, "end": 4.2, "text": "..."}]`.

Audio longer than 30 s is split on silence on the server and transcribed in
windows; segments carry absolute times.

### `WS /v1/audio/live?language=ha` (live microphone)

Auth through the `Authorization` header on the upgrade request.

Client → server:
- **Binary frames:** raw PCM, 16-bit little-endian, 16 kHz, mono. Any frame
  size; 100–200 ms (3,200–6,400 bytes) is recommended.
- **Text frame** `{"type": "stop"}`: no more audio. The server finalises the
  current utterance, sends its `final`, then `done`, and closes.

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

### Errors

The OpenAI error shape: `{"error": {"message": "...", "type": "...", "code": "..."}}`.

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `invalid_request` | Bad field, unknown model or language |
| 401 | `invalid_api_key` | Missing or wrong key |
| 403 | `user_cap_reached` | The licence's 1,000 active-user cap was reached |
| 413 | `audio_too_large` | File over 25 MB |
| 415 | `unsupported_audio` | Format that can't be decoded |
| 429 | `rate_limited` | Includes a `Retry-After` header (seconds) |
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

### Types

- `Language` enum: `HAUSA("ha")`, `IGBO("ig")`, `YORUBA("yo")`,
  `ENGLISH("en")` (Nigerian-accented English). `Language.code`,
  `Language.asrModel`.
- `NAtlasConfig(baseUrl, apiKey, connectTimeout = 15s, readTimeout = 120s,
  maxRetries = 2, userId: String? = null /* auto UUID if null */)`.
- `Transcript(text, language, durationSeconds, model, segments: List<Segment>)`;
  `Segment(start, end, text)`.
- `ChatMessage(role: Role, content)` with `ChatMessage.system/user/assistant(...)`.
- `ChatOptions(temperature = 0.1, maxTokens = 512, repetitionPenalty = 1.12, topP = null, stop = emptyList())`.
- `ChatResult(text, finishReason, usage: Usage?)`; `ChatDelta(text, finishReason?)`.
- `LiveEvent`: `Ready(model)`, `Partial(text, start, end)`, `Final(text, start, end)`.
- `VoiceEvent`: `Listening`, `Heard(text, isFinal)`, `Thinking`,
  `Reply(delta)`, `Done(userText, replyText)`.
- `NAtlasClient.ATTRIBUTION`: the licence attribution string.
- `NAtlasClient.hasMicPermission(context)`.

### Errors: `sealed class NAtlasException : Exception`

`Unauthorized`, `UserCapReached`, `RateLimited(retryAfterSeconds)`,
`InvalidRequest(message)`, `UnsupportedAudio`, `AudioTooLarge`,
`ModelLoading(retryAfterSeconds)`, `ServerError(httpCode, code, message)`,
`Network(cause)`, `Timeout`, `MicPermissionDenied`, `MicUnavailable`.
`CancellationException` is never wrapped.

Retries: 429 and 503, plus network errors on idempotent calls
(`transcribe`, non-streaming `complete`, and opening a stream before any
event). Exponential backoff with jitter, honouring `Retry-After`, up to
`maxRetries`.

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

The Flutter plugin is Android-only in v1. All work is delegated to
`natlas-core` through Pigeon. Streams are carried over an `EventChannel` keyed
by a session id. Cancelling a Dart subscription cancels the Kotlin coroutine.
