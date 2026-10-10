# Checkpoint review, Sun 11 Oct 2026, 00:50 WAT

Reviewer: natlas-critic. Branch `feat/project-setup` at `5a7d1f5`. Scope:
`docs/API.md`, `android/`, `server/`, `README.md`, `docs/STATUS.md`,
`docs/SUBMISSION.md`. `flutter/` not reviewed (still being built).

Checked by running:
- `server/.venv/Scripts/python.exe -m pytest -q`: **66 passed** in 11 s.
- `android/natlas-core/build/test-results`: 69 unit tests, 0 failures.
  `ServerIntegrationTest` passed against a running server at 00:24 WAT. That
  was the android agent's run; mine was served from Gradle's cache.
- Started the fake server: `/health` answers with every model `backend: fake`.
- Web checks (HF Spaces docs, HF pricing, Modal pricing, Oracle Free Tier FAQ,
  `QuantFactory/N-ATLaS-GGUF` metadata).

---

## The three things that matter

### 1. No real N-ATLaS model has served a request yet, and two of the four ASR models can't be downloaded

The code is ahead of the evidence. Every test runs against the `fake` backend.

- **The HF token can't download `NCAIR1/Igbo-ASR` or
  `NCAIR1/NigerianAccentedEnglish`** (403 on files, per natlas-backend's probe
  on 10 Oct). The gate terms were never accepted on those two repos. With the
  token as it is, Definition of Done boxes 5 and 6 can't go green for Igbo or
  English. **Baffa: two clicks on huggingface.co, today.** `docs/STATUS.md` H1
  says this is "still to check". It has been checked, and it fails.
- **`server/natlas_server/backends/transformers_llm.py` has never run.** No
  log, no `/health` from a GPU, no SSE sample. `pyproject.toml` does not pin
  `transformers` (`>=4.56`), so Colab installs whatever is newest (the local
  venv already has 5.19). `TextIteratorStreamer`, `stop_strings` and the
  bitsandbytes path are where a major-version change bites. Run the notebook
  end to end now. Then pin `transformers`, `bitsandbytes`, `accelerate` and
  `torch` to the versions that worked.
- **The notebook clones a repo that isn't public yet.**
  `server/colab/make_notebook.py:69` uses
  `https://github.com/Baffaabba/N-Atlas-SDK.git`, but H9 (push) is due Mon
  3 PM. Use the zip-upload cell (3b) today, or have Baffa push sooner. A
  private repo plus a token is not supported by the cell.
- **G0 (Sat 10 PM) passed without its condition.** No Colab run loaded
  N-ATLaS and Hausa-ASR. `STATUS.md` says to apply the G0 cut: Flutter keeps
  chat and transcribe only. Apply it, or record in `DECISIONS.md` why not.
  `STATUS.md` still says "Nothing in `server/` yet" and "Nothing in
  `android/` yet". Boxes 1–3 and 9 have evidence now. Update it so the gates
  are judged on facts.

**Do instead, in this order:** Baffa accepts the two gates. Run the notebook
on a T4 via zip upload. Paste the real `/health` and one SSE and one
transcription response into `docs/N-ATLAS-INTEGRATION.md` (all TODO now).
Install `android/sample/build/outputs/apk/debug/sample-debug.apk` on a real
phone, point it at the tunnel and try the four languages. That is G1, due Sun
12:00 noon. Nothing else matters until it is green.

### 2. Judges' hosting: Colab breaks Google's terms, and a free HF Space is no longer free. Use Modal's free credits with the existing server

What I found:

| Option | Free? | Fits? | ToS | Verdict |
| --- | --- | --- | --- | --- |
| Colab + ngrok static domain (current plan, DECISIONS 2026-10-10) | Yes | T4, 12 h session cap, idle disconnects | Colab FAQ bans "web service offerings" and "connecting to remote proxies" | Fine for filming the video while you work in it. Not for three days of judges. |
| **HF Docker Space, CPU Basic (2 vCPU, 16 GB, 50 GB disk that is wiped on restart)** | **No.** HF docs as of today: "creating a new Space that runs on compute (Gradio or Docker) requires a paid plan". PRO is $9/month. | RAM fits Q4_K_M (4.92 GB) + 4 Whisper-small fp32 (~4 GB) + KV cache. Speed on 2 vCPU: my estimate is 2–4 tokens/s for an 8B Q4 model, not measured. A 150-token reply takes about a minute. Live partials would lag, because each partial re-transcribes the whole open utterance on the same 2 cores. | Allowed. Sleeps after 48 h idle; a visit wakes it and re-downloads ~9 GB. | Costs $9 and gives a slow demo. |
| Oracle Always Free A1 (4 OCPU, 24 GB) | Yes | About twice the HF CPU speed; same llama.cpp work | Allowed | Needs a real credit card (no prepaid or virtual cards). "Out of host capacity" errors are common. You also need HTTPS and a domain. Too much risk in 24 h. |
| Kaggle | Yes | GPU notebooks, 12 h sessions | Not a hosting service; same problem as Colab | No. |
| **Modal, Starter plan** | **$30 of free credits a month.** T4 is $0.59/h, plus CPU and memory (about $0.80/h total for 2 cores and 16 GiB). | Same T4 as Colab, so the numbers we measure on Colab carry over. Scales to zero. | Web endpoints are what Modal sells. WebSocket ASGI apps are supported. | **Recommended.** |

There is also a provenance problem with the CPU route. `QuantFactory/N-ATLaS-GGUF`
is an **ungated** re-upload of the gated NCAIR weights, and its card has no
licence field. "You serve a third-party re-upload that skips the gate" is a
judge's question we don't want to get. If we ever go CPU, convert
`NCAIR1/N-ATLaS` ourselves with llama.cpp, in a Colab session we are actively
using. Put the GGUF in Baffa's own gated HF repo with the N-ATLAS licence and
attribution, and note its sha256.

**Plan (smallest change to `server/`: no backend code at all):**
1. Add one file, `server/deploy/modal_app.py` (~50 lines). It builds an image
   with `pip install ./server[gpu]` (pinned versions from finding 1). It mounts
   a `modal.Volume` at `/data`, uses `modal.Secret` for `HF_TOKEN` and
   `NATLAS_API_KEYS`, and exposes `create_app()` through `@modal.asgi_app()`.
   Settings are env vars the server already reads: `NATLAS_BACKEND=transformers`,
   `NATLAS_HF_CACHE_DIR=/data/hf`, `NATLAS_DB_PATH=/data/users.sqlite3`.
2. Set `gpu="T4"`, **`max_containers=1`** and `@modal.concurrent(max_inputs=16)`.
   Without these, Modal starts a new GPU container for each concurrent request.
   That multiplies cost, and every new container cold-loads 8B. Use
   `scaledown_window` of about 20 minutes and a long `timeout` so SSE and live
   sessions are not cut off.
3. Pre-fill the Volume once with a one-off function that runs
   `snapshot_download` with the existing allow-lists in `hf_download.py`.
4. For 15–17 Oct: keep one container warm during WAT daytime (about 30 h,
   ~$24) and let it scale to zero at night. A cold start takes a few minutes;
   the client gets `503 model_loading` with `Retry-After`, and the sample
   already shows "still loading". Set a workspace spending limit so the free
   credits are the ceiling.
5. The URL depends only on the workspace and app name
   (`https://<workspace>--<app>-<fn>.modal.run`). It can go into the portal on
   Monday even if tuning continues on Tuesday. Keep Colab + ngrok as the
   fallback that is already built, and the "run your own server" notebook as
   the README answer.

**Needs Baffa:** a Modal account (check at sign-up whether the $30 credit
needs a card), the spending limit, an HF token with all five gates accepted
(as a Modal secret), and approval to use this instead of the 10 Oct
Colab + ngrok decision. Record the choice in `DECISIONS.md` as replacing that
entry. Build the llama.cpp backend only if Baffa rejects Modal.

### 3. "Does it install from the README?" No. And the release APK would contain the API key

- `README.md` Kotlin quickstart: `implementation("io.github.baffaabba:natlas-core:0.1.0")`
  does not resolve, because nothing is published. Next to it is
  "TODO (android): exact steps once the Gradle project exists". The Flutter git
  dependency has `url: <repo-url>  # TODO`. A Flutter app outside this repo
  also can't resolve natlas-core unless someone runs `publishToMavenLocal`
  first (DECISIONS 2026-10-11). A judge who pastes the snippet gets a Gradle
  error in the first minute.
  **Do instead:** use **JitPack**. It is free, needs no account, and builds
  from the public tag. Add a root `jitpack.yml` (JDK 17, run the Gradle build
  in `android/`). Put the JitPack coordinate in the README and in the Flutter
  plugin's Gradle file. Test it from a fresh project after Baffa pushes and
  tags. If that can't be done by Mon 3 PM, the README must give the honest
  steps (clone, `publishToMavenLocal`, `mavenLocal()`), not a line that fails.
  Also remove "TODO: add the `LICENSE` file". `LICENSE` exists.
- **Key in the APK.** `android/sample/build.gradle.kts:36` bakes
  `NATLAS_API_KEY` from `local.properties` into `BuildConfig`. That is right
  for a developer's own build. But if the APK attached to the GitHub release is
  built on Baffa's machine, it ships the judges' key to anyone who unzips it.
  **Do instead:** build the release APK with an empty key. Judges type the key
  from the submission form into Settings. Persist the typed key in the sample's
  prefs (today it is lost on restart, `SampleViewModel.kt:46`). Rotate the key
  after 17 Oct.

---

## API design (expensive later, not deadline-blocking)

- **The README and the API disagree about keys.** The README says "get a
  short-lived key from your own backend". But `NAtlasConfig.apiKey` is a fixed
  `String`, so rotating a key means building a new client. `NAtlasConfig` and
  `ChatOptions` are public `data class`es, so adding `apiKeyProvider` (or any
  field) later breaks binary compatibility for every caller, including the
  Flutter plugin. **Do instead, before the 0.1.0 tag, if it fits in 30 min:**
  make `NAtlasConfig` a plain class (keep `toString`, drop
  `copy`/`componentN`). Otherwise write it in a CHANGELOG as a known 0.2
  break. This is a finding, because the docs promise something the API can't
  do.
- **Kotlin and Dart drift on `close()`.** API.md Part 3 lists `client.close()`
  as Dart-only. Kotlin's `NAtlasClient` owns an OkHttp client and the sample
  makes a new one every time the URL or key changes
  (`SampleViewModel.kt:65-67`). Make `NAtlasClient` `Closeable` (shut down the
  dispatcher and connection pool) so both sides have it.
- Good: the live protocol (error frame, then close code `4000 + status`;
  audio buffered before `ready`; `stop()` versus cancel; one terminal outcome),
  the sealed `NAtlasException` with `CancellationException` never wrapped, the
  retry rules, and `explicitApi()`. Don't change these.

## Things a judge will poke

| Question | Answer today | Gap |
| --- | --- | --- |
| Is this N-ATLAS? | `/health` lists each `NCAIR1/*` model with its backend and quantisation. The fake backend is labelled. | No real `/health` or response anywhere in the repo. `N-ATLAS-INTEGRATION.md` is all TODO. Fill it from the Colab run. |
| Does it install from the README? | No (finding 3). | JitPack, or honest manual steps. |
| Is the server up? | No hosted server. | Finding 2. |
| Key in the APK? | Yes, if built with `local.properties` set. | Finding 3. |
| User 1,001? | The server returns 403 `user_cap_reached` for new ids. | The count relies on the client sending `X-NAtlas-User`. Leave it out and you are not counted; send random ids and you fill the cap. Add one honest README sentence: the reference server counts per install, and a production backend should count its own signed-in users. Don't build more. |
| How is this different from N-ATLAS-Forge / OpenAtlas / N-Atlas-Toolkit? | Only "Other SDK entries are Python-first" in `SUBMISSION.md`. | Add three lines to the README: Forge is a Python client, OpenAtlas is a hand-keyed hosted gateway, Toolkit deploys a server; this is the Android-native client with live mic and a one-call voice pipeline, plus a server you can run yourself. "Stands alone" (DECISIONS) was about Baffa's other project, not about comparing with competitors. |
| Does the live mic really stream? | Yes, and API.md says honestly that it is VAD plus re-transcription about once a second. | Measure the time to the first partial on a phone over 4G, through the real tunnel. |

## Overengineering

Not much. The server's extras (hashed keys, rate limit, SQLite user counter,
lazy loading with back-off) each answer a judge question. Unused options
(`NATLAS_API_KEY_HASHES`, `NATLAS_LLM_QUANTIZATION=8bit|none`) are harmless.
Leave them. **Freeze `server/` features now**. The only server work left is
pinning versions and the Modal file.

## Missing rather than wrong

- **The sample keeps the mic session open when the app goes to the
  background.** Nothing stops live or voice on `ON_STOP`. Android sends
  silence to background apps, so nothing leaks, but the README says "never
  records in the background". Add a lifecycle observer that calls
  `stop()`/cancel.
- **No debug cleartext config.** `server/README.md` says the samples "need a
  debug network-security config for local testing", but the sample has none.
  A tester pointing a phone at `http://<LAN-IP>:8000` (the fake server) gets
  `Network` on Android 9+. Add a debug-only `network_security_config` or
  `usesCleartextTraffic` in `src/debug/AndroidManifest.xml`. Never in release.
- `docs/STATUS.md` and `docs/N-ATLAS-INTEGRATION.md` are out of date (see
  finding 1).

## Good, in one line each

- The API contract (`docs/API.md`) is precise, honest about mechanisms, and matches the code.
- Kotlin live session releases the mic on stop, cancel and error, and the tests cover it.
- The server never logs audio or text, keeps uploads in memory, and stores only hashed ids.
- The sample's error messages say what happened and what to do next.
