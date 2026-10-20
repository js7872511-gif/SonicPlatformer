@echo off
cd /d "%~dp0"
if not exist "RobloxStudioLauncherBeta.exe" (
  echo RobloxStudioLauncherBeta.exe is missing.
  pause
  exit /b 1
)
echo Starting the official Roblox Studio installer...
start "" /wait "RobloxStudioLauncherBeta.exe"
echo Installer closed. You can now use Launch_Studio.bat.
pause
