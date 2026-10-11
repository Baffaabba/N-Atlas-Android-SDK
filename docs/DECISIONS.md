# Decisions

Baffa's calls on this project, newest first. Every N-ATLAS SDK agent reads
this before it starts, and adds to it when Baffa decides something or
overrules one of them.

A decision here beats a default in an agent file, a skill, or a plan. If two
entries disagree, the newer one wins. Say so in the new entry rather than
editing the old one, so the history of a change of mind survives.

## How to add one

One entry per decision, under today's date:

- **The decision**, in one sentence.
  *Why:* what Baffa said, or what made it necessary.
  *Applies to:* who has to act differently because of it.

Write down only what Baffa decided or confirmed. Your own preference, or a
choice you made because nobody asked, is not a decision. When unsure whether
something counts, ask.

Also record it when Baffa overrules you. "Agent proposed X, Baffa chose Y
because Z" is the most useful kind of entry, because it is the one most likely
to be proposed again.

---

## 2026-10-11

- **The Colab notebook defaults to a Gradio share link (`MODE = "gradio"`):
  a browser playground plus the SDK API, mounted at `/natlas`, on one
  `*.gradio.live` URL. Cloudflared/ngrok stays available as `MODE = "tunnel"`.**
  *Why:* Baffa asked for "colab to use gradio or streamlit" and said the
  notebook must fully support Colab so other team members can test. Gradio
  over Streamlit: Gradio gives a public share link from Colab with no
  account, and its server is FastAPI, so the existing API mounts on the same
  link. Streamlit would need a separate tunnel and can't host the API.
  *Applies to:* backend, android/flutter (Colab `baseUrl` is
  `https://<id>.gradio.live/natlas`), pm (tester guide). Open risk: SSE and
  WebSocket through the Gradio share relay haven't been verified (frpc is
  blocked on Baffa's laptop). The notebook's smoke cells check both.

- **Team-member support in the notebook:** `GITHUB_TOKEN` secret for a
  private repo, automatic zip-upload fallback, a GPU check, and a per-token
  check of access to each gated NCAIR1 repo.
  *Why:* same request from Baffa.
  *Applies to:* backend.


- **Flutter bridge: Pigeon 29 `@HostApi` for calls plus one `@EventChannelApi`
  (`sessionEvents`, a sealed `SessionEventMsg`) for every stream, keyed by
  session id through Pigeon's `instanceName`.** Made by natlas-flutter under
  the delegation. *Why:* Pigeon 29 supports event channels and sealed event
  classes for Kotlin and Dart, so no hand-written channel code. A `start*`
  call registers the session's channel and returns its id; Kotlin collects
  the flow only while Dart listens, and Dart cancelling the subscription
  (`onCancel`) cancels the coroutine. *Applies to:* flutter, android (the
  plugin's Kotlin only converts types; no logic).

- **Dart exception subclasses are named `NAtlas` + the Kotlin name
  (`NAtlasException.Timeout` → `NAtlasTimeout`, `.Unauthorized` →
  `NAtlasUnauthorized`).** Made by natlas-flutter under the delegation.
  *Why:* plain Kotlin names clashed: `Timeout` is also exported by
  `flutter_test` (it broke `flutter analyze` in our own tests), and
  `TimeoutException` is in `dart:async`. Same rule API.md already uses for
  events (`LiveEvent.Ready` → `LiveReady`). Channel error codes stay the
  Kotlin simple names. *Applies to:* flutter; API.md Part 3.

- **The Flutter example builds natlas-core from source with a Gradle
  composite build (`includeBuild("../../../../android")` + dependency
  substitution for `io.github.baffaabba:natlas-core`), and pins AGP 8.13.2,
  Kotlin 2.2.21, Gradle 8.14.3 like `android/`.** Made by natlas-flutter
  under the delegation. *Why:* no publish step, and one AGP version in the
  composite build. Flutter 3.47.7's template defaults to AGP 9.1 / Kotlin
  2.4 / Gradle 9.3.1, but its Gradle plugin accepts AGP ≥ 8.11.1 and Kotlin
  ≥ 2.2.20 (warnings only). The plugin's Gradle file also lists
  `mavenLocal()` for apps outside this repo (`publishToMavenLocal`).
  *Applies to:* flutter, android (keep the two version lists in step), qa.

- **`natlas_sdk` is `publish_to: none` until natlas-core is on Maven
  Central; the example bundles Noto Sans (OFL) instead of downloading
  fonts.** Made by natlas-flutter under the delegation. *Why:* a pub.dev
  package that can't resolve its Android dependency would fail on install;
  bundled fonts render ɓ ɗ ƙ, ẹ ọ ṣ, ị ụ ṅ offline (coverage checked in
  the font's cmap). *Applies to:* flutter, pm (release steps).

- **Test server stack: FastAPI + uvicorn with Hugging Face transformers
  (N-ATLaS in 4-bit NF4 via bitsandbytes, the four ASR models through the
  transformers pipeline), behind a small backend interface with a `fake`
  backend for laptops and tests.** Made by natlas-backend under the
  delegation. *Why:* it runs on a free Colab T4 with no conversion step
  (vLLM and faster-whisper would need AWQ/CTranslate2 conversions of the
  NCAIR weights), matches the model cards' own code, and the fake backend
  lets android/flutter work without a GPU. *Applies to:* backend; android
  and flutter can run `python -m natlas_server` locally (`server/README.md`).

- **Wire-protocol details fixed in `docs/API.md` Part 1:** WebSocket
  problems arrive as an `error` frame after the upgrade, then close code
  `4000 + HTTP status`; `/health` adds `users` (active count and cap) and a
  per-model `state`; a supported language that the server does not enable is
  a 400; errors carry OpenAI's `param`. Made by natlas-backend under the
  delegation. *Why:* found while implementing; the close codes let clients
  react even if they miss the frame, and the cap stays visible before it is
  hit. *Applies to:* android, flutter (error mapping for live sessions).

- **ASR runs without forcing Whisper's language token by default
  (`NATLAS_ASR_FORCE_LANGUAGE=0`).** Made by natlas-backend under the
  delegation. *Why:* the NCAIR model cards call the pipeline with no
  language, their `generation_config` leaves the language slot open, and
  Igbo has no Whisper token at all. Switchable if tests show forcing helps.
  *Applies to:* backend.

- **The Colab notebook runs cloudflared with `--protocol http2`.** Made by
  natlas-backend under the delegation. *Why:* in a test on 2026-10-10 the
  default QUIC transport delivered no client-to-server WebSocket frames (live
  audio never reached the server); with HTTP/2 live audio and SSE both
  worked. *Applies to:* backend, anyone running the notebook.

## 2026-10-10

- **Android build stack: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21,
  compileSdk/targetSdk 36, minSdk 24; natlas-core compiles with Kotlin
  language/API version 2.1.** Made by natlas-android under the delegation.
  *Why:* AGP 9.4 is the newest, but its built-in Kotlin changes how plugins
  are applied and Flutter plugin builds may not follow yet; two days before
  the deadline the mature AGP 8 line is the safer bet, and it needs only the
  installed platform 36. Language version 2.1 keeps the published metadata
  readable by apps and the Flutter plugin on Kotlin 2.1+.
  *Applies to:* android, flutter (consume via `mavenLocal()` or composite
  build; either works).

- **`NAtlasClient(config)` needs no `Context`: natlas-core captures the app
  context with a tiny internal `ContentProvider` to keep the per-install
  `X-NAtlas-User` UUID in SharedPreferences.** Made by natlas-android under
  the delegation. *Why:* keeps the API in docs/API.md unchanged and the
  README snippet short; without it the user cap can't be counted per install.
  *Applies to:* android, flutter (nothing to do; the provider merges in).

- **Wrapper SDKs set their User-Agent through
  `@NAtlasPlatformApi NAtlasClient(config, userAgent)`.** Made by
  natlas-android under the delegation. *Why:* the wire protocol asks the
  Flutter plugin to send `natlas-flutter/<ver>`, but all calls go through
  Kotlin. An opt-in constructor keeps it off the app-facing surface.
  *Applies to:* flutter.

- **Judges' server for 15–17 Oct: the same Colab notebook behind a fixed
  tunnel address (ngrok's one free static domain), restarted on a schedule
  by Baffa; the README also offers a "run your own server" notebook.**
  Made by natlas-pm under the delegation. Replaces the open hosting question.
  *Why:* Colab URLs change each session; a fixed tunnel address fixes that
  without spending money. Paid serverless GPU (Modal, HF Endpoints) needs
  Baffa's approval to spend, so it is the upgrade, not the plan. Risks: Colab
  disconnects, and ngrok's free tier has a 1 GB/month bandwidth cap.
  *Applies to:* backend (support a fixed tunnel domain), Baffa (Colab and
  ngrok accounts, restarts).

- **Submit on the Innovation & Enterprise track by 9:00 PM WAT Mon 12 Oct,
  three hours before the portal closes.** Made by natlas-pm under the
  delegation.
  *Why:* the human-only list includes a CAC certificate, which is that
  track's requirement; the buffer covers portal or upload trouble.
  *Applies to:* pm, Baffa.

- **The SDK's own code is Apache-2.0; the models keep their own licence.**
  Made by natlas-pm under the delegation.
  *Why:* standard for Android and Flutter libraries, with a patent grant; the
  repo holds no model weights, so the N-ATLAS licence does not bind our code.
  *Applies to:* android, flutter, backend (add `LICENSE` before release).

- **Baffa delegated all build decisions: "stop asking me questions, do the
  right thing… if you need something go online and search".**
  *Why:* Baffa is busy with other work.
  *Applies to:* all. Decide, record the decision here with the reasoning,
  and research online instead of asking. Commits on feature branches are
  allowed without asking. Never push, never touch `main`/`dev`. Spending
  money or creating cloud accounts still needs Baffa.

- **Wire protocol is OpenAI-compatible, plus a WebSocket for live mic**
  (`docs/API.md`). Made under the delegation above.
  *Why:* any official NCAIR endpoint later becomes a config change, standard
  tools work, and two community servers already use this shape.
  *Applies to:* backend, android, flutter.

- **Namespace `io.github.baffaabba` (Maven group and Kotlin package
  `io.github.baffaabba.natlas`); Flutter package `natlas_sdk`.**
  *Why:* Maven Central verifies `io.github.<user>` against the GitHub account
  (Baffaabba), so we own it without buying a domain.
  *Applies to:* android, flutter.

- **Toolchain is portable under `E:\dev-tools`** (JDK 17, Android SDK,
  Flutter 3.47.7). Load it with `source /e/dev-tools/env.sh` (Git Bash) or
  `. E:\dev-tools\env.ps1` (PowerShell). Nothing was installed system-wide.
  *Applies to:* android, flutter, qa.

- **This project stands alone; drop all references to other projects.**
  *Why:* Baffa: "forget gyara". This replaces the NAIC eligibility question
  in the "Target: NAIC 2026" entry below.
  *Applies to:* pm, critic.

- **Test hosting is Google Colab for now.**
  *Why:* research showed no usable hosted API. Agent proposed an own gateway
  on serverless GPU plus a CPU fallback; Baffa chose to test on Colab first.
  *Applies to:* backend builds the server as a Python package plus a Colab
  notebook with a tunnel URL. Hosting for the 15–17 Oct check is still open.
  This replaces the "Hosting" entry below.

- **Our edge: Android-native, Flutter, live microphone input and a one-call
  voice pipeline on a real phone.**
  *Why:* Baffa confirmed the agent's suggested line.
  *Applies to:* pm (README, video), all builders (these four must work).

- **The SDK is a cloud client only; no on-device inference in v1.**
  *Why:* agent proposed hybrid (on-device ASR + server LLM); Baffa chose cloud
  only. N-ATLaS is 8B (~6 GB resident at Q4), beyond most phones.
  *Applies to:* android, flutter, backend.

- **One Kotlin core library; the Flutter plugin wraps it through Pigeon.**
  *Why:* Baffa accepted the recommended architecture. One engine, two APIs.
  *Applies to:* android, flutter.

- **v1 covers speech-to-text, LLM chat and the voice pipeline (mic → ASR →
  N-ATLaS → text).**
  *Why:* Baffa's scope choice.
  *Applies to:* all.

- **Text-to-speech is deferred.**
  *Why:* N-ATLAS ships no TTS model. Agent proposed a pluggable server TTS
  with Meta MMS-TTS (CC-BY-NC); Baffa chose to defer.
  *Applies to:* backend, android, flutter. Leave a slot in the pipeline; build
  nothing for TTS.

- **Target: NAIC 2026.**
  *Why:* Baffa's answer. Deadline Mon 12 Oct 2026, 11:59 PM WAT.
  *Applies to:* pm, critic.

- **Lean agent team: pm, android, flutter, backend, qa, critic.**
  *Why:* Baffa chose the lean set over a larger nine-role team.
  *Applies to:* all.

- **Hosting: Baffa asked for deep research before deciding.** Research done
  (`docs/research/HOSTING.md`): no public official or stable third-party API
  exists. Recommendation awaiting Baffa: own gateway, OpenAI-compatible
  protocol, configurable base URL. Not yet a decision.
  *Applies to:* backend must not build a server until Baffa decides.
