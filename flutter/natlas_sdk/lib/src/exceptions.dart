/// Errors thrown by the N-ATLAS SDK.
///
/// Mirrors Kotlin `io.github.baffaabba.natlas.NAtlasException` one-to-one:
/// Kotlin `NAtlasException.Unauthorized` is Dart [NAtlasUnauthorized], and so on.
/// The rule is the one used for events (Kotlin `LiveEvent.Ready` is Dart
/// `LiveReady`): the parent's prefix plus the Kotlin name. The prefix avoids
/// clashes with `Timeout` in `flutter_test` and `TimeoutException` in
/// `dart:async`.
///
/// The hierarchy is sealed, so a `switch` over it is exhaustive:
///
/// ```dart
/// try {
///   await client.asr.transcribe(path, Language.hausa);
/// } on NAtlasException catch (e) {
///   switch (e) {
///     case NAtlasUnauthorized(): showError('Check the API key.');
///     case NAtlasRateLimited(:final retryAfterSeconds): retryLater(retryAfterSeconds);
///     default: showError(e.message);
///   }
/// }
/// ```
sealed class NAtlasException implements Exception {
  const NAtlasException(this.message);

  /// What happened and what to do next, from the Kotlin library.
  final String message;

  @override
  String toString() => '$runtimeType: $message';
}

/// The server rejected the API key (HTTP 401). Check `apiKey` in
/// `NAtlasConfig`.
final class NAtlasUnauthorized extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasUnauthorized(super.message);
}

/// The licence's free tier of 1,000 active end users in a rolling 30 days
/// has been reached (HTTP 403 `user_cap_reached`). Past that, a commercial
/// licence from Awarri is needed.
final class NAtlasUserCapReached extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasUserCapReached(super.message);
}

/// Too many requests (HTTP 429). Wait [retryAfterSeconds] before trying
/// again, if the server said how long.
final class NAtlasRateLimited extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasRateLimited(super.message, {this.retryAfterSeconds});

  /// Seconds the server asked us to wait, or null if it did not say.
  final int? retryAfterSeconds;
}

/// The server refused the request as malformed (HTTP 400): for example an
/// unknown language or model.
final class NAtlasInvalidRequest extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasInvalidRequest(super.message);
}

/// The audio could not be decoded (HTTP 415). Use wav, mp3, m4a, ogg, flac
/// or webm.
final class NAtlasUnsupportedAudio extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasUnsupportedAudio(super.message);
}

/// The audio file is over the 25 MB limit (HTTP 413).
final class NAtlasAudioTooLarge extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasAudioTooLarge(super.message);
}

/// The server is still loading the models (HTTP 503). Try again after
/// [retryAfterSeconds], if known.
final class NAtlasModelLoading extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasModelLoading(super.message, {this.retryAfterSeconds});

  /// Seconds the server asked us to wait, or null if it did not say.
  final int? retryAfterSeconds;
}

/// Any other error the server reported.
final class NAtlasServerError extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasServerError(super.message, {required this.httpCode, this.code});

  /// HTTP status code (or the WebSocket error code mapped to one).
  final int httpCode;

  /// The `error.code` string from the server, if any.
  final String? code;
}

/// The server could not be reached: no connection, DNS failure, TLS error,
/// or the connection dropped. Check the network and `baseUrl`.
final class NAtlasNetwork extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasNetwork(super.message, {this.cause});

  /// Description of the underlying Kotlin/Java exception, if any.
  final String? cause;
}

/// The server did not answer within the configured timeouts.
final class NAtlasTimeout extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasTimeout(super.message);
}

/// The app does not hold the `RECORD_AUDIO` permission. Request it (with a
/// reason) before starting live transcription or a voice turn.
final class NAtlasMicPermissionDenied extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasMicPermissionDenied(super.message);
}

/// The microphone could not be opened (busy, or no microphone).
final class NAtlasMicUnavailable extends NAtlasException {
  /// Creates the error with the message from the Kotlin library.
  const NAtlasMicUnavailable(super.message);
}
