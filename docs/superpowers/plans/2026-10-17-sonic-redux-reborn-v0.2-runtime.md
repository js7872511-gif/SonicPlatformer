# Sonic Redux Re-born v0.2 Runtime + Super Sonic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Re-born from APK file overlays to a runtime-capable Android mod platform for the supplied Sonic Redux build, then ship the first built-in runtime mod: a toggleable Super Sonic transformation with seven 3D Chaos Emeralds, Super-only effects, Flight, Super Boost, and Chaos Burst.

**Architecture:** Keep the proven v0.1.2 manager/build pipeline and add an embedded runtime payload. The manager validates the exact supplied Redux build, renames the original Unity `libmain.so`, injects a tiny wrapper `libmain.so` plus `libreborn.so` for ARM64 and ARMv7, inserts a small runtime UI DEX, preserves the APK storage/alignment invariants, signs the rebuilt game, and installs it as the separate Re-born package. The native runtime waits for IL2CPP, resolves supported Unity/Redux capabilities dynamically, exposes a narrow bridge, and runs the Super Sonic state machine; the injected Android overlay owns touch buttons and forwards button events to native code.

**Tech Stack:** Java 17, Android Gradle Plugin 8.3.0, Gradle 8.9, Android SDK 35, Android NDK/CMake C++17, JNI, `dlopen`/`dlsym`, Unity IL2CPP exported API, JUnit 4.13.2, Android `apksig` 4.4.0, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-17-sonic-redux-reborn-v0.2-runtime-design.md`

## Global Constraints

- Manager package remains `com.sonicredux.reborn`; rebuilt game package remains `com.RebornXTeams.SonicRedux`.
- `minSdk 23`, `targetSdk 35`, and both `arm64-v8a` + `armeabi-v7a` remain supported.
- Runtime API version is exactly `2.0`.
- v0.1 file-overlay mods remain supported.
- Runtime support targets the user's supplied Sonic Redux APK first and fails closed for other builds.
- Runtime injection never modifies the original installed Redux package in place.
- Super Sonic is a toggle; all seven Chaos Emeralds are always owned; there is no ring requirement, ring drain, or emerald collection requirement.
- Super-only abilities for v0.2 are exactly `FLIGHT`, `SUPER BOOST`, and `CHAOS BURST`.
- No root access, external memory editor, general scripting engine, arbitrary native plugin loader, or online/network gameplay modification.
- Preserve the v0.1.2 fix: `resources.arsc` must remain STORED and 4-byte aligned, and APK signatures must verify before delivery.

## Review Focus

- **Wrong Redux APK / subtly different build:** runtime injection must be rejected before replacing native libraries; Task 1 adds fingerprint rejection tests.
- **Repeated Build / partially injected APK:** injection must be deterministic and idempotent for a clean baseline, never produce duplicate DEX/native payload entries; Task 4 adds duplicate-entry tests.
- **Runtime starts before Unity/IL2CPP is ready:** initialization must wait/retry with a bounded state machine rather than dereference missing symbols; Task 5 adds resolver/lifecycle tests.
- **Player disappears during scene load while Super:** all transient handles must be invalidated and Super state restored only after a new valid player is acquired; Task 7 adds scene-loss/reacquire tests.
- **Rapid taps on GO SUPER / ability buttons:** transforming states ignore conflicting input and cleanup remains idempotent; Task 7 adds transition and duplicate-toggle tests.

---

### Task 1: Supported Redux Build Profile

**Files:**
- Create: `reborn-android/app/src/main/java/com/sonicredux/reborn/runtime/ReduxBuildProfile.java`
- Create: `reborn-android/app/src/test/java/com/sonicredux/reborn/runtime/ReduxBuildProfileTest.java`
- Modify: `reborn-android/app/src/main/java/com/sonicredux/reborn/MainActivity.java`

**Interfaces:**
- Consumes: selected clean Redux APK as a `File`.
- Produces: `ReduxBuildProfile.ValidationResult ReduxBuildProfile.validate(File apk)` with `supported`, `profileId`, and `reason` fields; `ReduxBuildProfile.SUPPLIED_REDUX_V1` is the only supported profile in v0.2.

- [ ] **Step 1: Write the failing fingerprint tests**

Add tests named `acceptsSuppliedReduxFingerprint`, `rejectsWrongMetadataHash`, and `rejectsMissingRequiredAbi`. The accepted profile must pin these supplied-APK values:

```text
lib/arm64-v8a/libmain.so                      6728 bytes   sha256 7de2b8bbbdaae62d05592f3ab74f03de6372758dda47554094121298363f5cc8
lib/arm64-v8a/libil2cpp.so                58503712 bytes   sha256 ee5ee8c44d14fedf5788dd3d6b2521cb1159ba2c4b19fbd2d82c92ea1001fd38
lib/armeabi-v7a/libmain.so                    8588 bytes   sha256 10b9a628813d91d830225c3c5a12346af6c598973a222ccdc8c59974dc812161
lib/armeabi-v7a/libil2cpp.so               48633732 bytes   sha256 355c0b14f7affaf8798cdf36ffc632363a0a744ead9ce42770cb6ac5112db3e0
assets/bin/Data/Managed/Metadata/global-metadata.dat 8295240 bytes sha256 7518bd2682b1e125a8b10e0e2bddb56485a3353378764ebe43df394a03a779b1
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `cd reborn-android && gradle testDebugUnitTest --tests '*ReduxBuildProfileTest' --stacktrace`

Expected: FAIL because `ReduxBuildProfile` does not exist.

- [ ] **Step 3: Implement `ReduxBuildProfile.validate(File apk)`**

Use streaming SHA-256 over the named ZIP entries, compare both size and digest, and return a human-readable rejection reason naming the first mismatched entry. Do not hash `data.unity3d` in normal validation because the IL2CPP + metadata fingerprints already identify this supplied build while avoiding an extra ~77 MB hash pass on the phone.

- [ ] **Step 4: Gate runtime builds in `MainActivity.buildAndInstall()`**

Before runtime injection, call `ReduxBuildProfile.validate(base)`. Unsupported builds must show `Runtime unsupported for this Redux APK: <reason>` and stop before producing a signed output; ordinary v0.1 overlay-only behavior remains available through a runtime-disabled build option introduced in Task 4.

- [ ] **Step 5: Run tests**

Run: `cd reborn-android && gradle testDebugUnitTest --stacktrace`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add reborn-android/app/src/main/java/com/sonicredux/reborn/runtime/ReduxBuildProfile.java \
        reborn-android/app/src/test/java/com/sonicredux/reborn/runtime/ReduxBuildProfileTest.java \
        reborn-android/app/src/main/java/com/sonicredux/reborn/MainActivity.java
git commit -m "feat: validate supported Redux runtime build"
```

### Task 2: Runtime Payload Module and `libmain` Wrapper

**Files:**
- Modify: `reborn-android/settings.gradle`
- Create: `reborn-android/runtime-payload/build.gradle`
- Create: `reborn-android/runtime-payload/src/main/AndroidManifest.xml`
- Create: `reborn-android/runtime-payload/src/main/cpp/CMakeLists.txt`
- Create: `reborn-android/runtime-payload/src/main/cpp/main_wrapper.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/reborn_runtime.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/reborn_runtime.h`
- Create: `reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java`
- Create: `reborn-android/runtime-payload/src/test/java/com/sonicredux/reborn/runtimeui/RebornOverlayStateTest.java`

**Interfaces:**
- Produces native exports: `jint JNI_OnLoad(JavaVM*, void*)` from wrapper `libmain.so`; `int reborn_runtime_start(JavaVM*)` from `libreborn.so`.
- Produces Java class: `com.sonicredux.reborn.runtimeui.RebornOverlay` with static `install(Activity)`, `setRuntimeStatus(String)`, `setSuperState(boolean, boolean)`, and native callbacks `nativeToggleSuper()`, `nativeToggleFlight()`, `nativeSuperBoost()`, `nativeChaosBurst()`.

- [ ] **Step 1: Add the runtime-payload module and failing overlay-state test**

The test verifies that normal state exposes only `GO SUPER`, Super state exposes `GO SUPER`, `FLIGHT`, `SUPER BOOST`, and `CHAOS BURST`, and transforming state disables conflicting presses.

- [ ] **Step 2: Run the payload unit test and verify RED**

Run: `cd reborn-android && gradle :runtime-payload:testDebugUnitTest --stacktrace`

Expected: FAIL because the overlay/state helpers do not exist yet.

- [ ] **Step 3: Implement the Java overlay**

`RebornOverlay.install(Activity)` adds one transparent `FrameLayout` to the activity decor view on the UI thread. Use Android `Button` controls with large touch targets. `GO SUPER` is always visible when the mod is enabled; the three ability buttons are visible only in Super state. Button listeners call the four native methods. Keep layout constants isolated in the overlay file so positions can be tuned after phone testing.

- [ ] **Step 4: Implement wrapper `JNI_OnLoad`**

The wrapper named `libmain.so` must `dlopen("libmain_redux.so", RTLD_NOW | RTLD_GLOBAL)`, resolve and call the renamed original `JNI_OnLoad`, then `dlopen("libreborn.so", RTLD_NOW | RTLD_GLOBAL)` and call `reborn_runtime_start(vm)`. If Re-born runtime loading fails, return the original Redux `JNI_OnLoad` result so the game can continue without mods.

- [ ] **Step 5: Implement minimal `libreborn.so` startup**

`reborn_runtime_start(JavaVM*)` must be idempotent, store the VM, spawn one initialization thread, and initially do nothing except wait for Unity activity availability and post `REBORN RUNTIME ONLINE` through `RebornOverlay.setRuntimeStatus` once the Java overlay is installed.

- [ ] **Step 6: Build both ABIs**

Configure ABI filters exactly `arm64-v8a`, `armeabi-v7a`; use C++17. Run: `cd reborn-android && gradle :runtime-payload:assembleRelease --stacktrace`

Expected: AAR contains both `jni/arm64-v8a/{libmain.so,libreborn.so}` and `jni/armeabi-v7a/{libmain.so,libreborn.so}`.

- [ ] **Step 7: Run payload tests**

Run: `cd reborn-android && gradle :runtime-payload:testDebugUnitTest --stacktrace`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add reborn-android/settings.gradle reborn-android/runtime-payload
git commit -m "feat: add Re-born native runtime payload"
```

### Task 3: Build and Embed the Runtime DEX + Native Payload in the Manager

**Files:**
- Modify: `reborn-android/app/build.gradle`
- Create: `reborn-android/tools/prepare-runtime-payload.gradle`
- Create: `reborn-android/app/src/main/java/com/sonicredux/reborn/runtime/RuntimePayload.java`
- Create: `reborn-android/app/src/test/java/com/sonicredux/reborn/runtime/RuntimePayloadTest.java`

**Interfaces:**
- Produces manager assets:
  - `reborn-runtime/classes.dex`
  - `reborn-runtime/arm64-v8a/libmain.so`
  - `reborn-runtime/arm64-v8a/libreborn.so`
  - `reborn-runtime/armeabi-v7a/libmain.so`
  - `reborn-runtime/armeabi-v7a/libreborn.so`
- Produces `RuntimePayload.open(Context, String path) -> InputStream` and `RuntimePayload.requiredPaths() -> List<String>`.

- [ ] **Step 1: Write failing asset-manifest test**

`RuntimePayloadTest.requiredPathsAreComplete` asserts the exact five payload paths above and rejects duplicate paths.

- [ ] **Step 2: Run and verify RED**

Run: `cd reborn-android && gradle testDebugUnitTest --tests '*RuntimePayloadTest' --stacktrace`

Expected: FAIL because `RuntimePayload` does not exist.

- [ ] **Step 3: Add `prepareRuntimePayload` Gradle task**

The task depends on `:runtime-payload:assembleRelease`, extracts the AAR `classes.jar`, runs Android build-tools `d8` to one `classes.dex`, copies both ABI native libraries, and writes all outputs under a generated assets directory included by `app.sourceSets.main.assets`.

- [ ] **Step 4: Implement `RuntimePayload`**

Use `AssetManager.open("reborn-runtime/...", ACCESS_STREAMING)` and expose only the fixed allowlisted payload paths.

- [ ] **Step 5: Make manager build depend on payload preparation**

`preBuild` must depend on `prepareRuntimePayload`; no generated binary payload is committed to Git.

- [ ] **Step 6: Verify manager APK contains the payload assets**

Run: `cd reborn-android && gradle assembleDebug --stacktrace && unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'assets/reborn-runtime/'`

Expected: all five paths present.

- [ ] **Step 7: Run unit tests and commit**

Run: `cd reborn-android && gradle testDebugUnitTest --stacktrace`

```bash
git add reborn-android/app/build.gradle reborn-android/tools/prepare-runtime-payload.gradle \
        reborn-android/app/src/main/java/com/sonicredux/reborn/runtime/RuntimePayload.java \
        reborn-android/app/src/test/java/com/sonicredux/reborn/runtime/RuntimePayloadTest.java
git commit -m "build: embed Re-born runtime payload"
```

### Task 4: Deterministic Runtime APK Injection

**Files:**
- Create: `reborn-android/app/src/main/java/com/sonicredux/reborn/runtime/RuntimeInjector.java`
- Create: `reborn-android/app/src/test/java/com/sonicredux/reborn/runtime/RuntimeInjectorTest.java`
- Modify: `reborn-android/app/src/main/java/com/sonicredux/reborn/core/ZipOverlayBuilder.java`
- Modify: `reborn-android/app/src/test/java/com/sonicredux/reborn/core/ZipOverlayBuilderTest.java`
- Modify: `reborn-android/app/src/main/java/com/sonicredux/reborn/MainActivity.java`

**Interfaces:**
- Consumes: overlay-built unsigned Re-born APK plus `RuntimePayload`.
- Produces: `void RuntimeInjector.inject(Context context, File inputApk, File outputApk)`.
- Injected APK layout per ABI:
  - original `lib/<abi>/libmain.so` -> `lib/<abi>/libmain_redux.so`
  - payload wrapper -> `lib/<abi>/libmain.so`
  - payload runtime -> `lib/<abi>/libreborn.so`
  - payload DEX -> next free `classesN.dex`

- [ ] **Step 1: Write failing injection tests**

Tests must assert: both original `libmain.so` files are preserved as `libmain_redux.so`; wrapper/runtime files exist for both ABIs; DEX uses the next free index; original signatures are absent; a second injection attempt on an already injected fixture is rejected with `APK already contains Re-born runtime`; and `resources.arsc` stays STORED.

- [ ] **Step 2: Run and verify RED**

Run: `cd reborn-android && gradle testDebugUnitTest --tests '*RuntimeInjectorTest' --stacktrace`

Expected: FAIL.

- [ ] **Step 3: Implement `RuntimeInjector.inject(...)`**

Reuse the ZIP-writing/storage/alignment helpers already proven in `ZipOverlayBuilder`; do not create a second incompatible ZIP implementation. For native `.so` entries preserve STORED mode and alignment required by Android. Choose the first free DEX name from `classes3.dex` upward after inspecting existing entries.

- [ ] **Step 4: Refactor only the shared ZIP-entry writer needed by both builders**

Extract a package-private helper from `ZipOverlayBuilder` if necessary; keep its existing public `build(...)` signature so v0.1 mod behavior is unchanged.

- [ ] **Step 5: Wire manager build stages**

The build order becomes: validate baseline -> apply v0.1 overlays/package rename -> runtime inject -> sign -> promote `last-successful.apk` -> installer. Add a manager toggle labeled `ENABLE V0.2 RUNTIME` defaulting ON; when OFF, preserve overlay-only v0.1 behavior even if the Redux runtime fingerprint does not match.

- [ ] **Step 6: Run all JVM tests**

Run: `cd reborn-android && gradle testDebugUnitTest --stacktrace`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add reborn-android/app/src/main/java/com/sonicredux/reborn/runtime/RuntimeInjector.java \
        reborn-android/app/src/test/java/com/sonicredux/reborn/runtime/RuntimeInjectorTest.java \
        reborn-android/app/src/main/java/com/sonicredux/reborn/core/ZipOverlayBuilder.java \
        reborn-android/app/src/test/java/com/sonicredux/reborn/core/ZipOverlayBuilderTest.java \
        reborn-android/app/src/main/java/com/sonicredux/reborn/MainActivity.java
git commit -m "feat: inject Re-born runtime into rebuilt APK"
```

### Task 5: IL2CPP Resolver, Runtime Diagnostics, and First Phone-Proof Build

**Files:**
- Create: `reborn-android/runtime-payload/src/main/cpp/il2cpp_api.h`
- Create: `reborn-android/runtime-payload/src/main/cpp/il2cpp_resolver.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/il2cpp_resolver.h`
- Create: `reborn-android/runtime-payload/src/main/cpp/runtime_status.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/runtime_status.h`
- Modify: `reborn-android/runtime-payload/src/main/cpp/reborn_runtime.cpp`
- Modify: `reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java`
- Modify: `.github/workflows/reborn-apk.yml`

**Interfaces:**
- Produces `Il2CppResolver::waitUntilReady(timeoutMs)` and `RuntimeStatus` states: `STARTING`, `IL2CPP_READY`, `BRIDGE_READY`, `DISABLED`.
- Required resolver symbols include `il2cpp_domain_get`, `il2cpp_domain_get_assemblies`, `il2cpp_assembly_get_image`, `il2cpp_image_get_name`, `il2cpp_class_from_name`, `il2cpp_class_get_method_from_name`, `il2cpp_runtime_invoke`, `il2cpp_object_unbox`, and field/property helpers used by later bridge tasks.

- [ ] **Step 1: Add host-testable resolver state tests**

Create a fake symbol provider so tests cover missing `libil2cpp.so`, missing required export, delayed readiness, and idempotent successful initialization without loading a real Android library.

- [ ] **Step 2: Run native host tests and verify RED**

Add a CMake host-test target `reborn_runtime_tests`; run from CI with `cmake -S runtime-payload/src/main/cpp -B /tmp/reborn-host -DREBORN_HOST_TESTS=ON && cmake --build /tmp/reborn-host && ctest --test-dir /tmp/reborn-host --output-on-failure`.

Expected: FAIL before resolver implementation.

- [ ] **Step 3: Implement dynamic IL2CPP resolution**

Use `dlopen("libil2cpp.so", RTLD_NOW | RTLD_NOLOAD)` in a bounded retry loop. Resolve only the explicit allowlist required by the runtime. A missing symbol sets `DISABLED` with a diagnostic string and performs no game writes.

- [ ] **Step 4: Implement visible diagnostics**

Overlay status text shows `REBORN RUNTIME ONLINE` after IL2CPP is ready, plus compact internal status values for ABI, profile match, IL2CPP ready, bridge ready, Super mod loaded, and last error. Keep this visible in test builds only.

- [ ] **Step 5: Extend GitHub Actions**

CI must build `:runtime-payload:assembleRelease`, run host native tests, run JVM tests, build manager, verify embedded payload paths, verify manager v1/v2 signing, and package artifact as `Sonic_Redux_Re-born_v0.2-test.apk`.

- [ ] **Step 6: Verify CI locally as far as available**

Run the host native tests, `gradle testDebugUnitTest`, and `gradle assembleDebug`.

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add reborn-android/runtime-payload/src/main/cpp \
        reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java \
        .github/workflows/reborn-apk.yml
git commit -m "feat: initialize IL2CPP runtime with diagnostics"
```

- [ ] **Step 8: Phone validation checkpoint**

Build/install from the user's clean Redux APK with Super Sonic still disabled. Success criteria: separate Re-born game installs, launches, reaches the game normally, and visibly reports `REBORN RUNTIME ONLINE`. If startup fails, stop here and diagnose before adding gameplay hooks.

### Task 6: Redux Bridge and Generic Unity Object Operations

**Files:**
- Create: `reborn-android/runtime-payload/src/main/cpp/redux_bridge.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/redux_bridge.h`
- Create: `reborn-android/runtime-payload/src/main/cpp/unity_bridge.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/unity_bridge.h`
- Create: `reborn-android/runtime-payload/src/main/cpp/tests/redux_bridge_test.cpp`
- Modify: `reborn-android/runtime-payload/src/main/cpp/reborn_runtime.cpp`

**Interfaces:**
- Produces `ReduxBridge::acquirePlayer()`, `ReduxBridge::invalidateScene()`, `ReduxBridge::setInvincible(bool)`, `ReduxBridge::setSpeedMultiplier(float)`, and `ReduxBridge::setFlight(bool)`.
- Produces generic Unity helpers for finding player object, creating/destroying GameObjects, reading/writing Transform state, creating primitive meshes, setting renderer/material color, and tracking runtime-owned objects.

- [ ] **Step 1: Write bridge failure-mode tests**

Tests assert that missing player returns `Unavailable` without writes, scene invalidation clears cached object handles, repeated cleanup is safe, and optional capabilities report unavailable individually rather than disabling the whole runtime.

- [ ] **Step 2: Run native tests and verify RED**

Run the host CMake/CTest command from Task 5.

Expected: FAIL.

- [ ] **Step 3: Implement Unity generic operations through IL2CPP metadata/method discovery**

Resolve Unity assemblies/classes/methods by assembly + namespace + class + method name, never by absolute address. The first player acquisition strategy is `UnityEngine.GameObject.FindWithTag("Player")`; if the supplied Redux scene does not use that tag, expose a diagnostic candidate scan rather than guessing a pointer.

- [ ] **Step 4: Implement Redux capability discovery**

Use the supplied build profile to search `Assembly-CSharp` for the known gameplay-member candidates exposed by its metadata, including `OnInvincible`, `invincibility`, `speedShoes`, `selfSpeed`, `originalSpeed`, `topSpeed`, `airSpeed`, and `groundSpeed`. Store resolved class/member identities in diagnostics; do not perform a write until the member type and owning player/controller instance are verified.

- [ ] **Step 5: Implement bridge operations with fail-closed fallbacks**

`setInvincible`, `setSpeedMultiplier`, and `setFlight` return a capability result. Unsupported capabilities leave the game unchanged and mark the corresponding Super ability unavailable.

- [ ] **Step 6: Run native tests and commit**

Run CTest; expected PASS.

```bash
git add reborn-android/runtime-payload/src/main/cpp/redux_bridge.* \
        reborn-android/runtime-payload/src/main/cpp/unity_bridge.* \
        reborn-android/runtime-payload/src/main/cpp/tests/redux_bridge_test.cpp \
        reborn-android/runtime-payload/src/main/cpp/reborn_runtime.cpp
git commit -m "feat: add supported Redux runtime bridge"
```

### Task 7: Super Sonic State Machine, Emerald Transformation, and Cleanup

**Files:**
- Create: `reborn-android/runtime-payload/src/main/cpp/super_sonic.cpp`
- Create: `reborn-android/runtime-payload/src/main/cpp/super_sonic.h`
- Create: `reborn-android/runtime-payload/src/main/cpp/tests/super_sonic_test.cpp`
- Modify: `reborn-android/runtime-payload/src/main/cpp/reborn_runtime.cpp`
- Modify: `reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java`

**Interfaces:**
- Produces `SuperSonicController` states `NORMAL`, `TRANSFORMING_IN`, `SUPER`, `TRANSFORMING_OUT` and methods `toggle()`, `update(float dt)`, `onSceneLost()`, `onPlayerReady()`, `disable()`.
- Consumes `ReduxBridge` and `UnityBridge` from Task 6.

- [ ] **Step 1: Write state-machine tests**

Tests assert: `NORMAL -> TRANSFORMING_IN -> SUPER`; Super toggle returns through `TRANSFORMING_OUT -> NORMAL`; rapid taps during either transform are ignored; scene loss destroys all seven emerald handles and transient effects; repeated cleanup is safe; player reacquisition in desired-Super mode reapplies only stable Super state.

- [ ] **Step 2: Run native tests and verify RED**

Run CTest; expected FAIL.

- [ ] **Step 3: Implement seven procedural Chaos Emeralds**

Create seven runtime-owned primitive/mesh objects, one each red/blue/yellow/green/cyan/purple/white. Arrange them around the player, orbit with deterministic phase offsets, accelerate during `TRANSFORMING_IN`, converge toward the player, then hide/destroy them before entering `SUPER`. Do not package copied game model assets.

- [ ] **Step 4: Implement reversible Super visuals**

Track every changed material/value so toggle-off restores the exact pre-Super state. Apply gold/yellow treatment, aura/glow object(s), and speed trail only through verified bridge operations. If a visual capability is unavailable, continue with the remaining transformation rather than crashing.

- [ ] **Step 5: Implement stable Super state**

On entering `SUPER`, request invincibility and enhanced movement from `ReduxBridge`, then call `RebornOverlay.setSuperState(true, false)`. On exit, restore original values before removing runtime-owned objects and call `setSuperState(false, false)`.

- [ ] **Step 6: Run tests and commit**

Run CTest; expected PASS.

```bash
git add reborn-android/runtime-payload/src/main/cpp/super_sonic.* \
        reborn-android/runtime-payload/src/main/cpp/tests/super_sonic_test.cpp \
        reborn-android/runtime-payload/src/main/cpp/reborn_runtime.cpp \
        reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java
git commit -m "feat: add Super Sonic transformation runtime mod"
```

### Task 8: Flight, Super Boost, and Chaos Burst

**Files:**
- Modify: `reborn-android/runtime-payload/src/main/cpp/super_sonic.cpp`
- Modify: `reborn-android/runtime-payload/src/main/cpp/super_sonic.h`
- Modify: `reborn-android/runtime-payload/src/main/cpp/tests/super_sonic_test.cpp`
- Modify: `reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java`

**Interfaces:**
- Adds `toggleFlight()`, `superBoost()`, and `chaosBurst()` to `SuperSonicController`.
- Native JNI callbacks in `RebornOverlay` dispatch only when controller state is `SUPER`.

- [ ] **Step 1: Write ability-gating tests**

Assert all three abilities are rejected outside `SUPER`; Flight is a toggle and is forcibly cleared on transform-out/scene loss; Super Boost is rate-limited so duplicate taps in the same cooldown window do not stack; Chaos Burst cleans up its effect objects after the configured short lifetime.

- [ ] **Step 2: Run tests and verify RED**

Run CTest; expected FAIL.

- [ ] **Step 3: Implement Flight**

Use the verified Redux flight/gravity capability when present. If that capability is absent, hide/disable the Flight button and report `Flight unavailable on this scene/build`; do not emulate flight with unsafe raw transform writes.

- [ ] **Step 4: Implement Super Boost**

Apply a short, stronger speed multiplier/impulse through the verified movement bridge plus a distinct runtime-owned trail/flash; restore the ordinary Super multiplier automatically after the boost window.

- [ ] **Step 5: Implement Chaos Burst**

Create a short-lived expanding visual burst centered on Sonic. If a verified nearby-enemy/damage method is discovered in the supported Redux bridge, invoke it through a typed capability; otherwise keep Chaos Burst visual-only in the first phone build and report the damage capability as unavailable rather than guessing a damage call.

- [ ] **Step 6: Run tests and commit**

Run CTest; expected PASS.

```bash
git add reborn-android/runtime-payload/src/main/cpp/super_sonic.* \
        reborn-android/runtime-payload/src/main/cpp/tests/super_sonic_test.cpp \
        reborn-android/runtime-payload/src/main/java/com/sonicredux/reborn/runtimeui/RebornOverlay.java
git commit -m "feat: add Super Sonic runtime abilities"
```

### Task 9: v0.2 Manager UX, CI Verification, and Android Delivery

**Files:**
- Modify: `reborn-android/app/src/main/java/com/sonicredux/reborn/MainActivity.java`
- Modify: `reborn-android/app/build.gradle`
- Modify: `.github/workflows/reborn-apk.yml`
- Create: `reborn-android/README-v0.2.md`

**Interfaces:**
- Manager version becomes `versionCode 3`, `versionName '0.2.0'`.
- Manager status exposes runtime enabled/disabled and supported-build state before build.

- [ ] **Step 1: Update manager version and status copy**

The UI must distinguish `Clean Redux base ready`, `Runtime: supported / unsupported / disabled`, and enabled overlay-mod count. Keep the existing select/import/mods/build/play workflow recognizable.

- [ ] **Step 2: Add final CI checks**

After manager APK build, verify package/label, v1+v2 signatures, embedded runtime payload assets, both ABI libraries, and that no generated binary payload was accidentally committed. Keep existing static checks.

- [ ] **Step 3: Run complete verification suite**

Run:

```bash
cd reborn-android
gradle :runtime-payload:testDebugUnitTest testDebugUnitTest --stacktrace
cmake -S runtime-payload/src/main/cpp -B /tmp/reborn-host -DREBORN_HOST_TESTS=ON
cmake --build /tmp/reborn-host
ctest --test-dir /tmp/reborn-host --output-on-failure
gradle assembleDebug --stacktrace
```

Expected: all tests/builds PASS.

- [ ] **Step 4: Verify built manager artifact**

Use latest Android build tools `aapt` + `apksigner`; expected package `com.sonicredux.reborn`, label `Sonic Redux Re-born`, v1 `true`, v2 `true`. Confirm the APK contains the five `assets/reborn-runtime/` payload entries.

- [ ] **Step 5: Commit final v0.2 packaging changes**

```bash
git add reborn-android/app/src/main/java/com/sonicredux/reborn/MainActivity.java \
        reborn-android/app/build.gradle .github/workflows/reborn-apk.yml reborn-android/README-v0.2.md
git commit -m "release: prepare Re-born v0.2 runtime test build"
```

- [ ] **Step 6: Phone validation in staged order**

Validate on the user's Android device in this order and stop on the first failure: install/launch -> `REBORN RUNTIME ONLINE` -> GO SUPER toggle -> seven emerald orbit/converge -> Super visual state -> Flight -> Super Boost -> Chaos Burst -> toggle back to normal -> change scene and repeat.

- [ ] **Step 7: Deliver using the proven ZIP-wrapped APK route**

Download the successful GitHub Actions artifact, verify its SHA-256 locally, place the APK inside `SonicReduxReborn_020_Extract_Me.zip`, and provide that ZIP. This avoids the direct-APK handoff problem previously observed in the ChatGPT Android app.
