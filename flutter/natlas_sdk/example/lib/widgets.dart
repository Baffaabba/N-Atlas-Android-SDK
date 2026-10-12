import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';
import 'package:permission_handler/permission_handler.dart';

import 'app_state.dart';

/// Turns any error into one plain sentence that says what to do next.
String describeError(Object error) => switch (error) {
      NAtlasNetwork() =>
        "We couldn't reach the server. Check your internet connection and "
            'the server address in Settings, then try again.',
      NAtlasTimeout() =>
        'The server took too long to answer. It may be busy or asleep. '
            'Try again in a moment.',
      NAtlasUnauthorized() =>
        'The server did not accept the API key. Check the key in Settings.',
      NAtlasUserCapReached() =>
        'This server has reached its limit of 1,000 active users this month. '
            'Try again later.',
      NAtlasRateLimited(:final retryAfterSeconds) => retryAfterSeconds == null
          ? 'Too many requests. Wait a moment and try again.'
          : 'Too many requests. Try again in $retryAfterSeconds seconds.',
      NAtlasModelLoading(:final retryAfterSeconds) =>
        'The server is still starting up. Try again in '
            '${retryAfterSeconds ?? 30} seconds.',
      NAtlasUnsupportedAudio() =>
        "We couldn't read that file. Use a wav, mp3, m4a, ogg, flac or webm "
            'recording.',
      NAtlasAudioTooLarge() => 'That file is too big. Pick one under 25 MB.',
      NAtlasInvalidRequest(:final message) => 'The server refused the request: '
          '$message',
      NAtlasMicPermissionDenied() =>
        'The app needs the microphone. Allow it in your phone settings.',
      NAtlasMicUnavailable() =>
        "We couldn't open the microphone. Close other apps that may be using "
            'it, then try again.',
      NAtlasServerError(:final httpCode) =>
        'The server had a problem (error $httpCode). Try again.',
      _ => 'Something went wrong: $error',
    };

/// Whether the error means "no network", so screens can show an offline
/// state rather than a generic error.
bool isOffline(Object error) => error is NAtlasNetwork;

/// A red card with the error and a retry button.
class ErrorCard extends StatelessWidget {
  const ErrorCard({super.key, required this.error, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      color: scheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(isOffline(error) ? Icons.wifi_off : Icons.error_outline,
                    color: scheme.onErrorContainer),
                const SizedBox(width: 8),
                Text(isOffline(error) ? 'No connection' : 'Something went wrong',
                    style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: scheme.onErrorContainer)),
              ],
            ),
            const SizedBox(height: 8),
            Text(describeError(error),
                style: TextStyle(color: scheme.onErrorContainer)),
            if (onRetry != null) ...[
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                    onPressed: onRetry, child: const Text('Try again')),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Grey centred hint shown before anything has happened.
class EmptyHint extends StatelessWidget {
  const EmptyHint({super.key, required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.outline;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: color),
            const SizedBox(height: 12),
            Text(text, textAlign: TextAlign.center, style: TextStyle(color: color)),
          ],
        ),
      ),
    );
  }
}

/// Shown on every feature screen until a server URL and key are set.
class NotConfigured extends StatelessWidget {
  const NotConfigured({super.key, required this.onOpenSettings});

  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.dns_outlined, size: 48),
            const SizedBox(height: 12),
            const Text(
              'Add your N-ATLAS server address and API key to start.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            FilledButton(
                onPressed: onOpenSettings, child: const Text('Open settings')),
          ],
        ),
      ),
    );
  }
}

/// Dropdown bound to [AppState.language].
class LanguagePicker extends StatelessWidget {
  const LanguagePicker({super.key, required this.state, this.enabled = true});

  final AppState state;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<Language>(
      initialValue: state.language,
      decoration: const InputDecoration(
          labelText: 'Language', border: OutlineInputBorder()),
      items: [
        for (final l in Language.values)
          DropdownMenuItem(value: l, child: Text(languageLabel(l))),
      ],
      onChanged: enabled ? (l) => state.language = l! : null,
    );
  }
}

/// Asks for the microphone with a reason first. Returns true if granted.
///
/// The SDK never asks for permissions itself; this is the app's job.
Future<bool> ensureMicPermission(BuildContext context) async {
  var status = await Permission.microphone.status;
  if (status.isGranted) return true;
  if (!context.mounted) return false;

  if (status.isPermanentlyDenied) {
    final open = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Microphone is off'),
        content: const Text(
            'The app needs the microphone to hear you. Turn it on in the '
            "app's settings."),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(c, false),
              child: const Text('Not now')),
          FilledButton(
              onPressed: () => Navigator.pop(c, true),
              child: const Text('Open settings')),
        ],
      ),
    );
    if (open == true) await openAppSettings();
    return false;
  }

  final ok = await showDialog<bool>(
    context: context,
    builder: (c) => AlertDialog(
      title: const Text('Use the microphone?'),
      content: const Text(
          'The app needs the microphone to hear you. Nothing is recorded '
          'until you tap Start. Your voice is sent to the N-ATLAS server to '
          'be written down, and is not saved on this phone.'),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(c, false),
            child: const Text('Not now')),
        FilledButton(
            onPressed: () => Navigator.pop(c, true),
            child: const Text('Continue')),
      ],
    ),
  );
  if (ok != true) return false;
  status = await Permission.microphone.request();
  return status.isGranted;
}
