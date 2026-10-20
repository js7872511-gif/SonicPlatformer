# StudioDroid v0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Android APK that imports `RobloxStudioLauncherBeta.exe`, validates and stores it, prepares StudioDroid helper files, and opens an installed Winlator-compatible Windows layer for Install/Launch/Repair actions.

**Architecture:** A single-activity Kotlin Android app uses Storage Access Framework for EXE import, app-private workspace storage for persistence, and Android package-manager intents for Windows-layer handoff. Helper `.bat` files ship as app assets. GitHub Actions builds debug and release APK artifacts from the isolated `studiodroid-build` branch.

**Tech Stack:** Kotlin, Android SDK, AndroidX AppCompat, Gradle, JUnit, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-19-studiodroid-v0.1-design.md`

## Global Constraints
- Phone-first Android app; no PC required.
- Do not bundle Roblox Studio, Wine, Box64, DXVK, or Winlator.
- Do not modify Roblox Studio binaries.
- Imported EXE must persist between app launches.
- Use ContentResolver/Storage Access Framework rather than direct filesystem assumptions.
- Android 12+ installability is the primary target.

## Review Focus
- User selects a non-EXE or malformed file: reject without overwriting the last good import.
- Content URI cannot be reopened: surface an error and preserve existing workspace.
- No supported Windows layer installed: keep files intact and show a clear status.
- Re-import replaces the prior EXE atomically.
- Repeated Install/Launch/Repair taps must not duplicate/corrupt helper files.

---

### Task 1: Android project skeleton and validation core

**Files:**
- Create: `studiodroid/settings.gradle.kts`
- Create: `studiodroid/build.gradle.kts`
- Create: `studiodroid/app/build.gradle.kts`
- Create: `studiodroid/app/src/main/AndroidManifest.xml`
- Create: `studiodroid/app/src/main/java/com/studiodroid/app/PeValidator.kt`
- Test: `studiodroid/app/src/test/java/com/studiodroid/app/PeValidatorTest.kt`

**Interfaces:**
- Produces: `PeValidator.isPeExecutable(header: ByteArray): Boolean`.

- [ ] Write failing tests for valid `MZ`, short input, and wrong signature.
- [ ] Run unit tests and verify failure before implementation.
- [ ] Implement `PeValidator.isPeExecutable`.
- [ ] Run tests and verify pass.

### Task 2: Workspace import and persistence

**Files:**
- Create: `studiodroid/app/src/main/java/com/studiodroid/app/WorkspaceManager.kt`
- Create: `studiodroid/app/src/main/assets/Install_Studio.bat`
- Create: `studiodroid/app/src/main/assets/Launch_Studio.bat`
- Create: `studiodroid/app/src/main/assets/Repair_Studio.bat`
- Test: `studiodroid/app/src/test/java/com/studiodroid/app/WorkspaceManagerTest.kt`

**Interfaces:**
- Consumes: `PeValidator.isPeExecutable`.
- Produces: workspace path helpers and `importStudioExe(input: InputStream): Result<File>` plus idempotent helper-file preparation.

- [ ] Write failing tests for successful import, invalid file preservation, replacement, and repeated helper preparation.
- [ ] Run tests and verify failure.
- [ ] Implement atomic workspace import and helper extraction.
- [ ] Run tests and verify pass.

### Task 3: Windows-layer detection and mobile UI

**Files:**
- Create: `studiodroid/app/src/main/java/com/studiodroid/app/WindowsLayerDetector.kt`
- Create: `studiodroid/app/src/main/java/com/studiodroid/app/MainActivity.kt`
- Create: `studiodroid/app/src/main/res/layout/activity_main.xml`
- Create: `studiodroid/app/src/main/res/values/strings.xml`
- Create: `studiodroid/app/src/main/res/values/colors.xml`
- Create: `studiodroid/app/src/main/res/values/themes.xml`
- Test: `studiodroid/app/src/test/java/com/studiodroid/app/WindowsLayerDetectorTest.kt`

**Interfaces:**
- Produces: detection of known Winlator-compatible package names and launch intent selection.
- UI actions: Import Studio EXE, Install Studio, Launch Studio, Repair, Open Files, Open Windows Layer.

- [ ] Write failing detector tests for supported, missing, and fallback package states.
- [ ] Run tests and verify failure.
- [ ] Implement detector and MainActivity state flow.
- [ ] Run unit tests and verify pass.

### Task 4: APK build workflow and verification

**Files:**
- Create: `.github/workflows/studiodroid-apk.yml`
- Create: `studiodroid/README.md`

**Interfaces:**
- Produces: GitHub Actions artifact `StudioDroid-v0.1-debug.apk` and build logs.

- [ ] Configure workflow on pushes to `studiodroid-build` with Java 17, Android/Gradle build, unit tests, and artifact upload.
- [ ] Run workflow and verify unit tests pass.
- [ ] Verify generated APK exists and can be downloaded as an artifact.
- [ ] Record build SHA and artifact name in README.
