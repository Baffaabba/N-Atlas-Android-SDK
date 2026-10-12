// Pigeon schema for the natlas_sdk bridge (Dart <-> Kotlin natlas-core).
//
// Regenerate after editing:
//   dart run pigeon --input pigeons/natlas_api.dart
//
// The Kotlin side only translates these messages to natlas-core types and
// back. No HTTP, audio or retry logic lives in the plugin.
import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/src/messages.g.dart',
  dartOptions: DartOptions(),
  kotlinOut:
      'android/src/main/kotlin/io/github/baffaabba/natlas_sdk/Messages.g.kt',
  kotlinOptions: KotlinOptions(package: 'io.github.baffaabba.natlas_sdk'),
  dartPackageName: 'natlas_sdk',
))

/// Mirrors Kotlin `Language`.
enum LanguageMsg { hausa, igbo, yoruba, english }

/// Mirrors Kotlin `Role`.
enum RoleMsg { system, user, assistant }

/// Mirrors Kotlin `NAtlasConfig`. Durations are milliseconds.
class ConfigMsg {
  ConfigMsg({
    required this.baseUrl,
    required this.apiKey,
    required this.connectTimeoutMs,
    required this.readTimeoutMs,
    required this.maxRetries,
    this.userId,
    required this.userAgent,
  });
  String baseUrl;
  String apiKey;
  int connectTimeoutMs;
  int readTimeoutMs;
  int maxRetries;
  String? userId;

  /// `natlas-flutter/<version>`, set by the Dart side.
  String userAgent;
}

class SegmentMsg {
  SegmentMsg({required this.start, required this.end, required this.text});
  double start;
  double end;
  String text;
}

class TranscriptMsg {
  TranscriptMsg({
    required this.text,
    required this.language,
    required this.durationSeconds,
    required this.model,
    required this.segments,
  });
  String text;
  LanguageMsg language;
  double? durationSeconds;
  String model;
  List<SegmentMsg> segments;
}

class ChatMessageMsg {
  ChatMessageMsg({required this.role, required this.content});
  RoleMsg role;
  String content;
}

class ChatOptionsMsg {
  ChatOptionsMsg({
    required this.temperature,
    required this.maxTokens,
    required this.repetitionPenalty,
    this.topP,
    required this.stop,
  });
  double temperature;
  int maxTokens;
  double repetitionPenalty;
  double? topP;
  List<String> stop;
}

class UsageMsg {
  UsageMsg({
    required this.promptTokens,
    required this.completionTokens,
    required this.totalTokens,
  });
  int promptTokens;
  int completionTokens;
  int totalTokens;
}

class ChatResultMsg {
  ChatResultMsg({required this.text, this.finishReason, this.usage});
  String text;
  String? finishReason;
  UsageMsg? usage;
}

/// Every event that travels over the session event channel.
sealed class SessionEventMsg {}

class LiveReadyMsg extends SessionEventMsg {
  LiveReadyMsg({required this.model});
  String model;
}

class LivePartialMsg extends SessionEventMsg {
  LivePartialMsg({required this.text, required this.start, required this.end});
  String text;
  double start;
  double end;
}

class LiveFinalMsg extends SessionEventMsg {
  LiveFinalMsg({required this.text, required this.start, required this.end});
  String text;
  double start;
  double end;
}

class ChatDeltaMsg extends SessionEventMsg {
  ChatDeltaMsg({required this.text, this.finishReason});
  String text;
  String? finishReason;
}

class VoiceListeningMsg extends SessionEventMsg {
  VoiceListeningMsg();
}

class VoiceHeardMsg extends SessionEventMsg {
  VoiceHeardMsg({required this.text, required this.isFinal});
  String text;
  bool isFinal;
}

class VoiceThinkingMsg extends SessionEventMsg {
  VoiceThinkingMsg();
}

class VoiceReplyMsg extends SessionEventMsg {
  VoiceReplyMsg({required this.delta});
  String delta;
}

class VoiceDoneMsg extends SessionEventMsg {
  VoiceDoneMsg({required this.userText, required this.replyText});
  String userText;
  String replyText;
}

/// Calls from Dart into the plugin's Kotlin code.
///
/// Streams work in two steps: a `start*` call creates a session on the
/// Kotlin side, registers an event channel handler under the returned
/// session id and returns that id. Dart then listens to
/// `sessionEvents(instanceName: id)`. The Kotlin flow is collected only
/// while Dart listens; cancelling the Dart subscription cancels the
/// collecting coroutine (and so the HTTP call and the microphone).
@HostApi()
abstract class NAtlasHostApi {
  /// Creates a Kotlin `NAtlasClient` and returns its handle.
  int createClient(ConfigMsg config);

  /// Closes the Kotlin client and cancels its sessions.
  void closeClient(int clientId);

  @async
  TranscriptMsg transcribe(int clientId, String path, LanguageMsg language);

  @async
  ChatResultMsg chatComplete(
      int clientId, List<ChatMessageMsg> messages, ChatOptionsMsg options);

  /// Returns a session id. Events: [ChatDeltaMsg].
  String startChatStream(
      int clientId, List<ChatMessageMsg> messages, ChatOptionsMsg options);

  /// Returns a session id. Events: [LiveReadyMsg], [LivePartialMsg],
  /// [LiveFinalMsg].
  String startLive(int clientId, LanguageMsg language);

  /// Returns a session id. Events: the `Voice*Msg` classes.
  String startVoice(int clientId, LanguageMsg language,
      List<ChatMessageMsg> history, ChatOptionsMsg options, bool autoStop);

  /// Graceful finish for live and voice sessions (Kotlin `stop()`).
  void stopSession(String sessionId);

  /// Aborts a session that Dart never listened to, or no longer wants.
  void cancelSession(String sessionId);

  bool hasMicPermission();
}

/// One channel per session, selected with Pigeon's `instanceName`
/// (= session id).
@EventChannelApi()
abstract class NAtlasEventApi {
  SessionEventMsg sessionEvents();
}
