# Chaos Edition 3.0 Design

## Intent
Chaos Edition 3.0 is a separate Android Sonic 3 A.I.R. fork that acts as a platform, not a one-off ability mod. It keeps ordinary Sonic 3 A.I.R. mods compatible and adds optional Chaos Edition platform features without requiring mods to use a new package format.

## Goals
- Build/install as a separate Android APK named **S3AIR Chaos Edition 3.0**.
- Preserve stock Sonic 3 A.I.R. mod loading and ordinary `mod.json` behavior.
- Keep the 16-save design: bank A has eight completed Chaos saves; bank B has eight normal fresh/replay saves.
- Keep the user's current built-in gameplay concept: death progression and Berserker mode only. Do not re-add the rejected Water, Hyper Water, Super/Emerald-power, SLIP, Rush, Sky, or Chaos ability pack.
- Death progression:
  - 1 death: unlock Speed Up; +10% speed and +5% ring benefit.
  - 5 deaths: unlock Sonic Smash; Speed Up upgrades to +15% speed and +10% ring benefit.
  - 10 deaths: unlock Motobug Ride; jumping off sends the Motobug forward to crash/explode.
  - 15 deaths: unlock Berserker.
  - Lives continue below zero instead of causing Game Over while progression is active.
  - Berserker transformation: kneel, red eyes, gradually dark/corrupt appearance, infinite-lives HUD state.
  - Berserker replacements: Speed Up -> Flight, Sonic Smash -> summoned throwable laser, Motobug Ride -> summonable Sonic mech/Death-Egg-style machine.
  - Berserker badnik kills increase power; at 50 kills, bosses can be one-shot.
- Add a universal optional touch-button extension usable by any ordinary S3AIR mod:
  - A mod may include `chaos-buttons.json` next to its `mod.json`.
  - Mods without that file are unchanged.
  - Buttons can target stock player controls or secondary virtual controllers for independent actions.
  - Configurable position, size, control, virtual controller, and icon sprite key.
  - Rebuild button layout when active mods change.

## Non-goals for 3.0 core
- Do not bundle the Firey character mod.
- Do not bundle the rejected Water/Hyper Water/Super/Emerald ability pack.
- Do not require a Chaos-only mod package format.
- Do not embed an OpenAI/API key or online AI service in the APK.

## Button sidecar format
Example:
```json
{
  "Buttons": [
    {
      "Controller": 1,
      "Control": "A",
      "X": 0.65,
      "Y": -0.20,
      "Size": 0.14,
      "Sprite": "touch_overlay_A"
    }
  ]
}
```
`Controller` 0 maps to the player's normal controller. Higher controller indexes are independent virtual controller channels readable by LemonScript. Valid controls are `A`, `B`, `X`, `Y`, `L`, `R`, `Start`, `Up`, `Down`, `Left`, `Right`.

## Build base
- Sonic 3 A.I.R. `v26.03.28.0-stable`.
- Android arm64-v8a first.
- Separate package ID: `com.chaosed.sonic3air.v30`.
- APK filename: `S3AIR_Chaos_Edition_3.0.apk`.
