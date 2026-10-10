# N-ATLAS Android SDK

Kotlin core library + Flutter plugin for the N-ATLAS models (NCAIR/Awarri):
speech-to-text in Hausa, Igbo, Yoruba and Nigerian English, and chat with the
N-ATLaS LLM. Cloud client. Target: NAIC 2026 (closes Mon 12 Oct 2026, 11:59 PM WAT).

Start every session by loading the `natlas-context` skill and reading
`docs/DECISIONS.md`. Decisions there beat any default.

## Agent team (`.claude/agents/`)

| Agent | Use for |
| --- | --- |
| `natlas-pm` | Scope, cuts, status vs Definition of Done, open questions, NAIC package |
| `natlas-android` | Kotlin `natlas-core`, Gradle, Kotlin sample app |
| `natlas-flutter` | Flutter plugin (Pigeon), Dart API, Flutter example app |
| `natlas-backend` | Server / hosted endpoint, wire protocol, auth, user cap, deploy, latency |
| `natlas-qa` | Run suites, sample apps, README path, edge cases |
| `natlas-critic` | Review at every checkpoint: deadline risk, overengineering, API mistakes, judge questions |

Skills: `natlas-context` (ground truth), `sdk-api-rules` (API design rules),
`naic-rubric` (judging rules), `plain-english-copy` (all user-facing text).

Research: `docs/research/MODELS.md`, `docs/research/HOSTING.md`.

## Hard rules

- Only N-ATLAS models (`NCAIR1/*`). No other LLM or ASR, not even as fallback.
- No tokens or keys in code, APKs, assets or git. Never commit `.env`,
  `local.properties`, keystores or audio.
- Licence attribution on every public surface; state the 1,000-user cap.
- Never commit to `main` or `dev`. Feature branches only. Ask Baffa before
  every commit.
