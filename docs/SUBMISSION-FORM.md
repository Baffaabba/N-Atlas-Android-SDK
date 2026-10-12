# NAIC portal answers (copy and paste)

Portal: https://ondi.innox.africa/apply/naic/naic-innovation-enterprise
All answers are within the portal character limits (checked 12 Oct).

## Page 2: The Solution

**Problem Statement:** PS1 / Problem Statement 01: Developer Infrastructure

**One-line summary of your build:**
<!-- field:summary -->
An Android SDK (Kotlin and Flutter) that lets any app add Hausa, Igbo, Yoruba and Nigerian-English speech-to-text, live microphone transcription and N-ATLaS chat in a few lines of code.
<!-- /field -->

**Why did you choose this problem statement? (max 500)**
<!-- field:why:500 -->
Most Nigerians reach the internet through Android phones, yet N-ATLAS ships only as raw Hugging Face weights, which an app developer can't use directly. The other SDK efforts we found are Python-first. We wanted a Nigerian-maintained Android SDK so a mobile developer can add Hausa, Igbo, Yoruba or Nigerian-English voice and chat to an app in an afternoon, without learning model serving.
<!-- /field -->

**Describe the artefact you are building (or have built) (max 1000)**
<!-- field:artefact:1000 -->
N-ATLAS SDK for Android, three parts in one open-source repo (Apache-2.0):
1. natlas-core: a Kotlin library. Calls: transcribe an audio file; live microphone transcription with words appearing as you speak; streaming chat with N-ATLaS; and a one-call voice pipeline (mic, then speech-to-text, then N-ATLaS, then the reply). Typed errors, retries, cancellation, and no keys in the app.
2. natlas_sdk: a Flutter plugin that wraps the same Kotlin engine through Pigeon, so Kotlin and Dart behave the same.
3. A reference server (FastAPI) that serves all five NCAIR1 models (N-ATLaS plus the Hausa, Igbo, Yoruba and Nigerian-English ASR models) behind an OpenAI-compatible API, with API keys, rate limits and a counter for the licence's 1,000-user cap. It runs on a free Colab GPU from one notebook.
Sample apps in Kotlin and Flutter, docs and a benchmark script are included.
<!-- /field -->

**Current build status:** Working prototype / MVP (choose the closest option: functional, tested, not yet in production)

**Which N-ATLAS capability does your build use?** Tick: N-ATLAS language model (text), N-ATLAS ASR (speech-to-text). Do not tick N-ATLAS API (no official API was issued to us).

**Nigerian language(s) supported:** Tick all four: Yoruba, Hausa, Igbo, Nigerian-accented English.

**Technology stack / key tools used:**
<!-- field:stack -->
Kotlin (coroutines, Flow, OkHttp, kotlinx.serialization), Android AudioRecord; Flutter/Dart with Pigeon; Python FastAPI + uvicorn, Hugging Face transformers, bitsandbytes 4-bit, Gradio; Google Colab T4 GPU; NCAIR1 models from Hugging Face; JitPack; GitHub.
<!-- /field -->

**Link to public repository, live demo, or deployed application:**
https://github.com/Baffaabba/N-Atlas-Android-SDK

**Working artefact evidence:**
https://github.com/Baffaabba/N-Atlas-Android-SDK/releases/tag/v0.1.0

**Technical documentation (file upload):** `dist/NAtlas-SDK-Technical-Documentation.pdf`

**Video demonstration (3 to 5 minutes):** YouTube (unlisted) or Google Drive link, set to "anyone with the link". Script: `docs/SUBMISSION.md`.

## Page 3: Programme Fit

**Describe exactly how your artefact integrates with N-ATLAS (max 1500)**
<!-- field:integration:1500 -->
The SDK talks only to the official NCAIR1 models from Hugging Face. No other LLM or speech model is used, not even as a fallback.
Server side, our reference server downloads the gated weights with a Hugging Face token and loads them on a GPU: NCAIR1/N-ATLaS (8B, 4-bit NF4, Llama-3 chat template, temperature 0.1 and repetition penalty 1.12 as the model card advises) and the four Whisper-small ASR models NCAIR1/Hausa-ASR, Igbo-ASR, Yoruba-ASR and NigerianAccentedEnglish (float16). Each language code is routed to its own ASR model, and the language token is forced for Hausa, Yoruba and English.
It exposes them as an OpenAI-compatible API: POST /v1/chat/completions (streamed with SSE) for N-ATLaS, POST /v1/audio/transcriptions for files, and a WebSocket /v1/audio/live for live microphone audio with partial and final text. GET /health lists each loaded NCAIR1 model, its device and its quantisation.
Phone side, the Kotlin library (and the Flutter plugin on top of it) records 16 kHz mono audio, streams it to the ASR model, then passes the text to N-ATLaS and streams the reply back, all in one call (voice.converse). Every surface carries the N-ATLAS attribution and the 1,000-user licence cap, and the server counts active users. On 12 Oct 2026 all five models were loaded on a Colab T4 and served real requests (see the evidence file).
<!-- /field -->

**N-ATLAS API key / access reference (if issued):**
Not issued. We use the open NCAIR1 weights on Hugging Face (gated access accepted for all five repos).

**Evidence artefacts demonstrating integration (file upload):** `dist/NAtlas-SDK-Integration-Evidence.pdf`

**How was the artefact tested with real users, real data, or live benchmarks? (max 1500)**
<!-- field:tested:1500 -->
1. Live server with real models: on 12 Oct 2026 we loaded all five NCAIR1 models on a Google Colab Tesla T4 (cold start 952 s; 7.6 GB of 15 GB GPU memory) and called it over the internet from a laptop in Nigeria, through the same endpoints the Kotlin and Flutter SDK use.
2. Real speech data with human transcripts: a benchmark script (tools/benchmark in the repo) downloads public labelled speech, Google FLEURS (Hausa, Igbo, Yoruba) and AfriSpeech-200 (Nigerian-accented English), sends each clip to the server, and scores word error rate and request time. Anyone can rerun it against any server.
3. Hands-on use by the team in the Gradio playground. This found that Hausa transcription of short mic clips was poor until we forced Whisper's language token, which we then made the default.
4. Automated tests: 70 server tests (FastAPI, auth, user cap, SSE, WebSocket live sessions, audio splitting), Kotlin unit tests for natlas-core, and Flutter plugin tests.
We wrote a guide for external developer testers (docs/validation/TESTER-GUIDE.md). No external feedback was in by the deadline, and the repo says so.
<!-- /field -->

**Validation evidence (file upload):** `dist/NAtlas-SDK-Validation-Evidence.pdf`

**Key results or feedback from validation (max 1500)**
<!-- field:results:1500 -->
- All five NCAIR1 models served from one endpoint on a single free T4 GPU, using 7.6 GB of 15 GB.
- Hausa speech-to-text on real FLEURS speech: 7 of 7 requests succeeded. Median request time 9.45 s for a median clip of 19.3 s, network from Nigeria included, so a clip transcribes in about half its own length. Median word error rate 0.35; most errors were foreign names (Aristarchus, NHK), and the model writes hooked letters such as ƙ that the references leave out.
- One real bug found: the one clip over 30 s, split into two windows, made the model repeat text. Fix planned: a shorter split and a repetition guard.
- Forcing the Whisper language token fixed poor Hausa results on short mic clips found in hands-on testing; it is now the default.
- The Colab session ended partway through the benchmark, so Igbo, Yoruba, English, chat and live-mic timings were not measured in that run. We report what we measured and nothing else; the script reruns the whole set.
- Lesson: free Colab is fine for development but not for hosting. A stable hosted endpoint is our first post-challenge task.
<!-- /field -->

**Who benefits from this build, and how many people could it reach? (max 1500)**
<!-- field:benefit:1500 -->
Direct users are Nigerian mobile developers, startups and agencies who build Android apps in Kotlin or Flutter. Android runs on most smartphones in Nigeria, so it is where local-language voice features reach people.
Through their apps, the people who benefit are speakers of Hausa, Yoruba and Igbo (each language has tens of millions of speakers) and Nigerians who speak English with a local accent. Many are more at ease speaking than typing, or speaking their own language than English. Examples: farmers asking about crops by voice in Hausa, health and government-service apps that take spoken Yoruba or Igbo, fintech and customer-support apps that accept voice notes, and literacy and education apps.
Reach: each app built on the SDK serves up to 1,000 active users a month under the free N-ATLAS licence, with Awarri's commercial licence beyond that. Ten partner apps would mean about 10,000 monthly users on the free tier alone, and much more with commercial licences. Because the protocol is OpenAI-compatible, the same apps can switch to an official N-ATLAS API by changing the server address.
<!-- /field -->

**What is your plan to sustain or scale the build beyond the challenge? (max 1500)**
<!-- field:sustain:1500 -->
1. Hosting: move the reference server from Colab to a stable GPU host (Modal, a cloud GPU VM or NCAIR compute credits) with a fixed URL, keep-warm hours and spending limits. If NCAIR publishes an official N-ATLAS API, point the SDK at it with a config change, since our protocol is OpenAI-compatible.
2. Distribution: publish natlas-core to Maven Central and the Flutter plugin to pub.dev (both on JitPack and GitHub today), with semantic versions and a changelog.
3. Product: add text-to-speech when an N-ATLAS TTS model exists (the pipeline already has a slot for it), offline speech-to-text for low-end phones, and an iOS client.
4. Licence compliance: the server already counts active users against the 1,000-user cap. We will help apps that grow past it get Awarri's commercial licence.
5. Revenue: the SDK stays free and open source. Income would come from a managed hosted endpoint for teams that don't want to run GPUs, and from integration support for companies.
6. Community: run developer workshops, gather feedback from Nigerian app developers, and accept contributions on GitHub.
<!-- /field -->

**Are you seeking support for this build? (max 1500)**
<!-- field:support:1500 -->
Yes. We need GPU compute to host N-ATLaS and the four ASR models full time (today we use free Colab sessions, which end), access to the official N-ATLAS API if one exists, and introductions to Nigerian app teams in agriculture, health, fintech and education who want local-language voice features. We have no funding or institutional partnership for this build yet.
<!-- /field -->

**What support would help your team most?** Tick: Compute credits, Technical mentorship, Investor or partner introductions.

**How did you hear about the NAIC?** Pick what is true for you.

**Confirmations:** Tick Yes on all four (original work, accurate information, terms and conditions, publicity), after reading them.
