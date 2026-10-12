import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:natlas_sdk/natlas_sdk.dart';
import 'package:natlas_sdk/src/bridge.dart';
import 'package:natlas_sdk/src/messages.g.dart';

import 'fakes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeHostApi api;
  late NAtlasClient client;

  setUp(() {
    api = FakeHostApi();
    NAtlasBridge.instance = NAtlasBridge(api: api, events: api.events);
    client = NAtlasClient(const NAtlasConfig(
      baseUrl: 'https://x.test',
      apiKey: 'k',
      connectTimeout: Duration(seconds: 5),
      userId: 'u1',
    ));
  });

  /// Lets queued microtasks and async listeners run.
  Future<void> pump() => Future<void>.delayed(Duration.zero);

  group('types mirror Kotlin', () {
    test('Language codes and models', () {
      expect(Language.values.map((l) => l.code), ['ha', 'ig', 'yo', 'en']);
      expect(Language.hausa.asrModel, 'NCAIR1/Hausa-ASR');
      expect(Language.english.asrModel, 'NCAIR1/NigerianAccentedEnglish');
      expect(Language.fromCode('yo'), Language.yoruba);
      expect(Language.fromCode('xx'), isNull);
    });

    test('ChatOptions defaults follow the model card (as Kotlin)', () {
      const o = ChatOptions();
      expect(o.temperature, 0.1);
      expect(o.maxTokens, 512);
      expect(o.repetitionPenalty, 1.12);
      expect(o.topP, isNull);
      expect(o.stop, isEmpty);
    });

    test('NAtlasConfig defaults match Kotlin', () {
      const c = NAtlasConfig(baseUrl: 'u', apiKey: 'k');
      expect(c.connectTimeout, const Duration(seconds: 15));
      expect(c.readTimeout, const Duration(seconds: 120));
      expect(c.maxRetries, 2);
      expect(c.userId, isNull);
    });

    test('attribution text', () {
      expect(NAtlasClient.attribution,
          startsWith('N-ATLAS is an initiative of the Federal Ministry'));
    });
  });

  group('client lifecycle', () {
    test('Kotlin client is created lazily, once, with the config', () async {
      expect(api.calls, isEmpty);
      await client.asr.transcribe('/a.wav', Language.hausa);
      await client.chat.complete([const ChatMessage.user('x')]);
      expect(api.calls.where((c) => c == 'createClient'), hasLength(1));
      final cfg = api.lastConfig!;
      expect(cfg.baseUrl, 'https://x.test');
      expect(cfg.connectTimeoutMs, 5000);
      expect(cfg.readTimeoutMs, 120000);
      expect(cfg.maxRetries, 2);
      expect(cfg.userId, 'u1');
      expect(cfg.userAgent, 'natlas-flutter/${NAtlasClient.version}');
    });

    test('close releases the Kotlin client; later calls throw StateError', () async {
      await client.asr.transcribe('/a.wav', Language.hausa);
      await client.close();
      expect(api.calls, contains('closeClient:1'));
      await expectLater(client.asr.transcribe('/a.wav', Language.hausa),
          throwsA(isA<StateError>()));
    });

    test('close before first use does not create a client', () async {
      await client.close();
      expect(api.calls, isEmpty);
    });

    test('hasMicPermission goes to Kotlin', () async {
      expect(await NAtlasClient.hasMicPermission(), isTrue);
      expect(api.calls, ['hasMicPermission']);
    });
  });

  group('one-shot calls convert messages', () {
    test('transcribe', () async {
      final t = await client.asr.transcribe('/clip.wav', Language.hausa);
      expect(api.calls.last, 'transcribe:1:/clip.wav:hausa');
      expect(t.text, 'Ina kwana');
      expect(t.language, Language.hausa);
      expect(t.durationSeconds, 1.5);
      expect(t.segments.single.end, 1.5);
    });

    test('chat.complete', () async {
      final r = await client.chat.complete(
        [const ChatMessage.system('Be brief'), const ChatMessage.user('Sannu!')],
        options: const ChatOptions(temperature: 0.5, stop: ['\n']),
      );
      expect(api.lastMessages!.map((m) => m.role),
          [RoleMsg.system, RoleMsg.user]);
      expect(api.lastMessages!.last.content, 'Sannu!');
      expect(api.lastOptions!.temperature, 0.5);
      expect(api.lastOptions!.stop, ['\n']);
      expect(r.text, 'Lafiya lau');
      expect(r.usage!.totalTokens, 5);
    });
  });

  group('chat.stream lifecycle', () {
    test('nothing starts until listen', () async {
      client.chat.stream([const ChatMessage.user('x')]);
      await pump();
      expect(api.calls, isNot(contains('startChatStream')));
    });

    test('deltas arrive in order and done closes the stream', () async {
      final got = <String>[];
      final done = Completer<void>();
      client.chat.stream([const ChatMessage.user('x')]).listen(
            (d) => got.add(d.text),
            onDone: done.complete,
          );
      await pump();
      final s = api.sessions.values.single;
      expect(s.listened, 1);
      s.emit(ChatDeltaMsg(text: 'Lafiya'));
      s.emit(ChatDeltaMsg(text: ' lau', finishReason: 'stop'));
      await s.end();
      await done.future;
      expect(got, ['Lafiya', ' lau']);
      // EventChannel streams are broadcast controllers too: after the
      // platform ends the stream, onCancel still fires, which sends 'cancel'
      // to Kotlin so the plugin unregisters the session channel.
      await pump();
      expect(s.cancelled, 1);
    });

    test('cancelling the subscription cancels the Kotlin side', () async {
      final sub = client.chat.stream([const ChatMessage.user('x')]).listen((_) {});
      await pump();
      final s = api.sessions.values.single;
      expect(s.listened, 1);
      await sub.cancel();
      expect(s.cancelled, 1);
    });

    test('cancel while the session is starting cancels it once known', () async {
      api.holdChatStart = Completer<void>();
      final sub = client.chat.stream([const ChatMessage.user('x')]).listen((_) {});
      await pump();
      await sub.cancel();
      api.holdChatStart!.complete();
      await pump();
      await pump();
      final id = api.sessions.keys.single;
      expect(api.calls, contains('cancelSession:$id'));
      expect(api.sessions[id]!.listened, 0, reason: 'never subscribed');
    });

    test('a stream error is mapped, then the stream ends', () async {
      final events = <Object>[];
      final done = Completer<void>();
      client.chat.stream([const ChatMessage.user('x')]).listen(
            (d) => events.add(d),
            onError: (Object e) => events.add(e),
            onDone: done.complete,
          );
      await pump();
      final s = api.sessions.values.single;
      s.emit(ChatDeltaMsg(text: 'a'));
      s.fail('Network', details: {'cause': 'socket closed'});
      await s.end();
      await done.future;
      expect(events, hasLength(2));
      expect(events[1], isA<NAtlasNetwork>().having((e) => e.cause, 'cause', 'socket closed'));
    });

    test('a failure to start is mapped and ends the stream', () async {
      // Create the Kotlin client first so startChatStream gets the error.
      await client.chat.complete([const ChatMessage.user('x')]);
      api.throwOnNext = PlatformException(code: 'Unauthorized', message: 'bad key');
      await expectLater(
        client.chat.stream([const ChatMessage.user('x')]),
        emitsInOrder([emitsError(isA<NAtlasUnauthorized>()), emitsDone]),
      );
    });

    test('await for works and surfaces NAtlasException', () async {
      Future<void> run() async {
        await for (final _ in client.chat.stream([const ChatMessage.user('x')])) {}
      }

      final f = run();
      await pump();
      api.sessions.values.single.fail('Timeout');
      await expectLater(f, throwsA(isA<NAtlasTimeout>()));
    });
  });

  group('live transcription', () {
    test('events map; stop() finishes gracefully', () async {
      final live = await client.asr.live(Language.yoruba);
      expect(api.calls.last, 'startLive:yoruba');
      final got = <LiveEvent>[];
      final done = Completer<void>();
      live.events.listen(got.add, onDone: done.complete);
      await pump();
      final s = api.sessions.values.single;
      s.emit(LiveReadyMsg(model: 'NCAIR1/Yoruba-ASR'));
      s.emit(LivePartialMsg(text: 'Ẹ kú', start: 0, end: 1));
      s.emit(LiveFinalMsg(text: 'Ẹ kú àárọ̀', start: 0, end: 2));
      await live.stop();
      await done.future;
      expect(api.calls, contains('stopSession:${s.id}'));
      expect(got[0], isA<LiveReady>());
      expect(got[1], isA<LivePartial>().having((e) => e.text, 'text', 'Ẹ kú'));
      expect(got[2], isA<LiveFinal>().having((e) => e.end, 'end', 2));
    });

    test('mic is not touched until listen; cancel aborts', () async {
      final live = await client.asr.live(Language.hausa);
      await pump();
      final s = api.sessions.values.single;
      expect(s.listened, 0);
      final sub = live.events.listen((_) {});
      await pump();
      expect(s.listened, 1);
      await sub.cancel();
      expect(s.cancelled, 1);
    });

    test('events can be listened to once', () async {
      final live = await client.asr.live(Language.hausa);
      live.events.listen((_) {});
      expect(() => live.events.listen((_) {}), throwsStateError);
    });

    test('MicPermissionDenied arrives on the stream', () async {
      final live = await client.asr.live(Language.hausa);
      final f = live.events.toList();
      await pump();
      final s = api.sessions.values.single;
      s.fail('MicPermissionDenied');
      await s.end();
      await expectLater(f, throwsA(isA<NAtlasMicPermissionDenied>()));
    });
  });

  group('voice turn', () {
    test('passes history and autoStop, maps every event', () async {
      final turn = await client.voice.converse(
        Language.hausa,
        history: [const ChatMessage.user('a'), const ChatMessage.assistant('b')],
        autoStop: true,
      );
      expect(api.lastAutoStop, isTrue);
      expect(api.lastMessages!.map((m) => m.role), [RoleMsg.user, RoleMsg.assistant]);

      final f = turn.events.toList();
      await pump();
      final s = api.sessions.values.single;
      s
        ..emit(VoiceListeningMsg())
        ..emit(VoiceHeardMsg(text: 'Ƙasa', isFinal: false))
        ..emit(VoiceHeardMsg(text: 'Ƙasa ɗaya', isFinal: true))
        ..emit(VoiceThinkingMsg())
        ..emit(VoiceReplyMsg(delta: 'To'))
        ..emit(VoiceDoneMsg(userText: 'Ƙasa ɗaya', replyText: 'To'));
      await s.end();
      final events = await f;
      expect(events.map((e) => e.runtimeType.toString()), [
        'VoiceListening',
        'VoiceHeard',
        'VoiceHeard',
        'VoiceThinking',
        'VoiceReply',
        'VoiceDone',
      ]);
      expect((events.last as VoiceDone).userText, 'Ƙasa ɗaya');
    });

    test('stop() calls Kotlin stop; cancel aborts', () async {
      final turn = await client.voice.converse(Language.igbo);
      final sub = turn.events.listen((_) {});
      await pump();
      final s = api.sessions.values.single;
      await turn.stop();
      expect(api.calls, contains('stopSession:${s.id}'));
      await sub.cancel();
    });

    test('a wrong event kind is reported, not dropped', () async {
      final turn = await client.voice.converse(Language.igbo);
      final errors = <Object>[];
      turn.events.listen((_) {}, onError: errors.add);
      await pump();
      api.sessions.values.single.emit(ChatDeltaMsg(text: 'x'));
      await pump();
      expect(errors.single, isA<StateError>());
    });
  });
}
