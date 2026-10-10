---
name: natlas-context
description: Ground truth for the N-ATLAS Android SDK repo — what it is, the NAIC 2026 deadline, the verified facts about the N-ATLaS models (repos, sizes, licence, gated access), settled and open decisions, the planned module layout and the hard rules (licence, attribution, user cap, no secrets in apps). Load before any product, Android, Flutter, backend, QA or review work in this repo.
---

# N-ATLAS SDK: project ground truth

An Android SDK that lets app developers use the **N-ATLAS** models from
Kotlin and Flutter: chat with the N-ATLaS LLM, and transcribe speech in
Hausa, Igbo, Yoruba and Nigerian-accented English. It is a **cloud client**:
the phone sends text or audio to a server and gets results back.

Entered (planned) in **NAIC 2026**, Problem Statement 01, Developer
Infrastructure. Rules: the `naic-rubric` skill. Decisions log:
`docs/DECISIONS.md`, which beats anything written here.

## Deadline

NAIC submission closes **Mon 12 Oct 2026, 11:59 PM WAT**. Late entries are
not accepted. Integration check (judges may run it): 15–17 Oct.
Every scope question is answered against this date.

## The N-ATLaS models (verified on Hugging Face, 2026-10-10)

All five repos sit under the `NCAIR1` org. They were created 2025-09-19 and
have not changed since 2025-09-30. All are **gated**: a Hugging Face account
must accept the terms, and downloads need `HF_TOKEN`.

| Repo | What | Size |
| --- | --- | --- |
| `NCAIR1/N-ATLaS` | LLM. `LlamaForCausalLM`, 8.03B params, almost certainly Llama-3.1-8B base. BF16 safetensors | ~16 GB |
| `NCAIR1/Hausa-ASR` | Whisper-small fine-tune (~244M) | 967 MB fp32 `.bin` |
| `NCAIR1/Igbo-ASR` | Whisper-small fine-tune | ~967 MB |
| `NCAIR1/Yoruba-ASR` | Whisper-small fine-tune | ~967 MB |
| `NCAIR1/NigerianAccentedEnglish` | Whisper-small fine-tune | ~967 MB |

- LLM languages: English, Hausa, Igbo, Yoruba (Yoruba weakest: human eval 2.69/5
  against 4.21 for English). **No Pidgin support. No TTS model exists.**
- LLM chat template: standard Llama-3 (`<|start_header_id|>`, `<|eot_id|>`).
  Recommended sampling: temperature 0.1, repetition_penalty 1.12. Works best
  within ~8k tokens.
- ASR: 16 kHz mono, 30-second windows. No official WER published.
  Repos include a 1.9 GB `optimizer.pt`; never download the whole repo.
- Official formats: safetensors / PyTorch only. Unofficial GGUFs exist:
  `QuantFactory/N-ATLaS-GGUF` (Q4_K_M 4.92 GB, Q8_0 8.54 GB, others).
- **No official hosted API, GitHub repo or SDK exists** (as of 2026-10-10).

## Licence (affects every public surface)

"Open-Source Research and Innovation License" (HF `license:other`):

- Attribution required. Every README, sample app "About" screen, docs page
  and the video carries: *"N-ATLAS is an initiative of the Federal Ministry
  of Communications, Innovation and Digital Economy, and powered by Awarri
  Technologies."* (expose it in code as `NAtlas.ATTRIBUTION`).
- **Free use is capped at 1,000 active end users in a rolling 30 days.**
  Past that, a commercial licence from Awarri (datasupport@awarri.com).
- Banned: surveillance, profiling, disinformation, military use.
- Derivatives keep the licence; renamed models say "Powered by Awarri".
- Probably also the Llama 3.1 Community Licence ("Built with Llama"). Unconfirmed.

## Settled decisions (Baffa, 2026-10-10)

- **Cloud client only.** No on-device inference in v1. (8B is ~6 GB resident
  even at Q4; only 12 GB phones could run it.)
- **Kotlin core + Flutter wrapper.** One Android library in Kotlin does the
  real work. The Flutter plugin wraps it through Pigeon. One engine, two APIs.
- **v1 scope:** speech-to-text (file and live mic), LLM chat (streaming), and
  a voice pipeline (mic → ASR → N-ATLaS → text reply).
- **TTS deferred.** Not in v1. Design the pipeline so a TTS stage can slot in.
- **Lean agent team:** pm, android, flutter, backend, qa, critic.
- **This project stands alone.** No link to any other repo or entry.
- **Test server runs on Google Colab for now.** No public official or stable
  third-party API exists (`docs/research/HOSTING.md`). A Colab notebook loads
  the NCAIR models on a GPU and exposes them through a tunnel URL. The URL
  changes every session and Colab sessions end, so the SDK's `baseUrl` is
  always configurable. Production hosting for judges is decided later.
- **Our edge, in one sentence:** Android-native, Flutter, live microphone
  input and a one-call voice pipeline on a real phone. Other entries
  (N-ATLAS-Forge, `natlas` on PyPI) are Python-first SDK clients.

## Open questions

1. **Wire protocol (recommended, not yet confirmed):** OpenAI-compatible:
   `/v1/chat/completions` with SSE, `/v1/audio/transcriptions` with a
   `language` field mapped to the right NCAIR ASR model, so an official
   endpoint later is a config change.
2. **Hosting for the judges' integration check (15–17 Oct).** Colab sessions
   sleep and time out, so they can't host it.

## Planned layout (proposed; verify before citing)

```
android/                     Gradle project
  natlas-core/               Kotlin library: client, ASR, chat, pipeline
  sample/                    Kotlin sample app
flutter/natlas_sdk/          Flutter plugin (Pigeon bridge to natlas-core)
  example/                   Flutter sample app
server/                      Reference server, if we build one
docs/                        DECISIONS.md, API.md, N-ATLAS-INTEGRATION.md
```

Public Kotlin surface (draft, to be fixed in `docs/API.md`):

- `NAtlasClient(config)` with `baseUrl`, `apiKey` or token provider, timeouts.
- `client.asr.transcribe(audio, language) : Transcript`
- `client.asr.stream(language) : Flow<TranscriptEvent>` (live mic)
- `client.chat.complete(messages, options) : ChatResult`
- `client.chat.stream(messages, options) : Flow<ChatDelta>`
- `client.voice.pipeline(language) : Flow<VoiceEvent>`
- `Language` = `HAUSA`, `IGBO`, `YORUBA`, `ENGLISH_NG`.

## Hard rules

1. **Directly integrate N-ATLAS.** NAIC disqualifies wrappers of other
   general-purpose models. The SDK talks to `NCAIR1/*` models, never to a
   GPT/Gemini fallback.
2. **No secrets in apps.** No HF token or server master key in an APK or
   Flutter asset. Apps get short-lived or per-app keys from the developer's
   backend. Samples read the key from `local.properties` / `--dart-define`,
   both git-ignored.
3. **Attribution and licence** on every public surface (see above). The
   README explains the 1,000-user cap in one plain paragraph.
4. **Microphone and privacy.** Ask for `RECORD_AUDIO` at the moment it is
   needed, with a reason. Never record in the background. Audio is not stored
   on the device or logged by the SDK. Banned uses are listed in the README.
5. **Honest claims.** No "fast", "accurate" or latency claim without a
   measured number, the device and the network it came from.
6. **Never commit tokens, keys, `.env`, `local.properties` or audio.**

## Environment

- Dev machine: Windows 11, no usable GPU. Shells: PowerShell and Git Bash.
- Android: Gradle wrapper, minSdk 24 (proposed), Kotlin coroutines + Flow,
  OkHttp, kotlinx.serialization. Flutter: stable channel, Pigeon.
- Check what is installed (`java -version`, `flutter doctor`, Android SDK
  path) before assuming. Record findings in agent memory.

## Git

Never commit to `main` or `dev`. Work on feature branches. Ask Baffa before
every commit.
