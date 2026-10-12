import 'package:flutter/material.dart';
import 'package:natlas_sdk/natlas_sdk.dart';

/// Licence attribution, the user cap and banned uses.
class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('About')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('N-ATLAS SDK example', style: t.headlineSmall),
          const SizedBox(height: 8),
          const Text(
            'This app shows the natlas_sdk Flutter plugin. It writes down '
            'speech in Hausa, Igbo, Yoruba and Nigerian English, and chats '
            'with the N-ATLaS model. Replies come back as text: N-ATLAS has '
            'no text-to-speech model yet.',
          ),
          const SizedBox(height: 16),
          Text('Attribution', style: t.titleMedium),
          const SizedBox(height: 4),
          const SelectableText(NAtlasClient.attribution),
          const SizedBox(height: 16),
          Text('Licence', style: t.titleMedium),
          const SizedBox(height: 4),
          const Text(
            'The N-ATLAS models use the Open-Source Research and Innovation '
            'License. Free use stops at 1,000 active users in any 30 days. '
            'Past that, an app needs a commercial licence from Awarri '
            '(datasupport@awarri.com).',
          ),
          const SizedBox(height: 8),
          const Text(
            'The licence bans use for surveillance, profiling, '
            'disinformation and military purposes.',
          ),
          const SizedBox(height: 16),
          Text('Privacy', style: t.titleMedium),
          const SizedBox(height: 4),
          const Text(
            'The microphone is used only while you are recording. Audio is '
            'sent to the N-ATLAS server you set in Settings and is not saved '
            'on this phone.',
          ),
          const SizedBox(height: 16),
          Text('Letter check', style: t.titleMedium),
          const SizedBox(height: 4),
          const Text(
            'Each line below should show every letter clearly, with no boxes.',
          ),
          const SizedBox(height: 8),
          Text('Hausa: ɓ ɗ ƙ ʼy  Ɓ Ɗ Ƙ  ƙasa, ɗaya, ɓera', style: t.titleMedium),
          Text('Yoruba: ẹ ọ ṣ  Ẹ Ọ Ṣ  à á è é ì í ò ó ù ú  Ẹ kú àárọ̀',
              style: t.titleMedium),
          Text('Igbo: ị ụ ṅ ọ  Ị Ụ Ṅ Ọ  Ụtụtụ ọma', style: t.titleMedium),
          const SizedBox(height: 16),
          Text('Font: Noto Sans (SIL Open Font License 1.1).', style: t.bodySmall),
        ],
      ),
    );
  }
}
