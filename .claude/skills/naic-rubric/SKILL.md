---
name: naic-rubric
description: The NAIC 2026 rules the N-ATLAS SDK is judged against — Problem Statement 01 text, the "must directly integrate" rule, the seven required submission components, the six evaluation criteria, deadlines, what winning earns and the competing SDK entries. Load before scoping, reviewing the build as a judge would, writing docs or the video script, or packaging the submission.
---

# NAIC 2026: what we are judged on

Source: https://ncair.nitda.gov.ng/naic/ (checked 2026-10-08 and 2026-10-10).
Re-check the page before relying on it; organisers edit it.

## Problem Statement 01: Developer Infrastructure

> Build the tools that make N-ATLAS easy to build with. N-ATLAS is open-source
> and downloadable, but Nigerian-maintained tooling is needed to help developers
> move from accessing the model to integrating, testing, adapting and deploying it.

An Android SDK for Kotlin and Flutter fits "integrating and deploying".

**Hard requirement: "must directly integrate with N-ATLAS. Wrapping another
general-purpose model does not qualify."** The SDK must call `NCAIR1/N-ATLaS`
and the `NCAIR1/*-ASR` models. No other LLM, not even as a fallback.

"One problem statement per team."

## The seven components (all required)

1. **Working artefact**: repo, deployed app, published model, live API or
   dataset. For us: tagged repo, the Kotlin library and Flutter plugin
   installable from the README, sample apps (APK attached to a release),
   and a reachable server if we host one.
2. **N-ATLAS integration evidence**: `docs/N-ATLAS-INTEGRATION.md`, showing
   which model serves which call, with request/response examples.
3. **Real-world validation**: testing with actual users or real data, not
   simulated. **PS01 requires ≥ 2 external beta testers** (developers who
   built something with the SDK). Written feedback and screenshots in
   `docs/validation/`.
4. **Technical documentation**: architecture, setup, usage.
5. **Video**: 3–5 minutes, end to end (add dependency → speak Hausa → see
   text → N-ATLaS reply, on a real phone).
6. **Team profile.**
7. **Endorsement / registration**: CAC certificate (Innovation & Enterprise track).

Materials in English.

**Re-check 2026-10-10 (natlas-pm):** deadline, PS01 and the seven components
are unchanged. New facts: portal for our track is
https://ondi.innox.africa/apply/naic/naic-innovation-enterprise (form fields
not published; the portal did not resolve from our machine). Teams are 2–5
(the FAQ says 1–6). Component 7 for Track B is "CAC certificate or ID"
(third parties say "and valid ID"; have both). Team profile = names,
affiliations, roles. All members Nigerian citizens or a registered Nigerian
entity. Full notes: `docs/STATUS.md` section 4.

## The six evaluation criteria (no weights published)

Evaluation "focuses heavily on the **quality of the working build** and the
**depth of its integration with N-ATLAS**".

| Criterion | What earns it for the SDK |
| --- | --- |
| Working artefact & technical rigour | README quickstart works on a clean machine; tests green; sample APK runs on a mid-range phone |
| N-ATLAS integration | All four ASR models and the LLM used for real calls; voice pipeline chains them |
| Real-world validation | ≥ 2 external developers integrated it; measured latency and success rate on real devices and networks |
| Impact potential | Nigerian app developers add Hausa/Igbo/Yoruba voice and chat in an afternoon |
| Scalability & sustainability | Server scales; 1,000-user licence cap handled with a path to Awarri's commercial licence |
| Team capability | Roles, decisions log, disciplined gates |

## Timeline

- Submission: **Mon 12 Oct 2026, 11:59 PM WAT. Late submissions are not accepted.**
- Integration check 15–17 Oct (judges may run it: README path and server must
  be up); shortlisting 16–20 Oct; mentorship 25 Oct – 4 Nov; finals 8–10 Nov.

## What winning earns

No cash prize is listed. Winners get a procurement pathway review, ONDI
incubation, investor introductions and N-ATLAS API integration support.
Shortlisted teams get **N-ATLAS API credentials and compute credits**, which
suggests an official API exists for shortlisted teams only.

## Competing SDK entries (as of 2026-10-08)

- **N-ATLAS-Forge** (github.com/Greatgeo/N-ATLAS-Forge), `natlas` on PyPI /
  N-ATLAS-Kit: SDK clients (Python-first).
- **AtlasForge** (github.com/im-aderm/atlasforge): eval and fine-tuning.
- **OpenAtlas** (github.com/Benedict258/OpenAtlas): hosted gateway
  `api.getopenatlas.xyz`, hand-issued keys, "not always on" (2026-10-10).
- **N-Atlas-Toolkit** (github.com/samolubukun/N-Atlas-Toolkit): self-deploy
  OpenAI-compatible server on Modal/Lightning, LLM + ASR (2026-10-10).

PS02 (voice) wording: solutions "must use the official N-ATLAS ASR service",
which is not publicly documented. Ask naic@nitda.gov.ng.

Check them again before scoping. **Our edge (Baffa, 2026-10-10):
Android-native, Flutter, live microphone input and a one-call voice pipeline
on a real phone.** Every doc, the README opening and the video lead with it.
