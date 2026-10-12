/// Flutter plugin for the N-ATLAS models: speech-to-text in Hausa, Igbo,
/// Yoruba and Nigerian-accented English, chat with the N-ATLaS LLM, and a
/// one-call voice pipeline.
///
/// Android only in v1. Every call is delegated to the Kotlin library
/// `natlas-core` (`io.github.baffaabba:natlas-core`); this package carries
/// no networking or audio code of its own.
///
/// N-ATLAS is an initiative of the Federal Ministry of Communications,
/// Innovation and Digital Economy, and powered by Awarri Technologies.
/// Free use is capped at 1,000 active end users in a rolling 30 days.
///
/// ```dart
/// import 'package:natlas_sdk/natlas_sdk.dart';
///
/// final client = NAtlasClient(NAtlasConfig(
///   baseUrl: const String.fromEnvironment('NATLAS_BASE_URL'),
///   apiKey: const String.fromEnvironment('NATLAS_API_KEY'),
/// ));
/// final t = await client.asr.transcribe('/path/clip.wav', Language.hausa);
/// ```
library;

export 'src/client.dart'
    show NAtlasClient, Asr, Chat, Voice, LiveTranscription, VoiceTurn;
export 'src/config.dart' show NAtlasConfig;
export 'src/exceptions.dart';
export 'src/types.dart';
