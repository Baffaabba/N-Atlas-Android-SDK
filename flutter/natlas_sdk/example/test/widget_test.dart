import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:natlas_sdk_example/app_state.dart';
import 'package:natlas_sdk_example/main.dart';
import 'package:natlas_sdk_example/widgets.dart';
import 'package:natlas_sdk/natlas_sdk.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('without a server it asks for settings', (tester) async {
    final state = AppState();
    // Run without --dart-define, so nothing is configured.
    if (state.isConfigured) return;
    await tester.pumpWidget(NAtlasExampleApp(state: state));
    expect(find.text('Open settings'), findsOneWidget);
  });

  testWidgets('About shows the attribution and the user cap', (tester) async {
    await tester.pumpWidget(NAtlasExampleApp(state: AppState()));
    await tester.tap(find.byTooltip('About'));
    await tester.pumpAndSettle();
    expect(find.text(NAtlasClient.attribution), findsOneWidget);
    expect(find.textContaining('1,000 active users'), findsOneWidget);
  });

  test('every SDK error has a plain message with a next step', () {
    const errors = <NAtlasException>[
      NAtlasUnauthorized('x'),
      NAtlasUserCapReached('x'),
      NAtlasRateLimited('x', retryAfterSeconds: 5),
      NAtlasInvalidRequest('x'),
      NAtlasUnsupportedAudio('x'),
      NAtlasAudioTooLarge('x'),
      NAtlasModelLoading('x'),
      NAtlasServerError('x', httpCode: 500),
      NAtlasNetwork('x'),
      NAtlasTimeout('x'),
      NAtlasMicPermissionDenied('x'),
      NAtlasMicUnavailable('x'),
    ];
    for (final e in errors) {
      expect(describeError(e), isNot(startsWith('Something went wrong')), reason: '$e');
    }
    expect(isOffline(const NAtlasNetwork('x')), isTrue);
  });

  test('language labels', () {
    expect(Language.values.map(languageLabel),
        ['Hausa', 'Igbo', 'Yoruba', 'English (Nigerian)']);
  });

  testWidgets('settings accept a new server address at runtime', (tester) async {
    final state = AppState();
    await tester.pumpWidget(NAtlasExampleApp(state: state));
    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Server address'),
        'https://abc.trycloudflare.com');
    await tester.enterText(find.widgetWithText(TextField, 'API key'), 'k');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(state.baseUrl, 'https://abc.trycloudflare.com');
    expect(state.isConfigured, isTrue);
    expect(state.client, isNotNull);
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    final prefs = await SharedPreferences.getInstance();
    expect(prefs.getString('natlas_base_url'), 'https://abc.trycloudflare.com');
  });

  test('saved settings are loaded on start', () async {
    SharedPreferences.setMockInitialValues({
      'natlas_base_url': 'https://saved.example.com',
      'natlas_api_key': 'saved',
    });
    final state = await AppState.load();
    expect(state.baseUrl, 'https://saved.example.com');
    expect(state.isConfigured, isTrue);
  });
}
