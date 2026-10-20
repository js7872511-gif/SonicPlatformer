# StudioDroid v0.1

StudioDroid is a phone-first Android launcher that prepares the official Roblox Studio Windows bootstrapper for use inside a separately installed Winlator-compatible Windows layer.

## v0.1 flow
1. Install the StudioDroid APK.
2. Tap **Import Studio EXE** and select `RobloxStudioLauncherBeta.exe`.
3. StudioDroid validates the Windows `MZ` header and stores an app-private copy.
4. StudioDroid mirrors the EXE and helper scripts to `Downloads/StudioDroid`.
5. Tap **Install Studio**, **Launch Studio**, or **Repair / Update Studio** to open the detected Windows layer.
6. In Winlator, open the Downloads drive and run the requested `.bat` file from the `StudioDroid` folder.

StudioDroid does not bundle or modify Roblox Studio, Winlator, Wine, Box64, or DXVK.
