// Internal glue between the public Dart API and the Pigeon-generated code.
// Not exported.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'config.dart';
import 'exceptions.dart';
import 'messages.g.dart';
import 'types.dart';

/// Holds the Pigeon host API and the event stream factory. Tests replace
/// [NAtlasBridge.instance] with a fake.
class NAtlasBridge {
  NAtlasBridge({
    NAtlasHostApi? api,
    Stream<SessionEventMsg> Function(String sessionId)? events,
  })  : api = api ?? NAtlasHostApi(),
        events = events ?? _pigeonEvents;

  /// The bridge used by every new `NAtlasClient`.
  static NAtlasBridge instance = NAtlasBridge();

  final NAtlasHostApi api;
  final Stream<SessionEventMsg> Function(String sessionId) events;

  static Stream<SessionEventMsg> _pigeonEvents(String sessionId) =>
      sessionEvents(instanceName: sessionId);
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

/// Channel error codes. They are the Kotlin `NAtlasException` subclass simple
/// names; the Kotlin side of the plugin uses the same strings.
abstract final class ErrorCodes {
  static const unauthorized = 'Unauthorized';
  static const userCapReached = 'UserCapReached';
  static const rateLimited = 'RateLimited';
  static const invalidRequest = 'InvalidRequest';
  static const unsupportedAudio = 'UnsupportedAudio';
  static const audioTooLarge = 'AudioTooLarge';
  static const modelLoading = 'ModelLoading';
  static const serverError = 'ServerError';
  static const network = 'Network';
  static const timeout = 'Timeout';
  static const micPermissionDenied = 'MicPermissionDenied';
  static const micUnavailable = 'MicUnavailable';

  /// A Kotlin `IllegalArgumentException` (bad argument, e.g. a malformed
  /// `baseUrl`). Becomes a Dart [ArgumentError].
  static const illegalArgument = 'IllegalArgument';

  /// A Kotlin `IllegalStateException` (e.g. a closed client, an unknown
  /// session). Becomes a Dart [StateError].
  static const illegalState = 'IllegalState';
}

/// Turns a [PlatformException] from the Kotlin side into the matching Dart
/// error. Anything else is returned unchanged.
///
/// An error code this function does not know is a bug in the plugin. It is
/// passed through as the original [PlatformException] (never swallowed) and
/// flagged by an assert in debug builds.
Object mapError(Object error) {
  if (error is! PlatformException) return error;
  final msg = error.message ?? error.code;
  final d = error.details is Map ? error.details as Map : const {};
  int? intOf(String k) => (d[k] as num?)?.toInt();
  String? strOf(String k) => d[k] as String?;
  switch (error.code) {
    case ErrorCodes.unauthorized:
      return NAtlasUnauthorized(msg);
    case ErrorCodes.userCapReached:
      return NAtlasUserCapReached(msg);
    case ErrorCodes.rateLimited:
      return NAtlasRateLimited(msg, retryAfterSeconds: intOf('retryAfterSeconds'));
    case ErrorCodes.invalidRequest:
      return NAtlasInvalidRequest(msg);
    case ErrorCodes.unsupportedAudio:
      return NAtlasUnsupportedAudio(msg);
    case ErrorCodes.audioTooLarge:
      return NAtlasAudioTooLarge(msg);
    case ErrorCodes.modelLoading:
      return NAtlasModelLoading(msg, retryAfterSeconds: intOf('retryAfterSeconds'));
    case ErrorCodes.serverError:
      return NAtlasServerError(msg,
          httpCode: intOf('httpCode') ?? 0, code: strOf('code'));
    case ErrorCodes.network:
      return NAtlasNetwork(msg, cause: strOf('cause'));
    case ErrorCodes.timeout:
      return NAtlasTimeout(msg);
    case ErrorCodes.micPermissionDenied:
      return NAtlasMicPermissionDenied(msg);
    case ErrorCodes.micUnavailable:
      return NAtlasMicUnavailable(msg);
    case ErrorCodes.illegalArgument:
      return ArgumentError(msg);
    case ErrorCodes.illegalState:
      return StateError(msg);
  }
  assert(() {
    debugPrint('natlas_sdk: unmapped platform error ${error.code}: $msg');
    return true;
  }());
  return error;
}

/// Runs [call] and rethrows any platform error as its Dart equivalent.
Future<T> guard<T>(Future<T> Function() call) async {
  try {
    return await call();
  } catch (e, st) {
    Error.throwWithStackTrace(mapError(e), st);
  }
}

// ---------------------------------------------------------------------------
// Session streams
// ---------------------------------------------------------------------------

/// A single-subscription stream backed by a Kotlin session.
///
/// * Nothing happens until someone listens. On listen, [start] runs (it
///   creates or returns the Kotlin session id), then the event channel for
///   that session is subscribed, which makes Kotlin start collecting its
///   flow.
/// * Cancelling the subscription cancels the event channel subscription;
///   the Kotlin side then cancels the collecting coroutine, which aborts the
///   HTTP call or WebSocket and releases the microphone. If the cancel lands
///   while [start] is still running, the session is cancelled as soon as its
///   id is known.
/// * Errors are mapped with [mapError]. The stream always ends with done.
Stream<T> sessionStream<T>({
  required NAtlasBridge bridge,
  required Future<String> Function() start,
  required T Function(SessionEventMsg) convert,
}) {
  StreamSubscription<SessionEventMsg>? sub;
  var cancelled = false;
  late final StreamController<T> controller;
  controller = StreamController<T>(
    onListen: () async {
      final String id;
      try {
        id = await start();
      } catch (e, st) {
        if (!cancelled) {
          controller.addError(mapError(e), st);
          await controller.close();
        }
        return;
      }
      if (cancelled) {
        await _cancelQuietly(bridge, id);
        return;
      }
      sub = bridge.events(id).listen(
        (m) {
          try {
            controller.add(convert(m));
          } catch (e, st) {
            controller.addError(e, st);
          }
        },
        onError: (Object e, StackTrace st) =>
            controller.addError(mapError(e), st),
        onDone: controller.close,
      );
    },
    onPause: () => sub?.pause(),
    onResume: () => sub?.resume(),
    onCancel: () async {
      cancelled = true;
      final s = sub;
      sub = null;
      await s?.cancel();
    },
  );
  return controller.stream;
}

Future<void> _cancelQuietly(NAtlasBridge bridge, String id) async {
  try {
    await bridge.api.cancelSession(id);
  } catch (_) {
    // The session may already be gone; nothing to release then.
  }
}

// ---------------------------------------------------------------------------
// Conversions
// ---------------------------------------------------------------------------

ConfigMsg configToMsg(NAtlasConfig c, {required String userAgent}) => ConfigMsg(
      baseUrl: c.baseUrl,
      apiKey: c.apiKey,
      connectTimeoutMs: c.connectTimeout.inMilliseconds,
      readTimeoutMs: c.readTimeout.inMilliseconds,
      maxRetries: c.maxRetries,
      userId: c.userId,
      userAgent: userAgent,
    );

LanguageMsg languageToMsg(Language l) => switch (l) {
      Language.hausa => LanguageMsg.hausa,
      Language.igbo => LanguageMsg.igbo,
      Language.yoruba => LanguageMsg.yoruba,
      Language.english => LanguageMsg.english,
    };

Language languageFromMsg(LanguageMsg l) => switch (l) {
      LanguageMsg.hausa => Language.hausa,
      LanguageMsg.igbo => Language.igbo,
      LanguageMsg.yoruba => Language.yoruba,
      LanguageMsg.english => Language.english,
    };

ChatMessageMsg messageToMsg(ChatMessage m) => ChatMessageMsg(
      role: switch (m.role) {
        Role.system => RoleMsg.system,
        Role.user => RoleMsg.user,
        Role.assistant => RoleMsg.assistant,
      },
      content: m.content,
    );

ChatOptionsMsg optionsToMsg(ChatOptions o) => ChatOptionsMsg(
      temperature: o.temperature,
      maxTokens: o.maxTokens,
      repetitionPenalty: o.repetitionPenalty,
      topP: o.topP,
      stop: List.of(o.stop),
    );

Transcript transcriptFromMsg(TranscriptMsg t) => Transcript(
      text: t.text,
      language: languageFromMsg(t.language),
      durationSeconds: t.durationSeconds,
      model: t.model,
      segments: [
        for (final s in t.segments)
          Segment(start: s.start, end: s.end, text: s.text),
      ],
    );

ChatResult chatResultFromMsg(ChatResultMsg r) => ChatResult(
      text: r.text,
      finishReason: r.finishReason,
      usage: r.usage == null
          ? null
          : Usage(
              promptTokens: r.usage!.promptTokens,
              completionTokens: r.usage!.completionTokens,
              totalTokens: r.usage!.totalTokens,
            ),
    );

/// Thrown into a stream when Kotlin sends an event of the wrong kind for
/// the session. That would be a plugin bug.
StateError _unexpected(SessionEventMsg m, String stream) =>
    StateError('natlas_sdk: unexpected ${m.runtimeType} on a $stream stream');

ChatDelta chatDeltaFromMsg(SessionEventMsg m) => switch (m) {
      ChatDeltaMsg(:final text, :final finishReason) =>
        ChatDelta(text: text, finishReason: finishReason),
      _ => throw _unexpected(m, 'chat'),
    };

LiveEvent liveEventFromMsg(SessionEventMsg m) => switch (m) {
      LiveReadyMsg(:final model) => LiveReady(model),
      LivePartialMsg(:final text, :final start, :final end) =>
        LivePartial(text: text, start: start, end: end),
      LiveFinalMsg(:final text, :final start, :final end) =>
        LiveFinal(text: text, start: start, end: end),
      _ => throw _unexpected(m, 'live'),
    };

VoiceEvent voiceEventFromMsg(SessionEventMsg m) => switch (m) {
      VoiceListeningMsg() => const VoiceListening(),
      VoiceHeardMsg(:final text, :final isFinal) =>
        VoiceHeard(text: text, isFinal: isFinal),
      VoiceThinkingMsg() => const VoiceThinking(),
      VoiceReplyMsg(:final delta) => VoiceReply(delta),
      VoiceDoneMsg(:final userText, :final replyText) =>
        VoiceDone(userText: userText, replyText: replyText),
      _ => throw _unexpected(m, 'voice'),
    };
