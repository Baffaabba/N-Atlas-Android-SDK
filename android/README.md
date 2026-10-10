# N-ATLAS SDK for Android

Kotlin client for the N-ATLAS models: speech-to-text in Hausa, Igbo, Yorùbá and
Nigerian English, and chat with the N-ATLaS language model. It talks to an
N-ATLAS server over HTTPS. Nothing runs on the phone.

> N-ATLAS is an initiative of the Federal Ministry of Communications,
> Innovation and Digital Economy, and powered by Awarri Technologies.

## Quickstart

```kotlin
// build.gradle.kts
dependencies { implementation("io.github.baffaabba:natlas-core:0.1.0") }
```

```kotlin
val client = NAtlasClient(NAtlasConfig(baseUrl = BuildConfig.NATLAS_BASE_URL, apiKey = BuildConfig.NATLAS_API_KEY))

lifecycleScope.launch {
    val transcript = client.asr.transcribe(File(path), Language.HAUSA)   // a file
    val reply = client.chat.complete(listOf(ChatMessage.user("Sannu! Yaya kake?")))
    client.chat.stream(listOf(ChatMessage.user("Ka ba ni labari"))).collect { print(it.text) }
}
```

Live microphone (the app must hold `RECORD_AUDIO`):

```kotlin
val live = client.asr.live(Language.YORUBA)
val job = lifecycleScope.launch {
    live.events.collect { e ->
        when (e) {
            is LiveEvent.Ready -> showListening()
            is LiveEvent.Partial -> showDraft(e.text)    // replaced by the next event
            is LiveEvent.Final -> appendLine(e.text)     // never changes
        }
    }   // completes after stop(); throws NAtlasException on failure
}
live.stop()        // finish the last words, then the flow completes
// job.cancel()    // or abort at once; the microphone is released either way
```

One spoken turn (microphone, then text, then a reply from N-ATLaS):

```kotlin
val turn = client.voice.converse(Language.HAUSA, history = messages, autoStop = true)
turn.events.collect { e ->
    when (e) {
        VoiceEvent.Listening -> {}
        is VoiceEvent.Heard -> show(e.text)
        VoiceEvent.Thinking -> {}
        is VoiceEvent.Reply -> appendReply(e.delta)
        is VoiceEvent.Done -> save(e.userText, e.replyText)
    }
}
```

N-ATLAS has no text-to-speech model yet, so replies come back as text.

The full API, wire protocol and error table are in [`docs/API.md`](../docs/API.md).

## Before you start

- **A server.** The SDK needs an N-ATLAS server address (`baseUrl`) and an API
  key. There is no official hosted N-ATLAS API. Run the server in [`server/`](../server)
  yourself, for example in Google Colab, and use the tunnel address it prints.
  The address changes every Colab session, so the sample app lets you edit it.
- **Keys stay out of code.** Never put the key in source or resources. The sample
  reads it from `local.properties`, which git ignores. In a real app, fetch a
  per-app key from your own backend.

## Errors

Every failure is a `NAtlasException`. Catch the subtypes you can act on:

| Subtype | What to do |
| --- | --- |
| `Unauthorized` | Check `apiKey`. |
| `UserCapReached` | The app passed the licence's 1,000 active users in 30 days. |
| `RateLimited(retryAfterSeconds)` / `ModelLoading(retryAfterSeconds)` | Wait, then try again. The SDK already retried. |
| `InvalidRequest`, `UnsupportedAudio`, `AudioTooLarge` | Fix the input. Files: wav, mp3, m4a, ogg, flac, webm, up to 25 MB. |
| `Network`, `Timeout` | Check the connection and `baseUrl`. |
| `ServerError(httpCode, code, message)` | Server-side problem; the message says what. |
| `MicPermissionDenied`, `MicUnavailable` | Ask for `RECORD_AUDIO`, or free the microphone. |

The SDK retries 429, 503 and network failures on safe calls (up to
`maxRetries`, default 2), with exponential backoff and jitter. It waits as long
as `Retry-After` says, up to 30 seconds. A longer wait is reported to you
instead. Cancelling a coroutine is never turned into an error.

## How live transcription works

The SDK records 16 kHz mono 16-bit audio and sends it in 100 ms frames over a
WebSocket. The server detects pauses (voice activity detection). About once a
second it re-transcribes the current utterance with the Whisper-based NCAIR
model and sends a `Partial`. After about 0.8 s of silence, or 30 s of speech, it
sends a `Final`. This is near-real-time chunked transcription. Whisper is not a
true streaming model.

## Privacy and permissions

- The library declares `INTERNET` and `RECORD_AUDIO`. It never asks for the
  permission itself. Check `NAtlasClient.hasMicPermission(context)` and ask the
  user, with a reason, when they tap a button that needs the microphone.
- The microphone is on only while a live session or voice turn runs. It is
  released on `stop()`, on cancel and on error.
- Audio is not saved on the device or logged by the SDK.
- The SDK sends a random id, created once per install, as `X-NAtlas-User`. The
  server uses it only to count active users for the licence. It is not tied to
  the device. Set `NAtlasConfig.userId` to use your own anonymous id instead.

## Licence and limits

N-ATLAS models are under the N-ATLAS Open-Source Research and Innovation
License. Free use stops at **1,000 active users in any 30 days**. Past that, you
need a commercial licence from Awarri (datasupport@awarri.com). The licence
bans surveillance, profiling, disinformation and military use. Show the
attribution above (it is `NAtlasClient.ATTRIBUTION`) in your app's About screen.

## Sample app

`sample/` is a one-screen Compose app: pick a language, transcribe a file, live
transcription, chat with a streaming reply, and a "Talk to N-ATLAS" voice turn.

1. Add to `android/local.properties` (git-ignored):

   ```properties
   sdk.dir=/path/to/Android/sdk
   natlas.baseUrl=https://your-tunnel.trycloudflare.com
   natlas.apiKey=your-key
   ```

2. Build and install:

   ```bash
   cd android
   ./gradlew :sample:installDebug
   ```

You can also type the server address and key in the app's Settings card.

## Build from source

Requirements: JDK 17 and the Android SDK (platform 36).

```bash
cd android
./gradlew :natlas-core:testDebugUnitTest      # unit tests (MockWebServer, fake microphone)
./gradlew :natlas-core:publishToMavenLocal    # io.github.baffaabba:natlas-core:0.1.0 in ~/.m2
./gradlew :sample:assembleDebug
```

End-to-end check against a running server (for example `server/` with
`NATLAS_BACKEND=fake`). The test is skipped unless both variables are set:

```bash
NATLAS_IT_BASE_URL=http://127.0.0.1:8000 NATLAS_IT_API_KEY=your-key \
  ./gradlew :natlas-core:testDebugUnitTest --tests '*ServerIntegrationTest*'
```

To use a local build from another project, add `mavenLocal()` to its
repositories after running `publishToMavenLocal`.

| | Version |
| --- | --- |
| minSdk | 24 (Android 7.0) |
| compileSdk / targetSdk | 36 |
| Kotlin | 2.2.21 (published metadata readable from Kotlin 2.1) |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.14.3 |
| Runtime dependencies | OkHttp 5.3.2, kotlinx.coroutines 1.10.2, kotlinx.serialization 1.9.0 |
