---
name: project-toolchain
description: Where the JDK/Android SDK/Gradle live on Baffa's Windows machine and quirks found building android/ (2026-10-10)
metadata:
  type: project
---

Portable toolchain, nothing system-wide (found 2026-10-10):
- JDK: `E:\dev-tools\jdk17` = Temurin 17.0.20.1. Android SDK: `E:\dev-tools\android-sdk` (platforms 35, 36; build-tools 35.0.0, 36.0.0). Load with `source /e/dev-tools/env.sh` (Git Bash) or `. E:\dev-tools\env.ps1`.
- Gradle 8.14.3 distribution unpacked in `E:\dev-tools\_dl\gradle\gradle-8.14.3` (used once to generate the wrapper).
- `android/local.properties` needs `sdk.dir=E\:\\dev-tools\\android-sdk`.

Quirks:
- Git Bash heredocs on this machine collapse `\\` to `\` — write files containing backslashes with the Write tool, not `cat <<EOF`.
- First Gradle run downloads AGP + deps and is slow (10+ min); run long Gradle commands in the background.

**Why:** saves re-discovery each session. **How to apply:** check these paths still exist before relying on them; see [[stack-choice]] in docs/DECISIONS.md for why AGP 8.13.2/Kotlin 2.2.21.
