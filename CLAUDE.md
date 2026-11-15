# QuietDLP

Android APK, Kotlin + Jetpack Compose; GPL-3.0. Keep the existing native architecture.
Read `ESTADO.md` first for the current branch, checks and remaining work.

## Build and test (Windows PowerShell, repository root)

Requirements: JDK 17, Android SDK platform 35 and compatible build tools, configured in
the ignored `local.properties`. Gradle wrapper: 8.10.2. Dependencies may need network on a fresh machine.

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat assembleDebug --console=plain
```

For cached dependencies add `--offline`. To verify execution instead of cached results add
`--rerun-tasks`. JUnit XML: `app/build/test-results/testDebugUnitTest/`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Do not read, print, commit or replace signing material or private user files.

## Working rules

- Claude Code leads; Codex contributions stay on a branch for review. Do not merge without Sebas.
- Small commits and push to the existing remote are authorized since 2026-11-14. Keep Sebas's
  configured authorship; no assistant attribution, force push or bypass of branch protections.
- Releases, deployment, new remotes and device installation/testing require specific authorization.
- Device reference: Samsung, according to Claude's 2026-11-12 report. Current connection and model
  are not verified. Do not use the Realme without authorization. Do not clear real user data.
- Function new = entry new in Features, with English and Spanish resources and parity tests.
- Preserve preference keys, backup format compatibility, LICENSE and THIRD_PARTY_NOTICES.md.
- Follow `C:\AI\AGENTS.md`, `ESTANDAR-COMUN-APPS.md` and `REQUISITOS-BASE-APPS.md`.
  Reference common components in QuietRingtones, checking their behavior before adapting them.
- Keep findings distinct from runtime verification; update `ESTADO.md` with commands and results.
- Replies for Claude go in the existing exchange folder, continuing the same subject when possible.
