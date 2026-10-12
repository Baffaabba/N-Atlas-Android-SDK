# Status: N-ATLAS SDK for NAIC 2026

Last updated: **Sat 10 Oct 2026** by natlas-pm.

> **Note, Mon 12 Oct:** this page is the plan from 10 Oct and is out of date.
> What was built and measured is in `docs/validation/RESULTS.md` and
> `docs/N-ATLAS-INTEGRATION.md`. Portal answers: `docs/SUBMISSION-FORM.md`.
Deadline: **Mon 12 Oct 2026, 11:59 PM WAT.** Late entries are not accepted.
We submit by **9:00 PM WAT Monday**, which leaves three hours spare.

The rule for this page: a box with no evidence is red. Evidence means a
file, a command and its output, an APK, or a URL. "Someone said it works"
is not evidence.

- **Green**: done, and the evidence column proves it.
- **Amber**: started, or done but not yet proven on a real phone or a clean machine.
- **Red**: not started, or no evidence.

---

## 1. Definition of Done

| # | Box | State | Evidence (or what will count as evidence) |
| --- | --- | --- | --- |
| 1 | API contract written and shared by all three builders | Green | `docs/API.md` (v0.1 draft, 2026-10-10) |
| 2 | Colab server loads all five `NCAIR1/*` models; `/health` says `ok` | Red | Nothing in `server/` yet. Needs: notebook run log + `/health` JSON pasted into `docs/N-ATLAS-INTEGRATION.md` |
| 3 | Kotlin `natlas-core` builds; unit tests green | Red | Nothing in `android/` yet. Needs: `gradlew :natlas-core:test` output, saved to `docs/validation/measurements/` |
| 4 | Flutter plugin builds; example app runs on a real Android phone | Red | Nothing in `flutter/` yet. Needs: `flutter build apk` output + phone screenshot |
| 5 | Transcribe a **file** in Hausa, Igbo, Yoruba and Nigerian English | Red | Needs: four screenshots from a real phone + the four request/response pairs in `docs/N-ATLAS-INTEGRATION.md` |
| 6 | Transcribe a **live mic** clip in all four languages | Red | Needs: four phone screenshots showing partial then final text |
| 7 | Streaming chat with `NCAIR1/N-ATLaS` | Red | Needs: phone screenshot or screen recording + SSE sample in the integration doc |
| 8 | Voice pipeline: mic → speech-to-text → N-ATLaS → reply on screen | Red | Needs: one unedited take on a real phone (this is the video's key shot) |
| 9 | Licence line in code (`NAtlasClient.ATTRIBUTION`) and on both sample apps' About screens | Red | Needs: file path + screenshot |
| 10 | Server reachable by judges 15–17 Oct | Red | Plan agreed (see `docs/DECISIONS.md`, 2026-10-10). Needs: fixed URL answering `/health` on each of the three days |
| 11 | README quickstart works on a clean machine | Amber | `README.md` draft exists. Not tested; the code it describes does not exist yet. Needs: QA run log from a fresh checkout |
| 12 | N-ATLAS integration evidence | Amber | `docs/N-ATLAS-INTEGRATION.md` skeleton. Needs real examples, code paths and measured latency |
| 13 | ≥ 2 external developer testers, named, with written feedback and screenshots | Red | `docs/validation/TESTER-GUIDE.md` ready to send. Needs: two folders under `docs/validation/testers/` |
| 14 | Public repo with a tagged release and the sample APK attached | Red | Needs: GitHub release URL |
| 15 | Video, 3–5 min, real phone | Red | Script and shot list in `docs/SUBMISSION.md`. Needs: video URL |
| 16 | Team profile (names, affiliations, roles) | Red | Needs: `docs/TEAM.md` filled by Baffa |
| 17 | CAC certificate (and ID) | Red | Needs: PDF ready to upload (kept out of git) |
| 18 | Submitted on the portal | Red | Needs: confirmation email or screenshot |

The minimum that must ship, whatever else is cut: boxes 2, 3, 5, 7, one
Flutter sample (box 4), and boxes 13–18.

---

## 2. Gates

Each gate has a time and a cut. If the gate is missed, the cut happens
straight away. Nobody stretches.

| Gate | When (WAT) | Must be true | If missed, cut this |
| --- | --- | --- | --- |
| G0 | **Sat 10 Oct, 10:00 PM** | Server, Kotlin and Flutter skeletons build. Colab notebook loads at least N-ATLaS and Hausa-ASR. | Drop Flutter parity for secondary calls (keep Flutter chat + transcribe only). Saves ~4 h; costs a little on "working artefact". |
| G1 | **Sun 11 Oct, 12:00 noon** | Boxes 2 and 3 green. Kotlin sample transcribes a Hausa file and streams chat on a real phone. | Drop live mic streaming; use record-then-send for mic input. Saves ~6 h; costs part of our edge ("live microphone"). |
| G2 | **Sun 11 Oct, 6:00 PM** | Boxes 5, 7 and 8 green on a real phone. Flutter example runs. Tester build sent to both testers. | Drop voice pipeline polish (plain screen, no animation). Saves ~2 h; costs little. |
| G3 | **Mon 12 Oct, 12:00 noon** | Box 6 green (or cut at G1). Both testers' feedback in. Integration doc has real examples and measured latency. | Submit with whatever tester evidence exists and say so honestly. Costs on "real-world validation". |
| G4 | **Mon 12 Oct, 4:00 PM** | Video recorded. Release tagged, APK attached. Team profile and CAC ready. | Record the video with the Kotlin sample only. |
| G5 | **Mon 12 Oct, 9:00 PM** | **Submitted.** Confirmation saved. | No cut. This is the line. Portal closes 11:59 PM. |
| G6 | **Thu 15 – Sat 17 Oct** | Judges' server answers `/health` each day. | Point judges to the "run your own server" notebook in the README. |

---

## 3. Human-only work (Baffa)

Agents cannot do these. Each has one owner and a due time. "Chase" is when
natlas-pm asks again if it is not done.

| # | Task | Owner | Due (WAT) | State |
| --- | --- | --- | --- | --- |
| H1 | **Hugging Face access to the five gated `NCAIR1/*` repos, with a token** | Baffa | done | Green: token is in `.env` (value not read; `.env` is git-ignored). Still to check: the token's account has accepted the terms on all five repos, not just N-ATLaS. |
| H2 | **Google Colab account with GPU runtime** (Colab Pro if the free tier keeps disconnecting) and an ngrok account for a fixed tunnel address | Baffa | Sat 10 Oct, 10:00 PM | Red |
| H3 | **Email naic@nitda.gov.ng** asking for the official N-ATLAS API format (draft in `docs/SUBMISSION.md`, appendix A) | Baffa | Sat 10 Oct, 10:00 PM | Red |
| H4 | **Second team member confirmed.** The NAIC page says teams are 2–5 people (its FAQ says 1–6; the page contradicts itself). Name, affiliation, role. | Baffa | Sun 11 Oct, 12:00 noon | Red |
| H5 | **Two external developer testers, named.** Line them up now; send them `docs/validation/TESTER-GUIDE.md`, the server URL and a test key at G2. Collect written feedback and screenshots. | Baffa | Names: Sat 10 Oct, 10:00 PM. Feedback: Mon 12 Oct, 12:00 noon | Red |
| H6 | **A real Android phone** for testing and the video (mid-range is better than flagship; note model and Android version) | Baffa | Sun 11 Oct, 12:00 noon | Red |
| H7 | **CAC certificate and a valid ID**, scanned as PDF, kept out of git | Baffa | Mon 12 Oct, 12:00 noon | Red |
| H8 | **Team profile**: names, affiliations, roles, one line each on relevant experience. Fill `docs/TEAM.md`. | Baffa | Mon 12 Oct, 12:00 noon | Red |
| H9 | **Push the repo to a public GitHub repo and create the release** with the sample APK (agents never push) | Baffa | Mon 12 Oct, 3:00 PM | Red |
| H10 | **Record the video** on the real phone, following the script in `docs/SUBMISSION.md` | Baffa | Mon 12 Oct, 4:00 PM | Red |
| H11 | **Submit on the portal** (Innovation & Enterprise track): https://ondi.innox.africa/apply/naic/naic-innovation-enterprise | Baffa | Mon 12 Oct, 9:00 PM | Red |
| H12 | **Keep the judges' server up 15–17 Oct**: restart the Colab notebook on schedule so the fixed URL keeps answering | Baffa | Thu 15 – Sat 17 Oct | Red |

---

## 4. NAIC page re-check (Sat 10 Oct 2026)

Source: https://ncair.nitda.gov.ng/naic/

Unchanged:
- Deadline: Mon 12 Oct 2026, 11:59 PM WAT. Late submissions are not accepted.
- PS01 text, including "must directly integrate with N-ATLAS". At least two external beta testers.
- The seven components and the six evaluation criteria. "A functioning artefact is mandatory."
- Integration check 15–17 Oct; notification 16–20 Oct; mentorship 25 Oct – 4 Nov; finals 8–10 Nov.

New to our notes (not in the `naic-rubric` skill before today):
- **Portal links.** Innovation & Enterprise: https://ondi.innox.africa/apply/naic/naic-innovation-enterprise.
  Academia & Research: https://ondi.innox.africa/apply/naic/naic-academic-research.
  The page does not list the form fields, file formats or size limits. We could
  not open the portal from here (its address did not resolve on 10 Oct), so the
  fields are unknown. Baffa should open it on Saturday night to see the form early.
- **Team size 2–5** for both tracks. The FAQ says 1–6. Treat 2 as the safe minimum.
- **Component 7 for our track:** "CAC certificate or ID". Third-party listings
  say "CAC certificate and valid ID". Have both ready.
- **Team profile** must give names, affiliations and roles.
- All members must be Nigerian citizens or a registered Nigerian entity.
  Diaspora teams need at least half their members in Nigeria.
- PS01's example solutions name Python or JavaScript SDKs. These are examples,
  not rules. A Kotlin and Flutter SDK still fits, and it is our difference.
- Contacts: naic@nitda.gov.ng (Secretariat), ncair@nitda.gov.ng.
