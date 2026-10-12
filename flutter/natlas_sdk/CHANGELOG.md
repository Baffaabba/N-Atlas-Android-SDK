# Changelog

All notable changes to `natlas_sdk`. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the package uses
[SemVer](https://semver.org/). While the version is `0.x`, minor versions may
break the API; every break is listed here with a migration line.

## 0.1.0 - 2026-10-11

First release. Android only.

### Added

- `NAtlasClient(NAtlasConfig(baseUrl:, apiKey:))`, a Dart mirror of the
  Kotlin `natlas-core` 0.1.0 client. All work is delegated to `natlas-core`
  through Pigeon.
- `client.asr.transcribe(path, language)`: transcribe an audio file.
- `client.asr.live(language)`: live microphone transcription, with
  `LiveReady`, `LivePartial` and `LiveFinal` events and `stop()`.
- `client.chat.complete(messages)` and `client.chat.stream(messages)`.
- `client.voice.converse(language, history:, autoStop:)`: one spoken turn
  (microphone, then transcription, then a streamed text reply), with
  `VoiceListening`, `VoiceHeard`, `VoiceThinking`, `VoiceReply` and
  `VoiceDone` events and `stop()`.
- Sealed `NAtlasException` hierarchy that mirrors Kotlin one-to-one:
  `NAtlasUnauthorized`, `NAtlasUserCapReached`, `NAtlasRateLimited`, `NAtlasInvalidRequest`,
  `NAtlasUnsupportedAudio`, `NAtlasAudioTooLarge`, `NAtlasModelLoading`, `NAtlasServerError`,
  `NAtlasNetwork`, `NAtlasTimeout`, `NAtlasMicPermissionDenied`, `NAtlasMicUnavailable`.
- `Language` (`hausa`, `igbo`, `yoruba`, `english`), `Language.fromCode`,
  `ChatMessage`, `ChatOptions`, `ChatResult`, `ChatDelta`, `Usage`,
  `Transcript`, `Segment`.
- `NAtlasClient.attribution`, `NAtlasClient.version`,
  `NAtlasClient.hasMicPermission()`.
- `NAtlasClient.close()` (Dart only; releases the Kotlin client held by the
  plugin).
- Example app: language picker, file transcription, live recording with
  partial text, streaming chat, "Talk to N-ATLAS", runtime server settings,
  About screen with attribution and the licence's user cap.
