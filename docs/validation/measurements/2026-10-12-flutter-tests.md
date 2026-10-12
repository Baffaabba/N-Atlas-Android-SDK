# Flutter plugin: analyze and tests, 12 Oct 2026

Machine: Baffa's Windows 11 PC, Git Bash, `source /e/dev-tools/env.sh`.
Flutter 3.47.7 stable, Dart 3.13.5. No phone or emulator attached, so these
are host-side tests only (Pigeon bridge faked in Dart).

| Command (run in) | Result |
| --- | --- |
| `flutter analyze` (`flutter/natlas_sdk`) | No issues found (301.7 s) |
| `flutter test` (`flutter/natlas_sdk`) | 50 passed, 0 failed |
| `flutter analyze` (`flutter/natlas_sdk/example`) | No issues found (121.3 s) |
| `flutter test` (`flutter/natlas_sdk/example`) | 6 passed, 0 failed |

Plugin tests cover the Dart API, request mapping and the one-to-one mapping of
all 12 Kotlin `NAtlasException` codes to Dart exception types. Example tests
cover the no-server state, the attribution and 1,000-user cap on the About
screen, plain error messages, and saving/loading the server address and key.

Not tested here: anything on a real device (mic, live streaming, cancelling a
stream releasing the mic) and calls against a live server.
