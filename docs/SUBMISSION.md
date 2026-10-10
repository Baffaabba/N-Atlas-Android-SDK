# NAIC 2026 submission package

Problem Statement 01: Developer Infrastructure. Track: Innovation & Enterprise.
Portal: https://ondi.innox.africa/apply/naic/naic-innovation-enterprise
Closes **Mon 12 Oct 2026, 11:59 PM WAT**. We submit by 9:00 PM.

Our edge, said the same way everywhere: **the N-ATLAS SDK for Android.
Kotlin and Flutter, live microphone input, and a one-call voice pipeline,
on a real phone.** Other SDK entries are Python-first.

"Planned" means the artefact does not exist yet. `docs/STATUS.md` tracks
each one.

---

## The seven components

| # | Component | Our artefact | Path or link | State |
| --- | --- | --- | --- | --- |
| 1 | Working artefact | Public repo with a tagged release (`v0.1.0`) | GitHub URL (Baffa pushes; agents never push) | Planned |
| | | Kotlin library `io.github.baffaabba:natlas-core` | `android/natlas-core/` | Planned |
| | | Flutter plugin `natlas_sdk` | `flutter/natlas_sdk/` | Planned |
| | | Kotlin sample app, APK attached to the release | `android/sample/` | Planned |
| | | Flutter example app | `flutter/natlas_sdk/example/` | Planned |
| | | Reference server + Colab notebook, fixed URL for 15–17 Oct | `server/`, `server/README.md` | Planned |
| 2 | N-ATLAS integration evidence | Which model serves which call, real requests and responses, measured latency | `docs/N-ATLAS-INTEGRATION.md` | Skeleton |
| 3 | Real-world validation | Two or more external developers, named, with written feedback and screenshots; our own device measurements | `docs/validation/testers/<name>/`, `docs/validation/measurements/` | Guide ready (`docs/validation/TESTER-GUIDE.md`) |
| 4 | Technical documentation | Architecture, setup, usage | `README.md`, `docs/API.md`, `server/README.md`, `docs/N-ATLAS-INTEGRATION.md` | Draft |
| 5 | Video, 3–5 min | End to end on a real phone (script below) | Video URL (YouTube unlisted or Drive, link-viewable) | Script ready |
| 6 | Team profile | Names, affiliations, roles | `docs/TEAM.md` | Template |
| 7 | Endorsement / registration | CAC certificate and a valid ID (PDF, not in git) | Uploaded on the portal | Baffa |

Supporting evidence for "team capability": `docs/DECISIONS.md` (every call and
why), `docs/STATUS.md` (gates and cuts).

### Portal checklist (fill on Monday)

- [ ] Repo URL and release URL
- [ ] Video URL, checked in a private browser window
- [ ] Server URL (fixed address) and a judge test key, if the form asks
- [ ] Team profile text pasted from `docs/TEAM.md`
- [ ] CAC certificate and ID uploaded
- [ ] Confirmation saved as a screenshot in a private folder (not in git)

---

## Video script (target 4:00, hard limit 5:00)

**Rules for the shoot**
- A real Android phone, filmed or screen-recorded. No emulator in the key shots.
- The voice pipeline shot is **one unedited take**. If it fails, re-shoot the
  whole take. Never splice.
- The licence line is on screen for at least 5 seconds, in the closing card.
  It also shows on the sample app's About screen.
- No speed or accuracy claims. If we say a number, it must be measured and
  in `docs/N-ATLAS-INTEGRATION.md`, with device and network.
- Hausa lines are checked by a fluent speaker before filming, with the real
  letters (ɓ ɗ ƙ 'y).
- The server must be running and `/health` must say `ok` before you press record.

### Shot list and voice-over

| Time | Shot | On screen | Voice-over (say it plainly) |
| --- | --- | --- | --- |
| 0:00–0:20 | **Cold open.** Hand holding the phone, sample app open. Speaker taps the mic and says a Hausa question. | Live words appear as they speak. Then N-ATLaS's reply streams in, in Hausa. | (No voice-over. Let the phone talk.) |
| 0:20–0:45 | Title card, then face or desk. | "N-ATLAS SDK for Android. Kotlin and Flutter." | "That was N-ATLAS, Nigeria's own language model, inside an Android app. Speech in, answer out, in one call. This is the N-ATLAS SDK. It lets any Android developer add Hausa, Igbo, Yoruba and Nigerian English voice and chat to their app." |
| 0:45–1:30 | Android Studio. Add one Gradle line. Paste the five-line Kotlin snippet from the README. | `build.gradle.kts` and `MainActivity.kt` | "You add one dependency. You give the client your server address and key. The key comes from your own backend, never from the app." |
| 1:30–2:00 | VS Code. Flutter `pubspec.yaml`, same snippet in Dart. Run the Flutter example on the phone. | Dart code, then the phone | "Flutter developers get the same calls. The plugin uses the same Kotlin engine underneath, so both behave the same." |
| 2:00–2:30 | Phone: transcribe a file, then live mic, quickly across the four languages. | Language picker: Hausa, Igbo, Yoruba, English. Text appears. | "Each language goes to its own N-ATLAS speech model. With live mic, words appear while you speak." |
| 2:30–3:10 | **The one take.** Voice pipeline in Hausa, start to finish. | Listening, then the words heard, then Thinking, then the reply streaming. | "Here is the voice pipeline in one call: microphone, speech to text, N-ATLaS, reply. One take, no edits." |
| 3:10–3:35 | Browser: server `/health` showing the five `NCAIR1` models loaded. Then the architecture diagram from the README. | `/health` JSON, diagram | "Every call goes to the official N-ATLAS models from NCAIR on Hugging Face. No other model is used, not even as a backup." |
| 3:35–3:55 | Screenshots from the two external testers, one quote each. | Tester names, their apps | "Two outside developers built with it this weekend. Here is what they said, including what broke." |
| 3:55–4:15 | Licence card. | Full attribution line (below), "Free use up to 1,000 active users a month", repo URL | "N-ATLAS is free up to a thousand active users a month. Past that, you need a licence from Awarri. The SDK counts users so you know where you stand." |

**Closing card text (exact):**

> N-ATLAS is an initiative of the Federal Ministry of Communications,
> Innovation and Digital Economy, and powered by Awarri Technologies.
>
> Free use up to 1,000 active users in a rolling 30 days. Commercial licence: datasupport@awarri.com
>
> github.com/<repo> (fill in)

**Hausa lines for the cold open and the one take.** A fluent speaker writes
or checks them before filming:
- Greeting (from `docs/API.md`): "Sannu! Yaya kake?"
- Question: [a short everyday question, e.g. about farming or the weather,
  written and checked by a fluent Hausa speaker]

### If something is cut

- Live mic cut at G1: in 2:00–2:30 and the one take, show record-then-send.
  Change the voice-over to "Tap to record, tap to send." Do not say "live".
- Flutter cut to basics: keep 1:30–2:00 but show only transcribe and chat.

---

## Appendix A: email to naic@nitda.gov.ng (Baffa sends)

Subject: PS01 entry: format of the official N-ATLAS API

> Hello NAIC Secretariat,
>
> We are entering Problem Statement 01 (Developer Infrastructure) with an
> Android SDK for N-ATLAS, in Kotlin and Flutter.
>
> The page says shortlisted teams receive N-ATLAS API credentials. Could you
> share the format of that API (endpoints, auth, request and response shape)
> for chat and speech-to-text? Our SDK speaks an OpenAI-compatible protocol
> with a configurable server address. If the official API matches, judges
> could point it there with no code change.
>
> Until then, we serve the official NCAIR1 models from Hugging Face on our
> own test server.
>
> Thank you,
> Baffa
