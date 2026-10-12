/// The languages N-ATLAS supports. Mirrors Kotlin `Language`.
///
/// | Value | [code] | ASR model |
/// | --- | --- | --- |
/// | [hausa] | `ha` | `NCAIR1/Hausa-ASR` |
/// | [igbo] | `ig` | `NCAIR1/Igbo-ASR` |
/// | [yoruba] | `yo` | `NCAIR1/Yoruba-ASR` |
/// | [english] | `en` | `NCAIR1/NigerianAccentedEnglish` |
enum Language {
  /// Hausa (`ha`).
  hausa('ha', 'NCAIR1/Hausa-ASR'),

  /// Igbo (`ig`).
  igbo('ig', 'NCAIR1/Igbo-ASR'),

  /// Yoruba (`yo`).
  yoruba('yo', 'NCAIR1/Yoruba-ASR'),

  /// Nigerian-accented English (`en`).
  english('en', 'NCAIR1/NigerianAccentedEnglish');

  const Language(this.code, this.asrModel);

  /// The ISO 639-1 code sent to the server: `ha`, `ig`, `yo` or `en`.
  final String code;

  /// The Hugging Face id of the speech-to-text model for this language.
  final String asrModel;

  /// Returns the language with this [code] (`ha`, `ig`, `yo`, `en`), or
  /// null. Kotlin `Language.fromCode`.
  static Language? fromCode(String code) {
    for (final l in values) {
      if (l.code == code) return l;
    }
    return null;
  }
}

/// Who wrote a [ChatMessage]. Mirrors Kotlin `Role`.
enum Role {
  /// Instructions for the model.
  system,

  /// The person using the app.
  user,

  /// N-ATLaS.
  assistant,
}

/// One message in a conversation. Mirrors Kotlin `ChatMessage`.
final class ChatMessage {
  /// Creates a message with an explicit [role].
  const ChatMessage(this.role, this.content);

  /// A system message (instructions for the model).
  const ChatMessage.system(String content) : this(Role.system, content);

  /// A message from the user, e.g. `ChatMessage.user('Sannu! Yaya kake?')`.
  const ChatMessage.user(String content) : this(Role.user, content);

  /// A message from N-ATLaS, used to pass earlier replies back as history.
  const ChatMessage.assistant(String content)
      : this(Role.assistant, content);

  /// Who wrote it.
  final Role role;

  /// The text.
  final String content;

  @override
  bool operator ==(Object other) =>
      other is ChatMessage && other.role == role && other.content == content;

  @override
  int get hashCode => Object.hash(role, content);

  @override
  String toString() => 'ChatMessage(${role.name}, $content)';
}

/// Sampling options for chat. Mirrors Kotlin `ChatOptions`. The defaults
/// follow the N-ATLaS model card.
final class ChatOptions {
  /// Creates options; every field has the model card default.
  const ChatOptions({
    this.temperature = 0.1,
    this.maxTokens = 512,
    this.repetitionPenalty = 1.12,
    this.topP,
    this.stop = const [],
  });

  /// Randomness, 0 to 2. Default 0.1.
  final double temperature;

  /// Longest reply, in tokens. Default 512.
  final int maxTokens;

  /// Penalty for repeating tokens. Default 1.12.
  final double repetitionPenalty;

  /// Nucleus sampling, or null for the server default.
  final double? topP;

  /// Strings that end the reply.
  final List<String> stop;
}

/// Token counts reported by the server. Mirrors Kotlin `Usage`.
final class Usage {
  /// Creates a usage record.
  const Usage({
    required this.promptTokens,
    required this.completionTokens,
    required this.totalTokens,
  });

  /// Tokens in the request.
  final int promptTokens;

  /// Tokens in the reply.
  final int completionTokens;

  /// Sum of both.
  final int totalTokens;
}

/// A complete chat reply. Mirrors Kotlin `ChatResult`.
final class ChatResult {
  /// Creates a result.
  const ChatResult({required this.text, this.finishReason, this.usage});

  /// The reply text.
  final String text;

  /// Why generation stopped: `stop` or `length`.
  final String? finishReason;

  /// Token counts, if the server sent them.
  final Usage? usage;
}

/// One piece of a streamed chat reply. Mirrors Kotlin `ChatDelta`.
final class ChatDelta {
  /// Creates a delta.
  const ChatDelta({required this.text, this.finishReason});

  /// New text since the previous delta (may be empty).
  final String text;

  /// Set on the last delta: `stop` or `length`.
  final String? finishReason;
}

/// A time-stamped piece of a [Transcript]. Mirrors Kotlin `Segment`.
final class Segment {
  /// Creates a segment.
  const Segment({required this.start, required this.end, required this.text});

  /// Start, in seconds from the beginning of the audio.
  final double start;

  /// End, in seconds from the beginning of the audio.
  final double end;

  /// The words spoken in this segment.
  final String text;
}

/// The result of transcribing a file. Mirrors Kotlin `Transcript`.
final class Transcript {
  /// Creates a transcript.
  const Transcript({
    required this.text,
    required this.language,
    required this.durationSeconds,
    required this.model,
    this.segments = const [],
  });

  /// The full text.
  final String text;

  /// The language it was transcribed as.
  final Language language;

  /// Length of the audio in seconds, if the server reported it.
  final double? durationSeconds;

  /// The model that produced it, e.g. `NCAIR1/Hausa-ASR`.
  final String model;

  /// Time-stamped segments (may be empty).
  final List<Segment> segments;
}

/// An event from live microphone transcription. Mirrors Kotlin `LiveEvent`.
///
/// The server re-transcribes the open utterance about once a second
/// ([LivePartial]) and closes it after about 0.8 s of silence or 30 s
/// ([LiveFinal]). This is near-real-time chunked transcription, not a true
/// streaming model.
sealed class LiveEvent {
  const LiveEvent();
}

/// The server has loaded the model; audio is flowing.
/// Kotlin `LiveEvent.Ready`.
final class LiveReady extends LiveEvent {
  /// Creates the event.
  const LiveReady(this.model);

  /// The ASR model in use.
  final String model;
}

/// The current utterance so far; replaced by the next [LivePartial] or
/// [LiveFinal]. Kotlin `LiveEvent.Partial`.
final class LivePartial extends LiveEvent {
  /// Creates the event.
  const LivePartial({required this.text, required this.start, required this.end});

  /// Text so far.
  final String text;

  /// Start, in seconds since recording began.
  final double start;

  /// End, in seconds since recording began.
  final double end;
}

/// A finished utterance; it never changes. Kotlin `LiveEvent.Final`.
final class LiveFinal extends LiveEvent {
  /// Creates the event.
  const LiveFinal({required this.text, required this.start, required this.end});

  /// The utterance.
  final String text;

  /// Start, in seconds since recording began.
  final double start;

  /// End, in seconds since recording began.
  final double end;
}

/// An event from a voice turn (mic, then ASR, then N-ATLaS).
/// Mirrors Kotlin `VoiceEvent`.
sealed class VoiceEvent {
  const VoiceEvent();
}

/// The microphone is open. Kotlin `VoiceEvent.Listening`.
final class VoiceListening extends VoiceEvent {
  /// Creates the event.
  const VoiceListening();
}

/// What the user said so far ([isFinal] false) or in full ([isFinal] true).
/// Kotlin `VoiceEvent.Heard`.
final class VoiceHeard extends VoiceEvent {
  /// Creates the event.
  const VoiceHeard({required this.text, required this.isFinal});

  /// The transcribed text.
  final String text;

  /// True once the user's speech is complete.
  final bool isFinal;
}

/// N-ATLaS is generating the reply. Kotlin `VoiceEvent.Thinking`.
final class VoiceThinking extends VoiceEvent {
  /// Creates the event.
  const VoiceThinking();
}

/// The next piece of the reply. Kotlin `VoiceEvent.Reply`.
final class VoiceReply extends VoiceEvent {
  /// Creates the event.
  const VoiceReply(this.delta);

  /// New reply text since the previous [VoiceReply].
  final String delta;
}

/// The turn is complete. Kotlin `VoiceEvent.Done`.
final class VoiceDone extends VoiceEvent {
  /// Creates the event.
  const VoiceDone({required this.userText, required this.replyText});

  /// Everything the user said.
  final String userText;

  /// The full reply.
  final String replyText;
}
