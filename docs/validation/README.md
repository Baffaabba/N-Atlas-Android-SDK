# Validation evidence

This folder is NAIC component 3, "real-world validation". Problem Statement
01 needs at least two external developer testers. Everything here must be
real: real people, real phones, real networks. Nothing simulated.

## What goes where

```
docs/validation/
  TESTER-GUIDE.md           Send this to testers
  testers/
    <first-last>/
      feedback.md           Their answers, pasted as written (template in the guide)
      screenshots/          PNG or JPG, at least two
  measurements/
    <date>-<device>.md      Our own runs: device, Android version, network,
                            what was measured, how many runs, median and worst
```

## Rules

- **Consent first.** Only publish a tester's name, words and screenshots if
  they said yes in their feedback. If they said no, keep a note that the test
  happened, without names.
- **Paste, don't polish.** Copy feedback as written. Fix nothing except
  hiding keys or personal data. Keep the bad parts; they show the test was real.
- **No secrets.** Blur server URLs, keys and tokens in screenshots before
  adding them.
- **No audio files.** Audio is git-ignored (`.gitignore`). Describe the clip
  instead: language, length, speaker (e.g. "native Hausa speaker, 8 s, quiet room").
- **Numbers need their source.** A latency figure lists the device, network,
  number of runs, and the median. One run is an anecdote, not a number.

## Status

Testers so far: none. Tracked in `docs/STATUS.md` (box 13, task H5).
