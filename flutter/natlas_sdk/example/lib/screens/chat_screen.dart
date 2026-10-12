import 'dart:async';

import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';

import '../app_state.dart';
import '../widgets.dart';

/// Streaming chat with N-ATLaS.
class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.state});

  final AppState state;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _input = TextEditingController();
  final List<ChatMessage> _history = [];
  String? _streaming; // reply being written, or null
  StreamSubscription<ChatDelta>? _sub;
  Object? _error;

  bool get _busy => _streaming != null;

  void _send([String? retryText]) {
    final client = widget.state.client;
    final text = (retryText ?? _input.text).trim();
    if (client == null || text.isEmpty || _busy) return;
    if (retryText == null) {
      _history.add(ChatMessage.user(text));
      _input.clear();
    }
    setState(() {
      _streaming = '';
      _error = null;
    });
    final buffer = StringBuffer();
    _sub = client.chat.stream(List.of(_history)).listen(
      (d) {
        buffer.write(d.text);
        if (mounted) setState(() => _streaming = buffer.toString());
      },
      onError: (Object e) {
        if (mounted) setState(() => _error = e);
      },
      onDone: () {
        if (!mounted) return;
        setState(() {
          if (_error == null && buffer.isNotEmpty) {
            _history.add(ChatMessage.assistant(buffer.toString()));
          }
          _streaming = null;
          _sub = null;
        });
      },
    );
  }

  void _cancel() {
    // Cancelling the subscription cancels the HTTP request in Kotlin.
    _sub?.cancel();
    setState(() {
      final partial = _streaming;
      if (partial != null && partial.isNotEmpty) {
        _history.add(ChatMessage.assistant('$partial …'));
      }
      _streaming = null;
      _sub = null;
    });
  }

  @override
  void dispose() {
    _sub?.cancel();
    _input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final items = [
      for (final m in _history) _Bubble(text: m.content, mine: m.role == Role.user),
      if (_streaming != null)
        _Bubble(text: _streaming!.isEmpty ? 'Thinking…' : _streaming!, mine: false),
    ];
    return Column(
      children: [
        Expanded(
          child: items.isEmpty
              ? const EmptyHint(
                  icon: Icons.chat_bubble_outline,
                  text: 'Ask N-ATLAS anything in English, Hausa, Igbo or '
                      'Yoruba.\nFor example: Sannu! Yaya kake?',
                )
              : ListView(
                  reverse: true,
                  padding: const EdgeInsets.all(12),
                  children: items.reversed.toList(),
                ),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: ErrorCard(
              error: _error!,
              onRetry: _history.isNotEmpty && _history.last.role == Role.user
                  ? () => _send(_history.last.content)
                  : null,
            ),
          ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _input,
                    enabled: !_busy,
                    minLines: 1,
                    maxLines: 4,
                    textInputAction: TextInputAction.send,
                    onSubmitted: (_) => _send(),
                    decoration: const InputDecoration(
                      hintText: 'Type a message',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                _busy
                    ? IconButton.filledTonal(
                        tooltip: 'Stop',
                        onPressed: _cancel,
                        icon: const Icon(Icons.stop))
                    : IconButton.filled(
                        tooltip: 'Ask N-ATLAS',
                        onPressed: () => _send(),
                        icon: const Icon(Icons.send)),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _Bubble extends StatelessWidget {
  const _Bubble({required this.text, required this.mine});

  final String text;
  final bool mine;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.all(12),
        constraints: const BoxConstraints(maxWidth: 320),
        decoration: BoxDecoration(
          color: mine ? scheme.primaryContainer : scheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(12),
        ),
        child: SelectableText(text),
      ),
    );
  }
}
