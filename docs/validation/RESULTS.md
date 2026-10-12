# Validation results (12 Oct 2026)

What we tested, with what data, and what happened. Nothing here is
simulated. Where a test did not finish, this page says so.

## Set-up

| Item | Value |
| --- | --- |
| Server | `server/` (this repo) on Google Colab, NVIDIA Tesla T4 (15 GB), reached through a Gradio share link at `/natlas` |
| Models | All five `NCAIR1/*` models from Hugging Face. N-ATLaS in 4-bit NF4 (bitsandbytes); the four Whisper-small ASR models in float16 |
| Model load time | 952 s for all five on a cold Colab VM (N-ATLaS alone: 888 s, most of it the 16 GB download) |
| GPU memory in use | 7,617 MiB of 15,360 MiB with all five models loaded |
| Client | Windows 11 laptop on home broadband in Nigeria, calling the same HTTP endpoints the Kotlin and Flutter SDK call |
| Benchmark script | `tools/benchmark/run_benchmark.py` (clips fetched by `tools/benchmark/fetch_samples.py`) |

## Real speech data

We did not record our own test set. We used public speech with transcripts
written by people, so anyone can repeat the test:

| Language | Source | Licence | Clips fetched |
| --- | --- | --- | --- |
| Hausa | Google FLEURS `ha_ng`, dev split | CC-BY-4.0 | 10 |
| Igbo | Google FLEURS `ig_ng`, dev split | CC-BY-4.0 | 10 |
| Yoruba | Google FLEURS `yo_ng`, dev split | CC-BY-4.0 | 10 |
| Nigerian-accented English | AfriSpeech-200, dev split, Hausa-, Yoruba- and Igbo-accented Nigerian speakers | CC-BY-NC-SA-4.0 | 9 |

The audio is not committed to the repo (`*.wav` is git-ignored). The fetch
script downloads the same clips again.

## Result 1: all five N-ATLAS models served from one endpoint

`GET /health` from the live server (12 Oct 2026):

```json
{"status":"ok","version":"0.1.0",
 "models":{
  "NCAIR1/N-ATLaS":{"loaded":true,"state":"loaded","backend":"transformers","quantization":"4bit-nf4","device":"Tesla T4","max_context":8192,"load_seconds":887.8},
  "NCAIR1/Hausa-ASR":{"loaded":true,"state":"loaded","backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":"hausa","load_seconds":35.2},
  "NCAIR1/Igbo-ASR":{"loaded":true,"state":"loaded","backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":null,"load_seconds":20.8},
  "NCAIR1/Yoruba-ASR":{"loaded":true,"state":"loaded","backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":"yoruba","load_seconds":9.1},
  "NCAIR1/NigerianAccentedEnglish":{"loaded":true,"state":"loaded","backend":"transformers","device":"Tesla T4","dtype":"float16","forced_language":"english","load_seconds":9.7}},
 "users":{"active_30d":2,"cap":1000},
 "attribution":"N-ATLAS is an initiative of the Federal Ministry of Communications, Innovation and Digital Economy, and powered by Awarri Technologies."}
```

Request time from Nigeria: 2.56 s.

## Result 2: Hausa transcription on real speech (NCAIR1/Hausa-ASR)

`POST /v1/audio/transcriptions` with `language=ha`. Word error rate (WER)
after lower-casing and removing punctuation. Time is the full request from
the laptop in Nigeria: upload, the Gradio relay, transcription on the T4,
and the reply.

| # | Clip length | Request time | WER | Reference (FLEURS) | N-ATLAS output (first 70 characters) |
| --- | --- | --- | --- | --- | --- |
| 1 | 19.3 s | 9.45 s | 0.35 | Dubunnan shekarun da suka gabata, wani mutum da ake kira Aristarchus yace Tsarin Rana ya zagaya Rana. | dubbin nan shekarun da suka gabata, wani mutum da ake kira iristacis, … |
| 2 | 14.5 s | 7.33 s | 0.07 | Amazon kuma shine kogi mafi fadi a Duniya, a wasu lokutan nisan mil shida. | amazon kuma shine kogi mafi girma a duniya, a wasu lokutan nisan mil s… |
| 3 | 8.0 s | 4.32 s | 0.50 | A farkon lamari, kabilar Byzantine da ke gabashi ta karfafa saka tufafi. | a farkon lamarin abinar bai zantan da ke gaba shi ta karfafa, saka tuf… |
| 4 | 22.5 s | 10.33 s | 0.12 | A cikin wannan na'urar sufuri mai sauyawa kowa na da alaka da juna … | a cikin wannan na'urar sufuri mai sauyawa, kowa na da alaƙa da juna ta… |
| 5 | 13.1 s | 6.93 s | 0.80 | NHK ta kuma ruwaito cewa masana'antar samar da makamashin nukiliya ta Kashiwazaki Kariwa … | an iske ta kuma ruwaito cewa masu na'antar sumar da maƙamashin nukamiy… |
| 6 | 32.6 s | 22.55 s | 5.64 | Ta kai ga wannan matsayin ne saboda yawan tofa albarkatun bakin mutane … | ta kai ga wannan matsayi ne saboda yawan tofa albarkatun bakin mutane … (then repeats) |
| 7 | 28.0 s | 16.47 s | 0.14 | Kasancewa a kasar waje a karo na farko, na sa matafiya yin hakuri … | kasan cewar a ƙasar waje a karo na farko na sa matafiya yin hakuri da … |

- **7 of 7 requests succeeded.** Median request time 9.45 s for a median clip
  of 19.3 s, so a clip transcribes in about half its own length, network
  included. An earlier pass over clips 1–3 took 4.49 s, 2.83 s and 2.22 s;
  the relay's speed varies.
- **Median WER 0.35.** Everyday words come out right. Most errors are
  foreign names (Aristarchus, Byzantine, NHK, Kashiwazaki), which FLEURS
  is full of. The model also writes hooked letters (ƙ) that the FLEURS
  references leave out, and that counts as an error here.
- **Clip 6 (32.6 s) shows a real bug.** It is the only clip over 30 s. The
  server split it into two windows, and the model repeated text in the
  second one (WER 5.64). This is a known Whisper failure. Fix planned:
  split long audio at a shorter limit and stop repeated n-grams. We report
  it rather than drop the clip.

## What did not finish

The Colab session ended (the share link started returning 404) at clip 8
of 39. Igbo, Yoruba and English transcription, the streaming-chat timing
and the live-mic timing were therefore **not measured in this run**. Run
`tools/benchmark/run_benchmark.py` against any running server to repeat
the full test; it writes one JSON report.

Before that, the team tried the same server by hand from the Gradio
playground on 11–12 Oct. One finding from those checks: Hausa
transcription of short microphone clips was poor until the server forced
Whisper's language token (`docs/DECISIONS.md`, 11 Oct). We saved no
numbers from those manual checks, so we give none.

## Automated tests

| Suite | Result | Command |
| --- | --- | --- |
| Server (FastAPI, fake and real code paths) | 68 passed (+2 Gradio-mount tests, which pass on their own: 4 passed) | `cd server && .venv/Scripts/python -m pytest -q` |
| Kotlin `natlas-core` unit tests | 69 per variant, 0 failures, 2 skipped (the live-server integration tests; no server was up) | `cd android && ./gradlew :natlas-core:test` |
| Flutter plugin and example | Plugin 50 passed, example 6 passed; `flutter analyze` clean | `cd flutter/natlas_sdk && flutter test` |

## External testers

The problem statement asks for at least two external developer testers. The
tester guide is ready (`TESTER-GUIDE.md`), but no external feedback was in
when we submitted. We say so rather than invent it.
