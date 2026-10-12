import 'package:flutter/foundation.dart';
import 'package:natlas_sdk/natlas_sdk.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Defaults come from `--dart-define`; nothing is committed to the repo.
///
///   flutter run --dart-define=NATLAS_BASE_URL=https://xxxx.trycloudflare.com \
///               --dart-define=NATLAS_API_KEY=your-key
const String defaultBaseUrl = String.fromEnvironment('NATLAS_BASE_URL');
const String defaultApiKey = String.fromEnvironment('NATLAS_API_KEY');

/// App-wide settings and the shared [NAtlasClient].
///
/// The Colab test server gets a new tunnel URL every session, so the base
/// URL (and key) can be changed at runtime. Changing them closes the old
/// client and makes a new one.
class AppState extends ChangeNotifier {
  AppState({String? baseUrl, String? apiKey})
      : _baseUrl = baseUrl ?? defaultBaseUrl,
        _apiKey = apiKey ?? defaultApiKey {
    _rebuild();
  }

  static const _urlPref = 'natlas_base_url';
  static const _keyPref = 'natlas_api_key';

  /// Loads the address and key saved on this phone in Settings, falling back
  /// to the `--dart-define` values.
  static Future<AppState> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return AppState(
        baseUrl: prefs.getString(_urlPref),
        apiKey: prefs.getString(_keyPref),
      );
    } catch (_) {
      return AppState();
    }
  }

  String _baseUrl;
  String _apiKey;
  Language _language = Language.hausa;
  NAtlasClient? _client;

  /// Server URL in use.
  String get baseUrl => _baseUrl;

  /// API key in use (never shown in full in the UI).
  String get apiKey => _apiKey;

  /// Language picked by the user, shared by every screen.
  Language get language => _language;

  /// True once both a URL and a key are set.
  bool get isConfigured => _baseUrl.isNotEmpty && _apiKey.isNotEmpty;

  /// The client, or null until [isConfigured].
  NAtlasClient? get client => _client;

  set language(Language value) {
    if (value == _language) return;
    _language = value;
    notifyListeners();
  }

  /// Applies new server settings.
  void updateServer({required String baseUrl, required String apiKey}) {
    final url = baseUrl.trim();
    final key = apiKey.trim();
    if (url == _baseUrl && key == _apiKey) return;
    _baseUrl = url;
    _apiKey = key;
    _rebuild();
    notifyListeners();
    _persist(url, key).ignore();
  }

  Future<void> _persist(String url, String key) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_urlPref, url);
      await prefs.setString(_keyPref, key);
    } catch (_) {
      // Saving is a convenience; the settings still apply for this session.
    }
  }

  void _rebuild() {
    final old = _client;
    _client = isConfigured
        ? NAtlasClient(NAtlasConfig(baseUrl: _baseUrl, apiKey: _apiKey))
        : null;
    old?.close().ignore();
  }

  @override
  void dispose() {
    _client?.close().ignore();
    super.dispose();
  }
}

/// Plain-English names for the language picker.
String languageLabel(Language l) => switch (l) {
      Language.hausa => 'Hausa',
      Language.igbo => 'Igbo',
      Language.yoruba => 'Yoruba',
      Language.english => 'English (Nigerian)',
    };
