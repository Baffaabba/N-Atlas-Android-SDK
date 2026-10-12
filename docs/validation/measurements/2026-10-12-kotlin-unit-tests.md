# Kotlin unit tests (natlas-core), 2026-10-12

Command (Git Bash, Windows 11, Temurin JDK 17.0.20.1, Gradle 8.14.3 wrapper):

```bash
source /e/dev-tools/env.sh
cd android && ./gradlew :natlas-core:publishToMavenLocal :natlas-core:test
```

Result: BUILD SUCCESSFUL. `:natlas-core:test` runs the suites for both the
debug and release variants; the numbers are the same for each.

| Suite | Tests | Failed | Skipped |
| --- | ---: | ---: | ---: |
| TranscribeTest | 20 | 0 | 0 |
| ChatTest | 16 | 0 | 0 |
| LiveTest | 14 | 0 | 0 |
| RetryTest | 8 | 0 | 0 |
| MiscTest | 5 | 0 | 0 |
| VoiceTest | 4 | 0 | 0 |
| ServerIntegrationTest | 2 | 0 | 2 |
| **Total** | **69** | **0** | **2** |

All suites except ServerIntegrationTest use MockWebServer (401, 429 with
`Retry-After`, 5xx, timeouts, malformed JSON, streams that drop mid-way).

The 2 skipped tests (`wrongKeyIsUnauthorized`,
`transcribeChatStreamLiveVoice`) call a real server. They skip when no
server URL and key are set in the environment, as here. They did not run
in this pass.
