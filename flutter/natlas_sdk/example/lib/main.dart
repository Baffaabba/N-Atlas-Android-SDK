import 'package:flutter/material.dart';

import 'app_state.dart';
import 'screens/about_screen.dart';
import 'screens/chat_screen.dart';
import 'screens/live_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/transcribe_screen.dart';
import 'screens/voice_screen.dart';
import 'widgets.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(NAtlasExampleApp(state: await AppState.load()));
}

/// The example app.
class NAtlasExampleApp extends StatelessWidget {
  const NAtlasExampleApp({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    // Noto Sans is bundled (assets/fonts) so ɓ ɗ ƙ, ẹ ọ ṣ and ị ụ ṅ render
    // the same on every phone, offline.
    return MaterialApp(
      title: 'N-ATLAS',
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF008751),
        fontFamily: 'NotoSans',
        useMaterial3: true,
      ),
      home: HomePage(state: state),
    );
  }
}

/// Bottom tabs for the four features.
class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.state});

  final AppState state;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _tab = 0;

  static const _titles = ['Transcribe a file', 'Live', 'Chat', 'Talk'];

  void _openSettings() => Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => SettingsScreen(state: widget.state)));

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.state,
      builder: (context, _) {
        final s = widget.state;
        // The key makes screens start fresh (and drop running sessions)
        // when the server changes.
        final key = ValueKey(s.client);
        final body = !s.isConfigured
            ? NotConfigured(onOpenSettings: _openSettings)
            : switch (_tab) {
                0 => TranscribeScreen(key: key, state: s),
                1 => LiveScreen(key: key, state: s),
                2 => ChatScreen(key: key, state: s),
                _ => VoiceScreen(key: key, state: s),
              };
        return Scaffold(
          appBar: AppBar(
            title: Text(_titles[_tab]),
            actions: [
              IconButton(
                tooltip: 'Settings',
                icon: const Icon(Icons.settings),
                onPressed: _openSettings,
              ),
              IconButton(
                tooltip: 'About',
                icon: const Icon(Icons.info_outline),
                onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(builder: (_) => const AboutScreen())),
              ),
            ],
          ),
          body: body,
          bottomNavigationBar: NavigationBar(
            selectedIndex: _tab,
            onDestinationSelected: (i) => setState(() => _tab = i),
            destinations: const [
              NavigationDestination(icon: Icon(Icons.audio_file), label: 'File'),
              NavigationDestination(icon: Icon(Icons.mic), label: 'Live'),
              NavigationDestination(icon: Icon(Icons.chat), label: 'Chat'),
              NavigationDestination(
                  icon: Icon(Icons.record_voice_over), label: 'Talk'),
            ],
          ),
        );
      },
    );
  }
}
