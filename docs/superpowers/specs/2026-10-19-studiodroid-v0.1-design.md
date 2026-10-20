# StudioDroid v0.1 Design

## Goal
Build a phone-first Android launcher that helps the user run the official `RobloxStudioLauncherBeta.exe` inside an existing Winlator-style Windows compatibility environment without requiring a PC or Roblox Studio on desktop.

## Success Criteria
- Installs as a normal Android APK.
- Lets the user import `RobloxStudioLauncherBeta.exe` from Android storage.
- Stores the imported EXE and StudioDroid support files in a predictable app-managed folder.
- Provides large mobile-friendly buttons for Install Studio, Launch Studio, Repair, Files, and Open Windows Layer.
- Detects whether a compatible Windows layer app such as Winlator is installed and opens it when execution is requested.
- Preserves the imported EXE and configuration between launches.
- Does not modify Roblox Studio binaries or redistribute Roblox Studio itself.

## Architecture
StudioDroid v0.1 is intentionally a small Android front-end rather than an all-in-one emulator.

### Android App
A single-activity Kotlin app with a simple native UI. It owns file import, local state, status reporting, and handoff to the Windows compatibility layer.

### Storage
App-private storage is the source of truth. Imported files are copied into a StudioDroid workspace, with metadata stored in SharedPreferences. When a Winlator-compatible shared folder is available, StudioDroid copies the launcher/support files there as well.

### Windows Layer Handoff
StudioDroid does not embed Wine/Box64 in v0.1. It detects supported package names, prioritizing Winlator-compatible installs, and launches the selected app using an Android intent. If none is installed, StudioDroid shows a clear setup message instead of failing silently.

### Support Files
StudioDroid packages helper scripts equivalent to the existing container kit:
- `Install_Studio.bat`
- `Launch_Studio.bat`
- `Repair_Studio.bat`

These scripts are copied into the workspace beside the imported `RobloxStudioLauncherBeta.exe`.

## User Flow
1. User opens StudioDroid.
2. The home screen shows whether a Studio EXE is imported and whether a Windows layer is detected.
3. User taps Import Studio EXE and selects `RobloxStudioLauncherBeta.exe`.
4. StudioDroid copies and validates the file as a Windows PE executable.
5. Install Studio prepares the workspace and opens the Windows layer with guidance to run `Install_Studio.bat`.
6. Launch Studio prepares the workspace and opens the Windows layer with guidance to run `Launch_Studio.bat`.
7. Repair prepares the workspace and opens the Windows layer with guidance to run `Repair_Studio.bat`.

## UI
Dark mobile-first layout with a compact StudioDroid title, a status card, and large stacked buttons:
- Import Studio EXE
- Install Studio
- Launch Studio
- Repair
- Open Files
- Open Windows Layer

The app must remain usable on a phone without a hardware keyboard or mouse.

## Error Handling
- Missing EXE: disable install/launch actions and explain what is missing.
- Invalid file: reject files that do not begin with the PE `MZ` signature.
- Missing Windows layer: show a clear message and keep imported files intact.
- File-copy failure: show the exact failed action and keep the original imported file unchanged.
- Unsupported Android file URI: copy through ContentResolver rather than relying on direct filesystem paths.

## Security and Scope
- StudioDroid launches only user-selected files and bundled helper scripts.
- No code injection, Roblox client modification, exploit functionality, or anti-cheat bypassing.
- Roblox Studio remains the official Windows program supplied by the user.

## Testing
- Build debug and release APKs in GitHub Actions.
- Verify installability on Android 12+.
- Unit-test PE header validation and workspace state logic.
- Instrumentation-test the import flow with a mock document provider.
- Manually verify missing-layer, missing-file, and happy-path handoff states.

## v0.1 Non-Goals
- Bundling Winlator, Wine, Box64, DXVK, or Roblox Studio into the APK.
- Automatically controlling the Winlator UI.
- Guaranteeing that every Roblox Studio version works under Wine.
- Background downloading of Roblox Studio.