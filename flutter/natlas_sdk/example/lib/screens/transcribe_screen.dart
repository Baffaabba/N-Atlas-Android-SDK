import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';

import '../app_state.dart';
import '../widgets.dart';

/// Pick an audio file and get the text back.
class TranscribeScreen extends StatefulWidget {
  const TranscribeScreen({super.key, required this.state});

  final AppState state;

  @override
  State<TranscribeScreen> createState() => _TranscribeScreenState();
}

class _TranscribeScreenState extends State<TranscribeScreen> {
  String? _fileName;
  String? _path;
  bool _busy = false;
  Transcript? _result;
  Object? _error;

  Future<void> _pick() async {
    final file = await FilePicker.pickFile(type: FileType.audio);
    if (file == null) return; // the user closed the picker
    if (file.path == null) {
      setState(() => _error = StateError(
          "We couldn't open that file. Pick a file saved on this phone."));
      return;
    }
    setState(() {
      _fileName = file.name;
      _path = file.path;
      _result = null;
      _error = null;
    });
    await _transcribe();
  }

  Future<void> _transcribe() async {
    final client = widget.state.client;
    final path = _path;
    if (client == null || path == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final t = await client.asr.transcribe(path, widget.state.language);
      if (mounted) setState(() => _result = t);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final result = _result;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        LanguagePicker(state: widget.state, enabled: !_busy),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _busy ? null : _pick,
          icon: const Icon(Icons.audio_file),
          label: const Text('Choose an audio file'),
        ),
        if (_fileName != null) ...[
          const SizedBox(height: 8),
          Text(_fileName!, style: Theme.of(context).textTheme.bodySmall),
        ],
        const SizedBox(height: 16),
        if (_busy)
          const Column(children: [
            LinearProgressIndicator(),
            SizedBox(height: 8),
            Text('Writing down what was said…'),
          ])
        else if (_error != null)
          ErrorCard(error: _error!, onRetry: _transcribe)
        else if (result != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    result.text.isEmpty ? '(No speech found)' : result.text,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    [
                      languageLabel(result.language),
                      if (result.durationSeconds != null)
                        '${result.durationSeconds!.toStringAsFixed(1)} s',
                      result.model,
                    ].join(' · '),
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          )
        else
          const EmptyHint(
            icon: Icons.graphic_eq,
            text: 'Pick a recording to see its words here. '
                'wav, mp3, m4a, ogg, flac or webm, up to 25 MB.',
          ),
      ],
    );
  }
}
