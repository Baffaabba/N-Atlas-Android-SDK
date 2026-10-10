---
name: sdk-api-rules
description: The design rules every public surface of the N-ATLAS SDK must pass — one contract shared by Kotlin, Flutter and the server, error model, threading and cancellation, streaming, audio format, permissions, versioning and binary compatibility, and what makes an SDK pleasant in the first ten minutes. Load before designing, changing, reviewing or documenting any public API, wire protocol or sample code.
---

# SDK API rules

A developer judges an SDK in the first ten minutes: add the dependency, paste
the README snippet, hear their own Hausa sentence come back as text. Every
rule here protects those ten minutes or the next ten months.

## 1. One contract, three implementations

- `docs/API.md` is the single source of truth for the wire protocol (server)
  and the public API (Kotlin, Dart). Change it first, in the same PR as code.
- Kotlin and Dart expose **the same concepts with the same names**, adjusted
  only for idiom: `Flow` ↔ `Stream`, `suspend fun` ↔ `Future`, sealed class ↔
  sealed class. If a Kotlin feature has no Dart twin, say so in `API.md`.
- The Flutter plugin never reimplements logic. Pigeon carries calls to
  `natlas-core`. If Dart needs it, it goes in Kotlin first.

## 2. Small public surface

- Public by choice, internal by default (`explicitApi()` in Kotlin; Dart
  `src/` with a curated export file).
- No config option without a caller who needs it. Sensible defaults: the
  README snippet needs only `baseUrl` and a key.
- Prefer one obvious way: `transcribe(file)` and `stream()`, not five
  overloads.

## 3. Errors

- One sealed error type, `NAtlasException`, with subtypes the caller can act
  on: `Unauthorized`, `RateLimited(retryAfter)`, `UserCapReached`, `Network`,
  `Timeout`, `UnsupportedAudio`, `ServerError(code, message)`, `Cancelled`.
- Messages say what happened and what to do next ("The API key was rejected.
  Check `apiKey` in NAtlasConfig.").
- Never swallow an error. Never return an empty transcript in place of a
  failure. No silent fallback to another model.
- Retries: only idempotent calls, exponential backoff with jitter, capped,
  honour `Retry-After`. Configurable, on by default for 429/503.

## 4. Threading, lifecycle, cancellation

- Every network call is `suspend` or a cold `Flow`; nothing blocks the main
  thread. Java callers get a callback adapter only if someone asks.
- Cancelling the coroutine/`StreamSubscription` cancels the HTTP call and
  stops the microphone. Test this.
- The mic is released on cancel, error, and completion. A leaked
  `AudioRecord` is a blocker.
- `NAtlasClient` is cheap to share and safe across threads; one OkHttp
  client per `NAtlasClient`.

## 5. Streaming

- Chat streams tokens as they arrive (SSE or chunked). The first event
  arrives before the full reply is generated, or streaming is not real.
- Live ASR emits `Partial` and `Final` events with timestamps. State the
  real mechanism in docs (true streaming vs chunked 30 s windows + VAD); do
  not call chunked upload "real-time".
- Every stream ends with exactly one terminal event: completion or error.

## 6. Audio

- The SDK records 16 kHz mono PCM16, which is what Whisper wants. Files in
  other formats are converted on device or rejected with `UnsupportedAudio`
  naming the formats that work.
- Long files: chunk on silence, never in the middle of a word if avoidable;
  document the limit.
- No audio written to disk except a temp file deleted in `finally`.

## 7. Permissions and privacy

- The SDK never requests permissions itself in a way the app cannot
  control. It exposes `hasMicPermission()` and fails with a clear error;
  samples show the request flow with a rationale.
- Declare only what is used (`INTERNET`, `RECORD_AUDIO`). No analytics, no
  telemetry, no device IDs sent anywhere.

## 8. Versioning and compatibility

- SemVer. `0.x` may break, but every break is in `CHANGELOG.md` with a
  migration line.
- Kotlin: binary-compatibility validator once past 0.1. Dart: no breaking
  change in a patch.
- Wire protocol carries a version (`/v1/...`). The client sends
  `User-Agent: natlas-android/<ver>` or `natlas-flutter/<ver>`.
- minSdk and the Kotlin/AGP/Flutter versions are stated in the README.

## 9. The first ten minutes

- The README quickstart is copy-paste runnable in under 15 lines per platform.
- Samples are real apps a judge can install: record → see text → ask N-ATLaS
  → see reply. Empty, loading and error states handled.
- KDoc / dartdoc on every public symbol, with the language codes listed.
- Hausa examples use the real letters ɓ ɗ ƙ 'y and they render in the samples.
