---
name: project-toolchain
description: What flutter doctor and first builds showed on Baffa's Windows machine (portable E:\dev-tools Flutter 3.47.7) and the slow/quirky parts to plan around
metadata:
  type: project
---

Findings from 2026-10-11 (Flutter 3.47.7 stable, Dart 3.13.5, JDK Temurin 17.0.20.1, Android SDK under E:\dev-tools; load with `source /e/dev-tools/env.sh`).

- `flutter doctor`: Android toolchain OK, licences accepted. "Connected device" check crashes: `adb.exe: failed to check server version: protocol fault`. No device or emulator was available to the agent.
- Visual Studio missing (irrelevant: Android only).
- First `flutter analyze` took ~13 minutes (cold analyzer + likely antivirus scanning); run long commands with `run_in_background`.
- Bash `tail` on `E:\dev-tools\install.log` hung while the installer was writing; PowerShell `Get-Content` worked.
- Flutter 3.47.7 templates default to AGP 9.1.0 / Kotlin 2.4.0 / Gradle 9.3.1; Flutter's Gradle plugin errors below AGP 8.11.1 / Kotlin 2.2.20 / Gradle 8.14. See docs/DECISIONS.md for the pinned stack.

**Why:** these cost time when rediscovered.
**How to apply:** budget for slow first runs; don't promise on-device checks unless adb works and a phone is attached.

Update 2026-10-12: cold `flutter analyze` ~5 min (plugin) / ~2 min (example); example `flutter test` ~2 min. First `flutter build apk --release` of the example ran 31 min then failed with "AAPT2 ... Daemon startup failed" (transient, likely machine load from parallel Gradle builds by other agents); retry needed. Start APK builds early.
