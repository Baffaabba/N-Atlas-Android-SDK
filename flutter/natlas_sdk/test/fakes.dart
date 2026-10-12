import 'dart:async';

import 'package:flutter/services.dart';
import 'package:natlas_sdk/src/messages.g.dart';

/// A stand-in for the Kotlin side: extends the Pigeon host API and records
/// every call, plus one fake event channel per session.
class FakeHostApi extends NAtlasHostApi {
  final calls = <String>[];
  int clientsCreated = 0;
  ConfigMsg? lastConfig;
  List<ChatMessageMsg>? lastMessages;
  ChatOptionsMsg? lastOptions;
  bool? lastAutoStop;

  /// When set, the next host call throws it.
  Object? throwOnNext;

  /// Completes startChatStream; tests can hold it to simulate a slow start.
  Completer<void>? holdChatStart;

  final sessions = <String, FakeSession>{};
  int _nextSession = 0;

  void _maybeThrow() {
    final e = throwOnNext;
    if (e != null) {
      throwOnNext = null;
      throw e;
    }
  }

  String _newSession(String kind) {
    final id = '$kind-${_nextSession++}';
    sessions[id] = FakeSession(id);
    return id;
  }

  @override
  Future<int> createClient(ConfigMsg config) async {
    calls.add('createClient');
    _maybeThrow();
    lastConfig = config;
    return ++clientsCreated;
  }

  @override
  Future<void> closeClient(int clientId) async {
    calls.add('closeClient:$clientId');
  }

  @override
  Future<TranscriptMsg> transcribe(
      int clientId, String path, LanguageMsg language) async {
    calls.add('transcribe:$clientId:$path:${language.name}');
    _maybeThrow();
    return TranscriptMsg(
      text: 'Ina kwana',
      language: language,
      durationSeconds: 1.5,
      model: 'NCAIR1/Hausa-ASR',
      segments: [SegmentMsg(start: 0, end: 1.5, text: 'Ina kwana')],
    );
  }

  @override
  Future<ChatResultMsg> chatComplete(int clientId,
      List<ChatMessageMsg> messages, ChatOptionsMsg options) async {
    calls.add('chatComplete');
    _maybeThrow();
    lastMessages = messages;
    lastOptions = options;
    return ChatResultMsg(
      text: 'Lafiya lau',
      finishReason: 'stop',
      usage: UsageMsg(promptTokens: 3, completionTokens: 2, totalTokens: 5),
    );
  }

  @override
  Future<String> startChatStream(int clientId, List<ChatMessageMsg> messages,
      ChatOptionsMsg options) async {
    calls.add('startChatStream');
    await holdChatStart?.future;
    _maybeThrow();
    lastMessages = messages;
    return _newSession('chat');
  }

  @override
  Future<String> startLive(int clientId, LanguageMsg language) async {
    calls.add('startLive:${language.name}');
    _maybeThrow();
    return _newSession('live');
  }

  @override
  Future<String> startVoice(int clientId, LanguageMsg language,
      List<ChatMessageMsg> history, ChatOptionsMsg options, bool autoStop) async {
    calls.add('startVoice:${language.name}');
    _maybeThrow();
    lastMessages = history;
    lastAutoStop = autoStop;
    return _newSession('voice');
  }

  @override
  Future<void> stopSession(String sessionId) async {
    calls.add('stopSession:$sessionId');
    _maybeThrow();
    sessions[sessionId]?.controller.close();
  }

  @override
  Future<void> cancelSession(String sessionId) async {
    calls.add('cancelSession:$sessionId');
  }

  @override
  Future<bool> hasMicPermission() async {
    calls.add('hasMicPermission');
    return true;
  }

  /// The event stream factory handed to `NAtlasBridge`.
  Stream<SessionEventMsg> events(String id) {
    final s = sessions[id];
    if (s == null) {
      return Stream.error(
          PlatformException(code: 'IllegalState', message: 'no session $id'));
    }
    return s.controller.stream;
  }
}

/// Mimics a Pigeon event channel: a broadcast stream whose first listen is
/// Kotlin `onListen` and whose cancel is Kotlin `onCancel`.
class FakeSession {
  FakeSession(this.id) {
    controller = StreamController<SessionEventMsg>.broadcast(
      onListen: () => listened++,
      onCancel: () => cancelled++,
    );
  }

  final String id;
  late final StreamController<SessionEventMsg> controller;
  int listened = 0;
  int cancelled = 0;

  void emit(SessionEventMsg m) => controller.add(m);

  void fail(String code, {String message = 'boom', Object? details}) =>
      controller.addError(
          PlatformException(code: code, message: message, details: details));

  Future<void> end() => controller.close();
}
