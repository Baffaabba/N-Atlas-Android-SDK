/// Settings for an `NAtlasClient`. Mirrors Kotlin `NAtlasConfig`.
///
/// Only [baseUrl] and [apiKey] are needed. Never hard-code the key: read it
/// from `--dart-define` in development, and from your own backend in
/// production.
final class NAtlasConfig {
  /// Creates a config.
  const NAtlasConfig({
    required this.baseUrl,
    required this.apiKey,
    this.connectTimeout = const Duration(seconds: 15),
    this.readTimeout = const Duration(seconds: 120),
    this.maxRetries = 2,
    this.userId,
  });

  /// Root URL of the N-ATLAS server, e.g. `https://example.trycloudflare.com`.
  final String baseUrl;

  /// Per-app API key, sent as `Authorization: Bearer <key>`.
  final String apiKey;

  /// How long to wait for a connection. Default 15 s.
  final Duration connectTimeout;

  /// How long to wait for data once connected. Default 120 s.
  final Duration readTimeout;

  /// Retries for 429, 503 and network errors on idempotent calls. Default 2.
  final int maxRetries;

  /// Anonymous, stable end-user id used only to count active users against
  /// the licence cap. If null, the Kotlin library generates a random id per
  /// install.
  final String? userId;
}
