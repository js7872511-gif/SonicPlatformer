# Chaos Edition 3.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a signed Android Chaos Edition 3.0 APK that combines the current Chaos platform features, Death/Berserker progression, 16 save slots, and an optional universal touch-button extension for ordinary S3AIR mods.

**Architecture:** Keep upstream Sonic 3 A.I.R. mod loading untouched. Patch the engine at build time with focused Python patchers: one for universal mod-declared touch buttons, one for two save banks, one for Death/Berserker core script changes, and one for Android identity. Compile patched LemonScript to `scripts.bin`, build the Android app, sign it, and verify the packaged script digest.

**Tech Stack:** Python patchers/unittest, C++ Oxygen Engine, LemonScript, CMake, Android Gradle/NDK, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-29-chaos-edition-3.0.md`

## Global Constraints
- Base on Sonic 3 A.I.R. `v26.03.28.0-stable`.
- Preserve ordinary S3AIR mod compatibility.
- No Water/Hyper Water/Super/Emerald/SLIP/Rush/Sky/Chaos ability pack in the core.
- Firey stays a separate mod.
- Android package ID is `com.chaosed.sonic3air.v30`.
- Output is `S3AIR_Chaos_Edition_3.0.apk`.

## Review Focus
- Mods without `chaos-buttons.json` must behave exactly as before.
- Extra buttons must not consume or overwrite Player 1 inputs unless the sidecar explicitly targets Controller 0.
- Switching save banks must not cross-write save data.
- Classic save bank must not inherit Chaos-bank infinite resources/completion.
- Death progression must persist/advance without triggering stock Game Over at zero lives.

---

### Task 1: Universal mod button patcher
**Files:**
- Create: `chaos-edition/patch_universal_buttons.py`
- Create: `chaos-edition/tests/test_universal_buttons_patch.py`

**Interfaces:**
- Consumes: active mod list and optional `<mod>/chaos-buttons.json`.
- Produces: touch areas mapped to requested virtual controller/control pairs.

- [ ] Write failing source-transform tests for sidecar parsing, controller/control mapping, mod-change rebuild hook, and absence of hardcoded Water/Emerald buttons.
- [ ] Implement engine patcher.
- [ ] Run tests green.

### Task 2: Death/Berserker core patcher
**Files:**
- Create: `chaos-edition/patch_death_berserker.py`
- Create: `chaos-edition/tests/test_death_berserker_patch.py`

**Interfaces:**
- Produces: LemonScript state/progression hooks for deaths 1/5/10/15 and Berserker 50-kill max state.

- [ ] Write failing tests for thresholds, negative-life continuation, ability names/upgrades, Berserker replacements, and 50-kill boss-breaker state.
- [ ] Implement minimal source patcher without rejected ability code.
- [ ] Run tests green.

### Task 3: Save-bank hardening for 3.0
**Files:**
- Modify: `chaos-edition/patch_save_banks.py`
- Modify: `chaos-edition/tests/test_savebanks_patch.py`

**Interfaces:**
- Bank 0 = eight completed Chaos saves.
- Bank 1 = eight stock-style replay saves.

- [ ] Update labels/version comments to 3.0 and preserve no-Super-Emerald default.
- [ ] Verify infinite resources are bank-0-only.
- [ ] Run tests green.

### Task 4: Android identity and build pipeline
**Files:**
- Modify: `chaos-edition/patch_android_identity.py`
- Create: `chaos-edition/build_v30_ci.sh`
- Create/Modify: `.github/workflows/build-chaos-v30.yml`

**Interfaces:**
- Produces signed `S3AIR_Chaos_Edition_3.0.apk` plus SHA-256 file.

- [ ] Run all Python patch tests first.
- [ ] Apply universal buttons, save banks, Death/Berserker, and CI ROM-memory patch.
- [ ] Compile patched LemonScript and verify patched `scripts.bin` differs from stock.
- [ ] Build arm64 release APK.
- [ ] Sign and run `apksigner verify` plus `unzip -t`.
- [ ] Verify packaged `assets/data/scripts.bin` digest matches the compiled patched script.
- [ ] Upload APK artifact.

### Task 5: End-to-end verification
**Files:**
- Create: `chaos-edition/examples/universal-buttons-demo/chaos-buttons.json`
- Create: `chaos-edition/UNIVERSAL_BUTTONS.md`

- [ ] Verify example sidecar parses and targets secondary virtual controller inputs.
- [ ] Verify no rejected Water/Emerald hardcoded controls remain in the 3.0 patch path.
- [ ] Verify GitHub Actions build completes and artifact is downloadable.
