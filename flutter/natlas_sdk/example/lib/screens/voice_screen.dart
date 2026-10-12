import 'dart:async';

import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';

import '../app_state.dart';
import '../widgets.dart';

/// "Talk to N-ATLAS": one tap records the user, writes down what they said
/// and streams the reply. Text only: N-ATLAS has no text-to-speech model
/// yet, so replies come back as text.
class VoiceScreen extends StatefulWidget {
  const VoiceScreen({super.key, required this.state});

  final AppState state;

  @override
  State<VoiceScreen> createState() => _VoiceScreenState();
}

enum _Phase { idle, connecting, listening, thinking, replying }

class _Turn {
  _Turn(this.user, this.reply);
  final String user;
  final String reply;
}

class _VoiceScreenState extends State<VoiceScreen> {
  _Phase _phase = _Phase.idle;
  VoiceTurn? _turn;
  StreamSubscription<VoiceEvent>? _sub;
  final List<_Turn> _done = [];
  String _heard = '';
  String _reply = '';
  bool _nothingHeard = false;
  Object? _error;

  List<ChatMessage> get _history => [
        for (final t in _done) ...[
          ChatMessage.user(t.user),
          ChatMessage.assistant(t.reply),
        ],
      ];

  Future<void> _talk() async {
    final client = widget.state.client;
    if (client == null) return;
    if (!await ensureMicPermission(context)) return;
    setState(() {
      _phase = _Phase.connecting;
      _heard = '';
      _reply = '';
      _error = null;
      _nothingHeard = false;
    });
    try {
      final turn = await client.voice.converse(
        widget.state.language,
        history: _history,
        autoStop: true,
      );
      _turn = turn;
      _sub = turn.events.listen(
        (e) {
          if (!mounted) return;
          setState(() {
            switch (e) {
              case VoiceListening():
                _phase = _Phase.listening;
              case VoiceHeard(:final text):
                _heard = text;
              case VoiceThinking():
                _phase = _Phase.thinking;
              case VoiceReply(:final delta):
                _phase = _Phase.replying;
                _reply += delta;
              case VoiceDone(:final userText, :final replyText):
                // Nothing heard: the SDK ends with empty texts and N-ATLAS
                // is not asked.
                _nothingHeard = userText.isEmpty;
                if (!_nothingHeard) _done.add(_Turn(userText, replyText));
                _heard = '';
                _reply = '';
            }
          });
        },
        onError: (Object e) {
          if (mounted) setState(() => _error = e);
        },
        onDone: () {
          _turn = null;
          _sub = null;
          if (mounted) setState(() => _phase = _Phase.idle);
        },
      );
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _phase = _Phase.idle;
        });
      }
    }
  }

  Future<void> _finishSpeaking() async {
    try {
      await _turn?.stop();
    } catch (e) {
      await _cancel();
      if (mounted) setState(() => _error = e);
    }
  }

  Future<void> _cancel() async {
    await _sub?.cancel();
    _sub = null;
    _turn = null;
    if (mounted) setState(() => _phase = _Phase.idle);
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = switch (_phase) {
      _Phase.idle => null,
      _Phase.connecting => 'Connecting…',
      _Phase.listening => 'Listening… stop talking when you are done.',
      _Phase.thinking => 'Thinking…',
      _Phase.replying => 'N-ATLAS is answering…',
    };
    final hasContent = _done.isNotEmpty || _heard.isNotEmpty || _reply.isNotEmpty;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LanguagePicker(state: widget.state, enabled: _phase == _Phase.idle),
          const SizedBox(height: 8),
          Expanded(
            child: hasContent
                ? ListView(
                    children: [
                      for (final t in _done) ...[
                        _Line(label: 'You', text: t.user),
                        _Line(label: 'N-ATLAS', text: t.reply),
                        const Divider(),
                      ],
                      if (_heard.isNotEmpty) _Line(label: 'You', text: _heard),
                      if (_reply.isNotEmpty) _Line(label: 'N-ATLAS', text: _reply),
                    ],
                  )
                : const EmptyHint(
                    icon: Icons.record_voice_over_outlined,
                    text: 'Tap Talk to N-ATLAS and ask a question out loud. '
                        'The answer appears as text.',
                  ),
          ),
          if (_error != null) ErrorCard(error: _error!, onRetry: _talk),
          if (_nothingHeard && _phase == _Phase.idle)
            const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: Text("We didn't hear anything. Tap Talk to N-ATLAS and try again.",
                  textAlign: TextAlign.center),
            ),
          if (status != null) ...[
            Text(status, textAlign: TextAlign.center, style: theme.textTheme.bodyLarge),
            const SizedBox(height: 8),
          ],
          switch (_phase) {
            _Phase.idle => FilledButton.icon(
                onPressed: _talk,
                icon: const Icon(Icons.mic),
                label: const Text('Talk to N-ATLAS'),
              ),
            _Phase.listening || _Phase.connecting => FilledButton.icon(
                onPressed: _finishSpeaking,
                icon: const Icon(Icons.stop),
                label: const Text("I'm done talking"),
              ),
            _ => OutlinedButton.icon(
                onPressed: _cancel,
                icon: const Icon(Icons.close),
                label: const Text('Cancel'),
              ),
          },
        ],
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line({required this.label, required this.text});

  final String label;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.labelMedium),
          SelectableText(text, style: Theme.of(context).textTheme.bodyLarge),
        ],
      ),
    );
  }
}
