# N-ATLAS integration evidence

NAIC component 2. This file shows which N-ATLAS model serves each SDK call,
where that happens in the code, and real requests and responses with
measured timings.

> **Filled from a real run, 12 Oct 2026.**

N-ATLAS is an initiative of the Federal Ministry of Communications,
Innovation and Digital Economy, and powered by Awarri Technologies.

---

## 1. Which model serves which call

Only `NCAIR1/*` models are served. No other model is used, not even as a
fallback. An unknown model or language is rejected with a 400.

| SDK call (Kotlin / Dart) | Endpoint | N-ATLAS model |
| --- | --- | --- |
| `client.chat.complete(...)` / same | `POST /v1/chat/completions` | `NCAIR1/N-ATLaS` |
| `client.chat.stream(...)` / same | `POST /v1/chat/completions` with `"stream": true` (SSE) | `NCAIR1/N-ATLaS` |
| `client.asr.transcribe(file, HAUSA)` / `Language.hausa` | `POST /v1/audio/transcriptions`, `language=ha` | `NCAIR1/Hausa-ASR` |
| `client.asr.transcribe(file, IGBO)` / `Language.igbo` | `POST /v1/audio/transcriptions`, `language=ig` | `NCAIR1/Igbo-ASR` |
| `client.asr.transcribe(file, YORUBA)` / `Language.yoruba` | `POST /v1/audio/transcriptions`, `language=yo` | `NCAIR1/Yoruba-ASR` |
| `client.asr.transcribe(file, ENGLISH)` / `Language.english` | `POST /v1/audio/transcriptions`, `language=en` | `NCAIR1/NigerianAccentedEnglish` |
| `client.asr.live(language)` / same | `WS /v1/audio/live?language=<code>` | The ASR model for that language |
| `client.voice.converse(language)` / same | `WS /v1/audio/live`, then `POST /v1/chat/completions` (stream) | ASR model for the language, then `NCAIR1/N-ATLaS` |
| (health check) | `GET /health` | Reports every loaded model and its quantisation |

Source of truth: `docs/API.md`.

---

## 2. Where it happens in the code

| Concern | Path |
| --- | --- |
| Model ids, language to ASR model routing, attribution text | `server/natlas_server/registry.py` (`LLM_ID = "NCAIR1/N-ATLaS"`, `resolve_asr`) |
| N-ATLaS loading (4-bit NF4 via bitsandbytes on a T4), Llama-3 chat template, temperature 0.1, repetition penalty 1.12, token streaming | `server/natlas_server/backends/transformers_llm.py` |
| The four Whisper-small ASR models (float16), forced language token | `server/natlas_server/backends/transformers_asr.py` |
| Download only the files needed from the gated repos (never `optimizer.pt`) | `server/natlas_server/backends/hf_download.py` |
| HTTP endpoints, auth, 1,000-user counter | `server/natlas_server/app.py`, `server/natlas_server/auth.py` |
| Live mic: voice activity detection, partial and final text | `server/natlas_server/vad.py`, `server/natlas_server/live.py` |
| Colab notebook (loads all five models, opens a public link) | `server/colab/natlas_server_colab.ipynb` |
| Kotlin `Language` enum and model ids | `android/natlas-core/src/main/java/io/github/baffaabba/natlas/Language.kt` |
| Kotlin chat with SSE parsing | `.../natlas/ChatApi.kt`, `.../natlas/internal/Sse.kt` |
| Kotlin file transcription (multipart upload) | `.../natlas/AsrApi.kt` |
| Kotlin live mic (AudioRecord to WebSocket) | `.../natlas/LiveTranscription.kt`, `.../natlas/internal/AudioSource.kt` |
| Kotlin voice pipeline (mic, ASR, N-ATLaS) | `.../natlas/VoiceApi.kt` |
| Attribution constant | `NAtlasClient.ATTRIBUTION` in `.../natlas/NAtlasClient.kt` |
| Flutter Pigeon bridge over the Kotlin core | `flutter/natlas_sdk/pigeons/natlas_api.dart`, `flutter/natlas_sdk/lib/src/client.dart` |

---

## 3. Real requests and responses

Run on 12 Oct 2026 against the server on a Google Colab Tesla T4,
reached from a laptop in Nigeria. Key and URL hidden.

### 3.1 `GET /health`: all five NCAIR1 models loaded

```json
{"status":"ok","version":"0.1.0",
 "models":{
  "NCAIR1/N-ATLaS":{"loaded":true,"state":"loaded","backend":"transformers","quantization":"4bit-nf4","device":"Tesla T4","max_context":8192,"load_seconds":887.8},
  "NCAIR1/Hausa-ASR":{"loaded":true,"backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":"hausa","load_seconds":35.2},
  "NCAIR1/Igbo-ASR":{"loaded":true,"backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":null,"load_seconds":20.8},
  "NCAIR1/Yoruba-ASR":{"loaded":true,"backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":"yoruba","load_seconds":9.1},
  "NCAIR1/NigerianAccentedEnglish":{"loaded":true,"backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":"english","load_seconds":9.7}},
 "users":{"active_30d":2,"cap":1000},
 "attribution":"N-ATLAS is an initiative of the Federal Ministry of Communications, Innovation and Digital Economy, and powered by Awarri Technologies."}
```

Server log from the same session (Colab):

```
[  952s] ok: {'N-ATLaS': 'loaded', 'Hausa-ASR': 'loaded', 'Igbo-ASR': 'loaded', 'Yoruba-ASR': 'loaded', 'NigerianAccentedEnglish': 'loaded'}
Load time (this run): 952 s
memory.used [MiB], memory.total [MiB]
7617 MiB, 15360 MiB
SDK BASE URL: https://<id>.gradio.live/natlas
Android : NAtlasConfig(baseUrl = "https://<id>.gradio.live/natlas", apiKey = ...)
Flutter : --dart-define=NATLAS_BASE_URL=https://<id>.gradio.live/natlas --dart-define=NATLAS_API_KEY=...
```

### 3.2 Transcription: Hausa, real speech

```
curl -H "Authorization: Bearer $KEY" -F language=ha -F file=@ha_fleurs_dev_2.wav      https://<server>/natlas/v1/audio/transcriptions
{"text": "amazon kuma shine kogi mafi girma a duniya, a wasu lokutan nisan mil s…",
 "language": "ha", "model": "NCAIR1/Hausa-ASR", "duration": 14.5}
```

Reference (FLEURS, written by a person): "Amazon kuma shine kogi mafi fadi a
Duniya, a wasu lokutan nisan mil shida." Seven Hausa clips with word error
rates and timings: `docs/validation/RESULTS.md`.

### 3.3 Chat and live mic

Request and event shapes: `docs/API.md` (SSE chunks ending in `[DONE]`;
live frames `ready`, `partial`, `final`, `done`). The benchmark script
`tools/benchmark/run_benchmark.py` measures both. The Colab session ended
before those parts of the run on 12 Oct, so we give no numbers for them.

---

## 4. Measured numbers

From `docs/validation/RESULTS.md` (Colab T4, client on home broadband in
Nigeria, 12 Oct 2026):

| Measure | Runs | Median | Worst |
| --- | --- | --- | --- |
| `/health` request | 1 | 2.56 s | n/a |
| Hausa transcription, full request (clips 8 to 33 s) | 7 | 9.45 s | 22.55 s |
| Hausa word error rate (FLEURS dev) | 7 | 0.35 | 5.64 (repetition on the one clip over 30 s) |
| Cold start, all five models | 1 | 952 s | n/a |

Success rate: 7 of 7 transcription requests while the server was up.
