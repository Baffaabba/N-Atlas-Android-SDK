import 'dart:async';

import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';

import '../app_state.dart';
import '../widgets.dart';

/// Live microphone transcription with partial text.
class LiveScreen extends StatefulWidget {
  const LiveScreen({super.key, required this.state});

  final AppState state;

  @override
  State<LiveScreen> createState() => _LiveScreenState();
}

enum _Phase { idle, connecting, listening, finishing }

class _LiveScreenState extends State<LiveScreen> {
  _Phase _phase = _Phase.idle;
  LiveTranscription? _live;
  StreamSubscription<LiveEvent>? _sub;
  final List<String> _finals = [];
  String _partial = '';
  Object? _error;

  Future<void> _start() async {
    final client = widget.state.client;
    if (client == null) return;
    if (!await ensureMicPermission(context)) return;
    setState(() {
      _phase = _Phase.connecting;
      _finals.clear();
      _partial = '';
      _error = null;
    });
    try {
      final live = await client.asr.live(widget.state.language);
      _live = live;
      _sub = live.events.listen(
        (e) {
          if (!mounted) return;
          setState(() {
            switch (e) {
              case LiveReady():
                _phase = _Phase.listening;
              case LivePartial(:final text):
                _partial = text;
              case LiveFinal(:final text):
                if (text.isNotEmpty) _finals.add(text);
                _partial = '';
            }
          });
        },
        onError: (Object e) {
          if (mounted) setState(() => _error = e);
        },
        onDone: () {
          _live = null;
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

  Future<void> _stop() async {
    setState(() => _phase = _Phase.finishing);
    try {
      await _live?.stop();
    } catch (e) {
      // Stop failed: abort instead, which always releases the microphone.
      await _sub?.cancel();
      if (mounted) {
        setState(() {
          _error = e;
          _phase = _Phase.idle;
        });
      }
    }
  }

  @override
  void dispose() {
    // Leaving the screen aborts the session and releases the microphone.
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final running = _phase != _Phase.idle;
    final hasText = _finals.isNotEmpty || _partial.isNotEmpty;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LanguagePicker(state: widget.state, enabled: !running),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: switch (_phase) {
              _Phase.idle => _start,
              _Phase.listening || _Phase.connecting => _stop,
              _Phase.finishing => null,
            },
            icon: Icon(running ? Icons.stop : Icons.mic),
            label: Text(switch (_phase) {
              _Phase.idle => 'Start recording',
              _Phase.connecting => 'Connecting… tap to stop',
              _Phase.listening => 'Stop recording',
              _Phase.finishing => 'Finishing…',
            }),
          ),
          const SizedBox(height: 8),
          if (_phase == _Phase.listening)
            const Text('Listening… speak now.', textAlign: TextAlign.center),
          if (_error != null) ErrorCard(error: _error!, onRetry: _start),
          const SizedBox(height: 8),
          Expanded(
            child: hasText
                ? SingleChildScrollView(
                    reverse: true,
                    child: Text.rich(
                      TextSpan(children: [
                        for (final f in _finals) TextSpan(text: '$f\n'),
                        TextSpan(
                          text: _partial,
                          style: TextStyle(
                              color: Theme.of(context).colorScheme.outline),
                        ),
                      ]),
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  )
                : const EmptyHint(
                    icon: Icons.mic_none,
                    text: 'Tap Start recording and speak. Your words appear '
                        'here as you talk. Grey text may still change.',
                  ),
          ),
        ],
      ),
    );
  }
}
