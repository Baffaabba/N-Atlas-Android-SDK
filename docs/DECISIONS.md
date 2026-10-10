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

## 2026-10-10

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
