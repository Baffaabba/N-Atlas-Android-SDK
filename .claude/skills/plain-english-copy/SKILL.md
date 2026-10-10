---
name: plain-english-copy
description: Rewrite any text a developer, app user or judge will read — README, docs, KDoc/dartdoc, error messages, sample app labels, the video script — into short plain English. Apply to every piece of user-facing text before showing it, whichever role wrote it.
---

# Plain English for the N-ATLAS SDK

Two kinds of reader.

**Developers and judges** reading the README, API docs, error messages and
logs. Technical, busy, skeptical. They want the snippet that works and the
number with its evidence.

**End users** of the sample apps. Many speak Hausa, Igbo or Yoruba first and
are not technical. They need to know what to tap and what is happening.

## Rules for everyone

1. Short sentences. One idea each. Break anything over about 20 words.
2. Say what happened and what to do next. An error without a next step is
   unfinished.
3. No corporate voice: no "leverage", "seamless", "empower", "unlock",
   "revolutionise", "cutting-edge", "robust", "blazing fast".
4. Buttons are verbs: "Start recording", "Ask N-ATLAS", "Try again".
5. Never blame the reader. "We couldn't reach the server. Check your
   connection and try again." beats "Network error".
6. Numbers over vagueness, with their source. "First words in 1.8 s on a
   Tecno Spark 20 over 4G (median of 30 runs)" beats "low latency".
7. No emoji in functional UI.
8. Sentence case for labels and headings.

## For developers (README, docs, errors)

- Lead with the code, then the explanation.
- Exact names: `NCAIR1/Hausa-ASR`, `NAtlasClient`, `Language.HAUSA`.
- Show Kotlin and Dart side by side when the concept is the same.
- Name limitations plainly: "N-ATLAS has no text-to-speech model yet, so
  replies come back as text." "Free use stops at 1,000 active users a month."
- Error messages name the setting to fix.

## For app users (samples)

- No ML words: not "inference", "token", "ASR", "LLM". "Listening…",
  "Writing down what you said…", "Thinking…".
- The mic request explains why: "The app needs the microphone to hear you.
  Nothing is recorded until you tap Start."

## Nigerian languages

- Use the real letters ɓ ɗ ƙ 'y (Hausa) and tone marks / ẹ ọ ṣ (Yoruba),
  ị ọ ụ ṅ (Igbo). Never ASCII stand-ins.
- UI strings in Nigerian languages only if a fluent speaker checked them.
  A wrong label is worse than none.
