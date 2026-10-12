# Contributing

Thanks for helping. Issues and pull requests are welcome.

## Ground rules

- **Only N-ATLAS models.** The SDK and server call the `NCAIR1/*` models
  only. Changes that add another LLM or speech model, even as a fallback,
  will not be merged.
- **No secrets.** Never commit API keys, Hugging Face tokens, `.env`,
  `local.properties`, keystores or audio files.
- **Keep the attribution.** Every public surface keeps the N-ATLAS licence
  line and the 1,000-user cap note.
- **Plain English** in docs, error messages and sample app text.

## Set up

| Part | Folder | Build and test |
| --- | --- | --- |
| Kotlin library + sample | `android/` | `./gradlew :natlas-core:test :sample:assembleDebug` (JDK 17) |
| Flutter plugin + example | `flutter/natlas_sdk/` | `flutter analyze && flutter test` |
| Reference server | `server/` | `pip install -e ".[dev]"`, then `pytest -q` (the `fake` backend needs no GPU) |

Run the server locally with the fake backend (`python -m natlas_server`,
see `server/README.md`) to work on the SDK without a GPU.

## Pull requests

1. Open an issue first for anything bigger than a small fix.
2. Branch from the default branch. One change per pull request.
3. Add or update tests, and make the suites above pass.
4. If you change the wire protocol, update `docs/API.md` in the same pull
   request. Kotlin, Flutter and the server must stay in step.
5. Describe what you changed and how you tested it (device, Android
   version, server).

## Licence

The SDK code is Apache-2.0 (`LICENSE`). Contributions are accepted under the
same licence. The N-ATLAS models have their own licence from NCAIR and
Awarri.

N-ATLAS is an initiative of the Federal Ministry of Communications,
Innovation and Digital Economy, and powered by Awarri Technologies.
