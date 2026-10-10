# N-ATLAS integration evidence

NAIC component 2. This file shows which N-ATLAS model serves each SDK call,
where that happens in the code, and real requests and responses with
measured timings.

> **Skeleton, 2026-10-10.** Everything marked TODO is filled by the engineer
> who built that part, from a real run. No invented examples, no estimated
> numbers.

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

| Concern | Path | Notes |
| --- | --- | --- |
| Model loading (LLM) | TODO (backend): `server/...` | Quantisation used: TODO |
| Model loading (four ASR models) | TODO (backend): `server/...` | Which files are downloaded (never `optimizer.pt`): TODO |
| Language → ASR model routing | TODO (backend): `server/...` | |
| Llama-3 chat template, temperature 0.1, repetition penalty 1.12 | TODO (backend): `server/...` | |
| Live mic: VAD, partial and final | TODO (backend): `server/...` | |
| Colab notebook | TODO (backend): `server/...ipynb` | |
| Kotlin `Language` enum and model ids | TODO (android): `android/natlas-core/src/main/...` | |
| Kotlin chat (SSE parsing) | TODO (android) | |
| Kotlin transcription (multipart upload) | TODO (android) | |
| Kotlin live mic (AudioRecord → WebSocket) | TODO (android) | |
| Kotlin voice pipeline | TODO (android) | |
| Flutter Pigeon bridge | TODO (flutter): `flutter/natlas_sdk/...` | |
| Attribution constant | TODO (android): `NAtlasClient.ATTRIBUTION` | |

---

## 3. Real requests and responses

Paste from a real run against the Colab server. Hide the key and the server
URL. Note the date, the server version from `/health`, and the clip used.

### 3.1 `GET /health`

```
TODO (backend): real response
```

### 3.2 Chat, streaming

```
TODO (backend): curl command and the first few SSE events, plus [DONE]
```

### 3.3 Transcription, one per language

Describe each clip (speaker, length, conditions). Audio files are not
committed.

| Language | Clip | Response JSON | Correct text (by a speaker of the language) |
| --- | --- | --- | --- |
| Hausa | TODO | TODO | TODO |
| Igbo | TODO | TODO | TODO |
| Yoruba | TODO | TODO | TODO |
| Nigerian English | TODO | TODO | TODO |

### 3.4 Live mic (WebSocket)

```
TODO (backend/android): frames from one session: ready, partial..., final, done
```

### 3.5 Voice pipeline on a phone

TODO (android/qa): screenshot path in `docs/validation/measurements/` and the
event sequence from the log (Listening, Heard, Thinking, Reply, Done).

---

## 4. Measured latency

Every number gives the device, network, number of runs and the median.
Leave a row as TODO rather than guess.

Server: TODO (Colab GPU type, quantisation). Date: TODO.

| Measure | Device and network | Runs | Median | Worst |
| --- | --- | --- | --- | --- |
| Chat: time to first word | TODO | TODO | TODO | TODO |
| Chat: full reply (512 tokens max) | TODO | TODO | TODO | TODO |
| Transcribe a 10 s clip (file) | TODO | TODO | TODO | TODO |
| Live mic: speech end to final text | TODO | TODO | TODO | TODO |
| Voice pipeline: speech end to first reply word | TODO | TODO | TODO | TODO |

Success rate over the runs above: TODO.
