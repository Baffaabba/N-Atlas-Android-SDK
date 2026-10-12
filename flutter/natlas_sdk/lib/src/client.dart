import 'dart:async';

import 'bridge.dart';
import 'config.dart';
import 'types.dart';

/// Entry point of the SDK. Mirrors Kotlin `NAtlasClient`.
///
/// ```dart
/// final client = NAtlasClient(NAtlasConfig(
///   baseUrl: const String.fromEnvironment('NATLAS_BASE_URL'),
///   apiKey: const String.fromEnvironment('NATLAS_API_KEY'),
/// ));
/// final t = await client.asr.transcribe('/path/clip.wav', Language.hausa);
/// ```
///
/// All work is done by the Kotlin library `natlas-core`; this class only
/// forwards calls. Android only in v1. One client can be shared across the
/// app. Call [close] when you no longer need it (for example, before
/// creating a client with a new `baseUrl`).
///
/// Errors are thrown as subclasses of `NAtlasException`.
final class NAtlasClient {
  /// Creates a client. Cheap: the Kotlin client is created on first use.
  NAtlasClient(this.config) : _bridge = NAtlasBridge.instance {
    asr = Asr._(this);
    chat = Chat._(this);
    voice = Voice._(this);
  }

  /// The licence attribution that must be shown on every public surface of
  /// an app that uses N-ATLAS. Kotlin `NAtlasClient.ATTRIBUTION`.
  static const String attribution =
      'N-ATLAS is an initiative of the Federal Ministry of Communications, '
      'Innovation and Digital Economy, and powered by Awarri Technologies.';

  /// The version of this plugin, for example `0.1.0`. Sent to the server as
  /// `User-Agent: natlas-flutter/<version>`. Kotlin `NAtlasClient.VERSION`
  /// (which is the natlas-core version).
  static const String version = '0.1.0';

  /// Whether the app holds the `RECORD_AUDIO` permission.
  /// Kotlin `NAtlasClient.hasMicPermission(context)`.
  ///
  /// The SDK never asks for the permission itself. Request it in your app,
  /// with a reason, before calling [Asr.live] or [Voice.converse].
  static Future<bool> hasMicPermission() =>
      guard(() => NAtlasBridge.instance.api.hasMicPermission());

  /// The settings this client was created with.
  final NAtlasConfig config;

  /// Speech-to-text.
  late final Asr asr;

  /// Chat with N-ATLaS.
  late final Chat chat;

  /// The voice pipeline: microphone, speech-to-text, then N-ATLaS.
  late final Voice voice;

  final NAtlasBridge _bridge;
  Future<int>? _handle;
  bool _closed = false;

  Future<int> _id() {
    if (_closed) {
      return Future.error(StateError('This NAtlasClient has been closed.'));
    }
    return _handle ??= guard(() => _bridge.api.createClient(
        configToMsg(config, userAgent: 'natlas-flutter/$version')))
      ..ignore();
  }

  /// Releases the Kotlin client and cancels any running streams.
  /// The client cannot be used afterwards.
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    final handle = _handle;
    if (handle == null) return;
    final int id;
    try {
      id = await handle;
    } catch (_) {
      return; // It was never created.
    }
    await guard(() => _bridge.api.closeClient(id));
  }
}

/// Speech-to-text. Kotlin `client.asr`.
final class Asr {
  Asr._(this._client);
  final NAtlasClient _client;

  /// Transcribes the audio file at [path] in [language].
  ///
  /// Formats: wav, mp3, m4a, ogg, flac, webm; up to 25 MB. Audio longer
  /// than 30 s is split on silence by the server.
  Future<Transcript> transcribe(String path, Language language) =>
      guard(() async {
        final id = await _client._id();
        final t = await _client._bridge.api
            .transcribe(id, path, languageToMsg(language));
        return transcriptFromMsg(t);
      });

  /// Starts live transcription from the microphone in [language].
  ///
  /// Needs the `RECORD_AUDIO` permission. Recording starts when you listen
  /// to [LiveTranscription.events]. Call [LiveTranscription.stop] to finish
  /// (the last utterance is flushed, then the stream ends), or cancel the
  /// subscription to abort at once and release the microphone.
  Future<LiveTranscription> live(Language language) => guard(() async {
        final id = await _client._id();
        final session =
            await _client._bridge.api.startLive(id, languageToMsg(language));
        return LiveTranscription._(_client._bridge, session);
      });
}

/// A live transcription session. Kotlin `LiveTranscription`.
final class LiveTranscription {
  LiveTranscription._(this._bridge, this._sessionId)
      : events = sessionStream(
          bridge: _bridge,
          start: () async => _sessionId,
          convert: liveEventFromMsg,
        );

  final NAtlasBridge _bridge;
  final String _sessionId;

  /// The events: [LiveReady], then [LivePartial] and [LiveFinal].
  ///
  /// Single-subscription. Completes normally after [stop]; emits an
  /// `NAtlasException` on failure. Cancelling the subscription aborts the
  /// session and releases the microphone.
  final Stream<LiveEvent> events;

  /// Finishes: no more audio is recorded, the last utterance is flushed and
  /// then [events] completes.
  Future<void> stop() => guard(() => _bridge.api.stopSession(_sessionId));
}

/// Chat with N-ATLaS. Kotlin `client.chat`.
final class Chat {
  Chat._(this._client);
  final NAtlasClient _client;

  /// Sends [messages] and waits for the whole reply.
  Future<ChatResult> complete(
    List<ChatMessage> messages, {
    ChatOptions options = const ChatOptions(),
  }) =>
      guard(() async {
        final id = await _client._id();
        final r = await _client._bridge.api.chatComplete(
          id,
          messages.map(messageToMsg).toList(),
          optionsToMsg(options),
        );
        return chatResultFromMsg(r);
      });

  /// Sends [messages] and streams the reply as it is generated.
  ///
  /// The request is sent when you listen. Cancelling the subscription
  /// cancels the HTTP request.
  Stream<ChatDelta> stream(
    List<ChatMessage> messages, {
    ChatOptions options = const ChatOptions(),
  }) {
    final msgs = messages.map(messageToMsg).toList();
    final opts = optionsToMsg(options);
    return sessionStream(
      bridge: _client._bridge,
      start: () async {
        final id = await _client._id();
        return _client._bridge.api.startChatStream(id, msgs, opts);
      },
      convert: chatDeltaFromMsg,
    );
  }
}

/// The voice pipeline. Kotlin `client.voice`.
final class Voice {
  Voice._(this._client);
  final NAtlasClient _client;

  /// Starts one voice turn: records the user in [language], transcribes
  /// them, then streams N-ATLaS's reply. Text only in v1 (no speech output).
  ///
  /// Pass earlier turns as [history]. With [autoStop] the recording ends by
  /// itself when the user stops talking; otherwise call [VoiceTurn.stop].
  /// Needs the `RECORD_AUDIO` permission. Recording starts when you listen
  /// to [VoiceTurn.events].
  Future<VoiceTurn> converse(
    Language language, {
    List<ChatMessage> history = const [],
    ChatOptions options = const ChatOptions(),
    bool autoStop = false,
  }) =>
      guard(() async {
        final id = await _client._id();
        final session = await _client._bridge.api.startVoice(
          id,
          languageToMsg(language),
          history.map(messageToMsg).toList(),
          optionsToMsg(options),
          autoStop,
        );
        return VoiceTurn._(_client._bridge, session);
      });
}

/// One voice turn. Kotlin `VoiceTurn`.
final class VoiceTurn {
  VoiceTurn._(this._bridge, this._sessionId)
      : events = sessionStream(
          bridge: _bridge,
          start: () async => _sessionId,
          convert: voiceEventFromMsg,
        );

  final NAtlasBridge _bridge;
  final String _sessionId;

  /// The events: [VoiceListening], [VoiceHeard] (partial, then final),
  /// [VoiceThinking], [VoiceReply] pieces, then [VoiceDone].
  ///
  /// Single-subscription. Emits an `NAtlasException` on failure. Cancelling
  /// the subscription aborts the turn and releases the microphone.
  final Stream<VoiceEvent> events;

  /// The user has finished speaking: stop recording and get the reply.
  Future<void> stop() => guard(() => _bridge.api.stopSession(_sessionId));
}

