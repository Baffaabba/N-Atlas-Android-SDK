import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:natlas_sdk/natlas_sdk.dart';
import 'package:natlas_sdk/src/bridge.dart';

import 'fakes.dart';

PlatformException pe(String code, [Object? details]) =>
    PlatformException(code: code, message: 'msg for $code', details: details);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('mapError maps every Kotlin NAtlasException one-to-one', () {
    // Kotlin subtype simple name -> expected Dart type check.
    final cases = <String, bool Function(Object)>{
      'Unauthorized': (e) => e is NAtlasUnauthorized,
      'UserCapReached': (e) => e is NAtlasUserCapReached,
      'RateLimited': (e) => e is NAtlasRateLimited,
      'InvalidRequest': (e) => e is NAtlasInvalidRequest,
      'UnsupportedAudio': (e) => e is NAtlasUnsupportedAudio,
      'AudioTooLarge': (e) => e is NAtlasAudioTooLarge,
      'ModelLoading': (e) => e is NAtlasModelLoading,
      'ServerError': (e) => e is NAtlasServerError,
      'Network': (e) => e is NAtlasNetwork,
      'Timeout': (e) => e is NAtlasTimeout,
      'MicPermissionDenied': (e) => e is NAtlasMicPermissionDenied,
      'MicUnavailable': (e) => e is NAtlasMicUnavailable,
    };

    for (final entry in cases.entries) {
      test(entry.key, () {
        final mapped = mapError(pe(entry.key));
        expect(mapped, isA<NAtlasException>());
        expect(entry.value(mapped), isTrue, reason: '$mapped');
        expect((mapped as NAtlasException).message, 'msg for ${entry.key}');
        // Dart name = 'NAtlas' + Kotlin simple name.
        expect(mapped.runtimeType.toString(), 'NAtlas${entry.key}');
      });
    }

    test('twelve codes give twelve distinct Dart types', () {
      final types = cases.keys.map((c) => mapError(pe(c)).runtimeType).toSet();
      expect(types, hasLength(12));
    });
  });

  group('mapError carries fields', () {
    test('RateLimited.retryAfterSeconds', () {
      final e = mapError(pe('RateLimited', {'retryAfterSeconds': 7})) as NAtlasRateLimited;
      expect(e.retryAfterSeconds, 7);
    });

    test('RateLimited without Retry-After', () {
      final e = mapError(pe('RateLimited', {'retryAfterSeconds': null})) as NAtlasRateLimited;
      expect(e.retryAfterSeconds, isNull);
    });

    test('ModelLoading.retryAfterSeconds', () {
      final e = mapError(pe('ModelLoading', {'retryAfterSeconds': 30})) as NAtlasModelLoading;
      expect(e.retryAfterSeconds, 30);
    });

    test('ServerError.httpCode and code', () {
      final e = mapError(pe('ServerError', {'httpCode': 503, 'code': 'backend_unavailable'}))
          as NAtlasServerError;
      expect(e.httpCode, 503);
      expect(e.code, 'backend_unavailable');
    });

    test('Network.cause', () {
      final e = mapError(pe('Network', {'cause': 'java.net.UnknownHostException: x'})) as NAtlasNetwork;
      expect(e.cause, contains('UnknownHostException'));
    });
  });

  group('non-NAtlas errors', () {
    test('IllegalArgument becomes ArgumentError', () {
      expect(mapError(pe('IllegalArgument')), isA<ArgumentError>());
    });

    test('IllegalState becomes StateError', () {
      expect(mapError(pe('IllegalState')), isA<StateError>());
    });

    test('unknown code is passed through, never swallowed', () {
      final original = pe('SomethingNew');
      expect(identical(mapError(original), original), isTrue);
    });

    test('non-platform errors are passed through', () {
      final e = FormatException('x');
      expect(identical(mapError(e), e), isTrue);
    });
  });

  group('errors surface from calls as Dart types', () {
    late FakeHostApi api;
    setUp(() {
      api = FakeHostApi();
      NAtlasBridge.instance = NAtlasBridge(api: api, events: api.events);
    });

    NAtlasClient newClient() =>
        NAtlasClient(const NAtlasConfig(baseUrl: 'https://x.test', apiKey: 'k'));

    test('transcribe throws Unauthorized', () async {
      final client = newClient();
      await client.asr.transcribe('/a.wav', Language.hausa); // create the client
      api.throwOnNext = pe('Unauthorized');
      await expectLater(
          client.asr.transcribe('/a.wav', Language.hausa), throwsA(isA<NAtlasUnauthorized>()));
    });

    test('chat.complete throws NAtlasRateLimited with retryAfter', () async {
      final client = newClient();
      await client.chat.complete([const ChatMessage.user('hi')]);
      api.throwOnNext = pe('RateLimited', {'retryAfterSeconds': 3});
      await expectLater(
        client.chat.complete([const ChatMessage.user('hi')]),
        throwsA(isA<NAtlasRateLimited>().having((e) => e.retryAfterSeconds, 'retryAfter', 3)),
      );
    });

    test('a bad config surfaces as ArgumentError on first use', () async {
      api.throwOnNext = pe('IllegalArgument');
      final client = newClient();
      await expectLater(
          client.asr.transcribe('/a.wav', Language.hausa), throwsA(isA<ArgumentError>()));
    });

    test('NAtlasException is sealed: exhaustive switch compiles', () {
      String describe(NAtlasException e) => switch (e) {
            NAtlasUnauthorized() => 'a',
            NAtlasUserCapReached() => 'b',
            NAtlasRateLimited() => 'c',
            NAtlasInvalidRequest() => 'd',
            NAtlasUnsupportedAudio() => 'e',
            NAtlasAudioTooLarge() => 'f',
            NAtlasModelLoading() => 'g',
            NAtlasServerError() => 'h',
            NAtlasNetwork() => 'i',
            NAtlasTimeout() => 'j',
            NAtlasMicPermissionDenied() => 'k',
            NAtlasMicUnavailable() => 'l',
          };
      expect(describe(const NAtlasTimeout('t')), 'j');
    });
  });
}
