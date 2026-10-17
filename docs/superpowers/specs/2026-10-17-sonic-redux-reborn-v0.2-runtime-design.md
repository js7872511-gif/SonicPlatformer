# Sonic Redux Re-born v0.2 Runtime + Super Sonic Design

## Goal

Extend the already-working Sonic Redux Re-born Android manager from file-overlay mods into a runtime-capable mod platform for the user's supplied Sonic Redux APK. The first runtime mod is Super Sonic.

Success means the rebuilt Re-born game still installs and launches on the user's Android phone, the runtime reports that it loaded successfully, and Super Sonic can be toggled in-game without requiring rings or collecting Chaos Emeralds.

## Scope

### Re-born v0.2 runtime

- Keep v0.1 file-overlay mods working.
- Add a lightweight native runtime to rebuilt Re-born APKs.
- Support both `arm64-v8a` and `armeabi-v7a`, matching the supplied Redux APK.
- Support the user's exact Redux build first; reject or disable runtime features on unrecognized builds instead of guessing offsets.
- Load only inside the rebuilt Re-born package, never modify the original installed Redux app in place.
- Add a temporary `REBORN RUNTIME ONLINE` diagnostic during early v0.2 testing.

### First runtime mod: Super Sonic

- Always considers all seven Chaos Emeralds already owned.
- No ring requirement, ring drain, or collection requirement.
- Add a mobile `GO SUPER` toggle button.
- On toggle-on:
  1. Spawn seven 3D Chaos Emeralds around Sonic.
  2. Orbit them during a short transformation sequence.
  3. Pull the emeralds into Sonic and hide them.
  4. Enter Super state with gold/yellow visuals, glow/aura, and enhanced movement.
  5. Show Super-only mobile ability buttons.
- On toggle-off:
  1. Remove Super-only abilities/effects.
  2. Restore normal Sonic appearance and movement values.
  3. Return to the ordinary player state without requiring a level reload.

### Super-only abilities

Initial v0.2 ships with three focused abilities:

- `FLIGHT`: toggle controlled flight while Super.
- `SUPER BOOST`: a stronger, faster boost with distinct effects.
- `CHAOS BURST`: a short-range area burst/effect around Super Sonic.

No additional ability system is required for v0.2.

## Architecture

### 1. Android manager/build layer

The existing Re-born manager remains responsible for selecting the clean Redux APK, importing mods, building a separate Re-born APK, signing it, and opening Android's installer.

The v0.2 build pipeline adds one new stage before signing:

1. Validate the clean Redux baseline against the supported build fingerprint.
2. Apply ordinary v0.1 ZIP overlays.
3. Insert Re-born runtime native libraries for both supported ABIs.
4. Insert runtime configuration and enabled runtime-mod descriptors.
5. Patch the rebuilt APK so the runtime is loaded during game startup.
6. Preserve Android APK storage/alignment rules already fixed in v0.1.2.
7. Sign and install as the separate Re-born package.

### 2. Native Re-born Runtime

A small `libreborn` runtime is built for ARM64 and ARMv7. It runs only in the Re-born game process.

Responsibilities:

- Wait until Unity/IL2CPP is initialized.
- Resolve required IL2CPP exports dynamically at runtime.
- Discover the exact classes/methods/fields required by the Redux bridge.
- Expose a deliberately small internal API to runtime mods.
- Own runtime-mod lifecycle: load, update, scene change, disable, shutdown.
- Fail closed when required symbols or Redux targets are missing.

The runtime must not rely on hard-coded absolute memory addresses. Build-specific metadata/signatures may be used only after the clean APK has matched the supported Redux fingerprint.

### 3. Redux bridge

The Redux bridge isolates game-specific knowledge from the generic runtime. It provides typed operations such as:

- get current player object
- read/write supported movement values
- set/clear invincibility state
- request supported visual/material changes
- spawn/attach runtime objects near the player
- register/remove mobile touch buttons
- receive a per-frame/update callback
- handle scene/player recreation safely

If a requested bridge capability cannot be resolved for the supported build, that capability reports unavailable instead of performing an unsafe fallback.

### 4. Runtime mod interface

v0.2 uses a small built-in interface rather than a general-purpose scripting language. This keeps the first release focused and avoids shipping a large interpreter/runtime.

Each runtime mod has:

- ID and version
- required runtime API version
- enable/disable lifecycle
- optional update callback
- optional scene/player callbacks
- declared capabilities

Super Sonic is the first built-in runtime mod and is used to prove the API before opening it to more mods.

## Super Sonic State Machine

Super Sonic uses explicit states so repeated taps and scene changes cannot leave the player half-transformed:

- `NORMAL`
- `TRANSFORMING_IN`
- `SUPER`
- `TRANSFORMING_OUT`

Rules:

- `GO SUPER` from `NORMAL` starts `TRANSFORMING_IN`.
- Additional taps while transforming are ignored.
- Successful transformation enters `SUPER` and exposes the three Super-only buttons.
- `GO SUPER` from `SUPER` starts `TRANSFORMING_OUT`.
- Scene changes during a transformation cancel transient emerald/effect objects, then restore a consistent state.
- If the player object is recreated, the runtime reacquires it before reapplying Super state.

## Chaos Emeralds and Effects

The first version uses runtime-created emerald geometry/materials rather than copying copyrighted model files into the mod package. The seven emeralds are distinct by color, orbit around Sonic, accelerate during the transformation, converge toward the player, and then disappear when Super state begins.

Super visual effects are layered and reversible:

- gold/yellow character treatment where the Redux bridge can safely apply it
- aura/glow effect attached to the player
- speed/boost trail enhancement
- transformation flash/energy effect

Every created runtime object is tracked by the Super Sonic mod and destroyed on toggle-off, scene unload, or mod disable.

## Mobile UI

The runtime adds touch controls as an overlay owned by the Re-born runtime so the original Redux UI remains intact.

Controls:

- `GO SUPER` is always available when the Super Sonic mod is enabled.
- `FLIGHT`, `SUPER BOOST`, and `CHAOS BURST` appear only in `SUPER` state.
- Buttons use large mobile touch targets and avoid covering Redux's existing primary controls as much as possible.
- Button layout data is kept separate from ability logic so positions can be adjusted later without changing game hooks.

## Error Handling and Safety

The runtime prioritizes keeping the rebuilt game launchable:

- Unsupported clean APK: manager refuses runtime injection and explains that the Redux build is unsupported.
- Runtime library fails to load: game continues without runtime mods where technically possible.
- IL2CPP exports unavailable: runtime disables itself and records a diagnostic reason.
- Redux class/field discovery mismatch: affected capability is disabled; no blind write is attempted.
- Super transformation fails mid-sequence: transient emeralds/effects are removed and state returns to `NORMAL`.
- Player/scene disappears: object references are dropped and reacquired after the next valid player spawn.
- Ability is unavailable in a scene: button remains disabled or hidden rather than invoking an invalid target.

No root access, external memory editor, or modification of the original installed Redux package is required.

## Diagnostics

Early v0.2 test builds expose a small diagnostic indicator/log containing:

- runtime loaded / disabled
- CPU ABI
- Redux build matched / rejected
- IL2CPP initialized
- Redux bridge ready
- Super Sonic mod loaded
- last runtime error code/message

The temporary visible `REBORN RUNTIME ONLINE` indicator is removed or made optional after phone testing proves startup reliability.

## Testing

### Host/unit tests

- baseline fingerprint acceptance/rejection
- runtime-mod descriptor parsing
- Super Sonic state-machine transitions
- duplicate/rapid toggle handling
- ability visibility rules
- cleanup tracking for spawned runtime objects
- build pipeline preserves v0.1.2 APK alignment/storage invariants

### Native tests

- ARM64 and ARMv7 libraries compile
- required dynamic-symbol resolver paths handle missing symbols without crashes
- runtime initialization is idempotent
- state cleanup functions are safe when called more than once

### Android build/CI checks

- manager unit tests pass
- manager APK builds and signs
- native runtime builds for both ABIs
- generated test APK contains both runtime libraries
- `resources.arsc` remains stored/aligned correctly
- APK signature verification passes

### Phone validation order

1. Build Re-born v0.2 with runtime but Super Sonic disabled.
2. Confirm rebuilt game installs and launches.
3. Confirm `REBORN RUNTIME ONLINE` appears.
4. Enable Super Sonic with only the `GO SUPER` state toggle and cleanup.
5. Add emerald orbit/transformation effects.
6. Add Super visuals/stats.
7. Add Flight, Super Boost, then Chaos Burst one at a time.

This staged order makes failures attributable instead of combining every new hook in the first phone test.

## Compatibility and Versioning

- Runtime API starts at `2.0` for Re-born v0.2.
- v0.1 overlay mods remain supported.
- Runtime mods declare the minimum runtime API they require.
- The first runtime bridge targets the exact supplied Sonic Redux APK only.
- Supporting another Redux build requires a separate verified bridge profile rather than silently reusing the old one.

## Non-goals for v0.2

- General C#/Lua scripting engine
- arbitrary third-party native plugins
- online/network gameplay modification
- automatic support for every Redux release
- emerald collecting system
- ring-based Super timer/drain
- large public mod SDK before the Super Sonic bridge has been proven on-device

## Delivery Milestones

1. Runtime loader + diagnostics on both ABIs.
2. Stable Redux bridge discovery for the supplied APK.
3. `GO SUPER` state toggle with safe cleanup.
4. 3D emerald transformation and Super visual state.
5. Flight, Super Boost, and Chaos Burst.
6. Package as Re-born v0.2 manager build and perform the same ZIP-wrapped Android delivery flow that worked for v0.1.2.
