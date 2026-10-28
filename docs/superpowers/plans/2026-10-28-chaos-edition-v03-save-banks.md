# Chaos Edition v0.3 Save Banks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship an Android Chaos Edition v0.3 APK with two banks of eight save slots: eight always-complete Chaos saves with infinite lives/rings and eight normal replay saves with stock progression.

**Architecture:** Keep Sonic 3 A.I.R.'s original eight-slot RAM/UI layout and page between two persistent banks rather than rewriting Data Select. Bank 0 is normalized to completed/all-Chaos-Emerald state; bank 1 uses ordinary empty/normal saves. Rebuild `scripts.bin` from the patched LemonScript source, then package it into the Android APK alongside the existing Chaos touch-control patch.

**Tech Stack:** Python patch tooling/tests, LemonScript, Sonic 3 A.I.R. C++/CMake host build, Android Gradle/NDK.

**Spec:** Current conversation requirements: scroll between two sets of eight saves; first eight are 100% with infinite lives/rings; second eight play the normal game.

## Global Constraints

- Base on Sonic 3 A.I.R. `v26.03.28.0-stable`.
- Preserve normal Sonic 3 A.I.R. mod compatibility.
- Preserve Chaos Edition v0.2 custom touch controls.
- Bank 0 uses all seven Chaos Emeralds but no Super Emeralds.
- Bank 1 must not inherit infinite resources or completed progress.
- Build arm64-v8a Android APK and keep it installable separately from v0.2.

## Review Focus

- Switching banks must not write Bank 0 data into Bank 1 or vice versa.
- Deleting/creating a Bank 1 save must remain persistent.
- Bank 0 must re-normalize to completed/all-Chaos-Emerald state.
- Infinite ring/life enforcement must apply only while playing a Bank 0 save.
- The regenerated `scripts.bin` must come from patched source, not the stock APK.

---

### Task 1: Save-bank patcher and tests

**Files:**
- Create: `chaos-edition/patch_save_banks.py`
- Create: `chaos-edition/tests/test_savebanks_patch.py`

**Interfaces:**
- Produces: patched `saveslots.lemon`, `dataselect.lemon`, and `character.lemon`.

- [ ] Write tests for bank persistence keys, 100% defaults, edge switching, labels, and infinite-resource hooks.
- [ ] Run tests and verify they fail before the patcher exists.
- [ ] Implement the patcher.
- [ ] Run tests and verify they pass.

### Task 2: Rebuild scripts.bin in CI

**Files:**
- Modify: `.github/workflows/build-chaos-edition.yml`

**Interfaces:**
- Consumes: patched LemonScript source from Task 1.
- Produces: regenerated `assets/data/scripts.bin` compiled from the patched source.

- [ ] Run patcher tests in CI.
- [ ] Apply touch-control and save-bank patches to the stable source checkout.
- [ ] Build the Linux development executable and run it headlessly to regenerate `saves/scripts.bin`.
- [ ] Replace the stock Android `scripts.bin` with the regenerated file and verify it differs from stock.

### Task 3: Build and verify v0.3 APK

**Files:**
- Modify: `.github/workflows/build-chaos-edition.yml`

**Interfaces:**
- Consumes: regenerated script asset and existing touch patch.
- Produces: signed `S3AIR_Chaos_Edition_v0.3.apk`.

- [ ] Configure v0.3 package identity and arm64 ABI.
- [ ] Build release APK.
- [ ] Sign and verify the APK.
- [ ] Inspect packaged assets and verify the regenerated `scripts.bin` digest is present.
- [ ] Upload the APK artifact.
