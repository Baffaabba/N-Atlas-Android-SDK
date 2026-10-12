import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';

import '../app_state.dart';

/// Change the server address and key while the app runs. The Colab test
/// server gets a new address every session.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.state});

  final AppState state;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final _url = TextEditingController(text: widget.state.baseUrl);
  late final _key = TextEditingController(text: widget.state.apiKey);
  bool _showKey = false;
  bool _checking = false;
  String? _micStatus;

  @override
  void dispose() {
    _url.dispose();
    _key.dispose();
    super.dispose();
  }

  String? _urlError(String v) {
    if (v.trim().isEmpty) return null;
    final uri = Uri.tryParse(v.trim());
    if (uri == null || !(uri.scheme == 'https' || uri.scheme == 'http') || uri.host.isEmpty) {
      return 'Use a full address, like https://example.trycloudflare.com';
    }
    return null;
  }

  void _save() {
    if (_urlError(_url.text) != null) {
      setState(() {});
      return;
    }
    widget.state.updateServer(baseUrl: _url.text, apiKey: _key.text);
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('Saved.')));
    Navigator.of(context).maybePop();
  }

  Future<void> _checkMic() async {
    setState(() => _checking = true);
    try {
      final ok = await NAtlasClient.hasMicPermission();
      _micStatus = ok ? 'Microphone allowed.' : 'Microphone not allowed yet.';
    } catch (e) {
      _micStatus = 'Could not check: $e';
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _url,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: 'Server address',
              hintText: 'https://example.trycloudflare.com',
              border: const OutlineInputBorder(),
              errorText: _urlError(_url.text),
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _key,
            obscureText: !_showKey,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: 'API key',
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                tooltip: _showKey ? 'Hide key' : 'Show key',
                icon: Icon(_showKey ? Icons.visibility_off : Icons.visibility),
                onPressed: () => setState(() => _showKey = !_showKey),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Type the server address and key you were given. They are saved '
            'on this phone (in app storage, not encrypted) and used next time. '
            'Developers can also set them with --dart-define=NATLAS_BASE_URL '
            'and --dart-define=NATLAS_API_KEY.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          FilledButton(onPressed: _save, child: const Text('Save')),
          const Divider(height: 32),
          OutlinedButton(
            onPressed: _checking ? null : _checkMic,
            child: const Text('Check microphone permission'),
          ),
          if (_micStatus != null) ...[
            const SizedBox(height: 8),
            Text(_micStatus!),
          ],
        ],
      ),
    );
  }
}
