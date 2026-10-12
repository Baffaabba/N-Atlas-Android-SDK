# natlas_sdk

Flutter plugin for the N-ATLAS models. Speech-to-text in Hausa, Igbo, Yoruba
and Nigerian-accented English, chat with the N-ATLaS language model, and a
one-call voice pipeline (microphone, then transcription, then a text reply).

> N-ATLAS is an initiative of the Federal Ministry of Communications,
> Innovation and Digital Economy, and powered by Awarri Technologies.

**Android only in v1.** There is no iOS, web or desktop implementation, and
the plugin does not pretend to have one. Every call goes to the Kotlin
library `natlas-core` (`io.github.baffaabba:natlas-core`) through
[Pigeon](https://pub.dev/packages/pigeon). The plugin has no networking or
audio code of its own, so Kotlin and Flutter apps get the same behaviour.

It is a cloud client: the phone sends audio or text to an N-ATLAS server
that you run (see `server/` in this repository) and gets text back.

## Quickstart

```dart
import 'package:natlas_sdk/natlas_sdk.dart';

final client = NAtlasClient(NAtlasConfig(
  baseUrl: const String.fromEnvironment('NATLAS_BASE_URL'),
  apiKey: const String.fromEnvironment('NATLAS_API_KEY'),
));

final t = await client.asr.transcribe('/path/clip.wav', Language.hausa);
print(t.text);

final reply = StringBuffer();
await for (final d in client.chat.stream([ChatMessage.user('Sannu! Yaya kake?')])) {
  reply.write(d.text); // update your UI here
}
```

Run with `flutter run --dart-define=NATLAS_BASE_URL=https://... --dart-define=NATLAS_API_KEY=...`.
Never put the key in source code or assets. In production, fetch a per-app
key from your own backend.

## Requirements

| | Version |
| --- | --- |
| Android | minSdk 24 |
| Flutter | 3.35 or newer (built and tested with 3.47.7) |
| Dart | 3.9 or newer |
| Kotlin / AGP in your app | Kotlin 2.1+, AGP 8.x (the example uses Kotlin 2.2.21, AGP 8.13.2) |
| JDK | 17 |

## Install

The plugin is not on pub.dev yet, and `natlas-core` is not on Maven Central
yet. Until they are:

1. Add the plugin from this repository:

   ```yaml
   dependencies:
     natlas_sdk:
       path: ../N-Atlas-Android-SDK/flutter/natlas_sdk   # or a git dependency
   ```

2. Make `io.github.baffaabba:natlas-core:0.1.0` available to Gradle. Pick one:

   - **Composite build** (what the example app does). In your app's
     `android/settings.gradle.kts`:

     ```kotlin
     includeBuild("<path-to>/N-Atlas-Android-SDK/android") {
         dependencySubstitution {
             substitute(module("io.github.baffaabba:natlas-core")).using(project(":natlas-core"))
         }
     }
     ```

   - **Maven Local.** Run `./gradlew :natlas-core:publishToMavenLocal` in
     `N-Atlas-Android-SDK/android`. The plugin's Gradle file already looks in
     `mavenLocal()`.

## Permissions

The plugin declares `INTERNET` and `RECORD_AUDIO` (through `natlas-core`). It
never asks for the microphone itself. Ask for it in your app at the moment it
is needed, with a reason, for example with
[permission_handler](https://pub.dev/packages/permission_handler). Without
it, `asr.live` and `voice.converse` fail with `NAtlasMicPermissionDenied`.
`NAtlasClient.hasMicPermission()` tells you whether it is granted.

## The API

The Dart API mirrors the Kotlin API (`docs/API.md`, parts 2 and 3) with the
same names. `Flow` becomes `Stream`, `suspend` becomes `Future`, and enum
values are lower case.

| Kotlin | Dart |
| --- | --- |
| `client.asr.transcribe(File(path), Language.HAUSA)` | `client.asr.transcribe(path, Language.hausa)` |
| `client.asr.live(Language.HAUSA)` | `await client.asr.live(Language.hausa)` |
| `live.events: Flow<LiveEvent>` | `live.events: Stream<LiveEvent>` |
| `LiveEvent.Ready / Partial / Final` | `LiveReady / LivePartial / LiveFinal` |
| `client.chat.complete(messages)` | `client.chat.complete(messages)` |
| `client.chat.stream(messages): Flow<ChatDelta>` | `client.chat.stream(messages): Stream<ChatDelta>` |
| `client.voice.converse(lang, history, options, autoStop)` | `await client.voice.converse(lang, history:, options:, autoStop:)` |
| `VoiceEvent.Listening / Heard / Thinking / Reply / Done` | `VoiceListening / VoiceHeard / VoiceThinking / VoiceReply / VoiceDone` |
| `NAtlasException.Unauthorized` (etc.) | `NAtlasUnauthorized` (etc.), all `extends NAtlasException` |
| `NAtlasClient.ATTRIBUTION`, `VERSION` | `NAtlasClient.attribution`, `version` |
| `NAtlasClient.hasMicPermission(context)` | `NAtlasClient.hasMicPermission()` |
| (none) | `client.close()` |

Languages: `Language.hausa` (`ha`), `Language.igbo` (`ig`),
`Language.yoruba` (`yo`), `Language.english` (`en`, Nigerian-accented).

### Live microphone

```dart
final live = await client.asr.live(Language.hausa);
final sub = live.events.listen((e) => switch (e) {
      LiveReady() => null,
      LivePartial(:final text) => showPartial(text), // may still change
      LiveFinal(:final text) => append(text),         // never changes
    });
await live.stop();   // finish: the last words are flushed, then the stream ends
// or: await sub.cancel();   // abort now; the microphone is released
```

Recording starts when you listen. How it works: the phone sends 16 kHz mono
audio over a WebSocket; the server re-transcribes the current utterance about
once a second (`LivePartial`) and closes it after about 0.8 s of silence
(`LiveFinal`). This is near-real-time chunked transcription. Whisper is not a
true streaming model.

### Voice turn

```dart
final turn = await client.voice.converse(Language.hausa, history: messages, autoStop: true);
await for (final e in turn.events) {
  switch (e) {
    case VoiceHeard(:final text): showUser(text);
    case VoiceReply(:final delta): appendReply(delta);
    case VoiceDone(:final userText, :final replyText): save(userText, replyText);
    default:
  }
}
```

N-ATLAS has no text-to-speech model yet, so replies come back as text.

### Errors

Every failure is a subclass of the sealed `NAtlasException`, so a `switch`
over it is exhaustive:

`NAtlasUnauthorized`, `NAtlasUserCapReached`, `NAtlasRateLimited(retryAfterSeconds)`,
`NAtlasInvalidRequest`, `NAtlasUnsupportedAudio`, `NAtlasAudioTooLarge`,
`NAtlasModelLoading(retryAfterSeconds)`, `NAtlasServerError(httpCode, code)`,
`NAtlasNetwork(cause)`, `NAtlasTimeout`, `NAtlasMicPermissionDenied`, `NAtlasMicUnavailable`.

`e.message` says what happened and what to do next. Stream errors arrive on
the stream, which then ends. Retries (429, 503 and network errors on safe
calls) happen inside `natlas-core`. Each Dart name is `NAtlas` plus the
Kotlin name (`NAtlasException.Timeout` is `NAtlasTimeout`), the same rule
as `LiveEvent.Ready` and `LiveReady`.

### Cancellation

Cancelling a `StreamSubscription` cancels the Kotlin coroutine that collects
the flow. That closes the HTTP request or WebSocket and releases the
microphone. Streams carry exactly one terminal event: done, or an error
followed by done. `Future`s (`transcribe`, `complete`) cannot be cancelled
from Dart.

## Example app

`example/` has the same features as the Kotlin sample: a language picker,
file transcription, live recording with partial text, streaming chat and a
"Talk to N-ATLAS" voice turn. The server address and key can be typed in
Settings; they are saved on the phone, so a prebuilt APK needs no
`--dart-define`. The Colab test server gets a new address every session.

```sh
cd example
flutter run --dart-define=NATLAS_BASE_URL=https://xxxx.trycloudflare.com \
            --dart-define=NATLAS_API_KEY=your-key
```

The app bundles Noto Sans, so ɓ ɗ ƙ (Hausa), ẹ ọ ṣ (Yoruba) and ị ụ ṅ (Igbo)
render on every phone.

## Licence and limits

- **Attribution.** Every app, README and docs page that uses N-ATLAS must
  show: "N-ATLAS is an initiative of the Federal Ministry of Communications,
  Innovation and Digital Economy, and powered by Awarri Technologies."
  `NAtlasClient.attribution` has the exact text.
- **User cap.** The N-ATLAS licence allows free use for up to 1,000 active
  end users in any rolling 30 days. Past that you need a commercial licence
  from Awarri (datasupport@awarri.com). The server counts users with an
  anonymous random id per install; when the cap is reached, calls fail with
  `NAtlasUserCapReached`.
- **Banned uses.** The licence bans surveillance, profiling, disinformation
  and military use.
- **Privacy.** The microphone is used only while a live session or voice
  turn runs. The SDK stores no audio on the device and logs none. It sends
  no analytics.

The plugin's own code is part of this repository. The bundled Noto Sans font
in the example app is under the SIL Open Font License 1.1
(`example/assets/fonts/OFL.txt`).
