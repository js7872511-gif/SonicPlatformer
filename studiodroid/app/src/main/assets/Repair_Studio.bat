@echo off
cd /d "%~dp0"
if not exist "RobloxStudioLauncherBeta.exe" (
  echo RobloxStudioLauncherBeta.exe is missing.
  pause
  exit /b 1
)
echo Running the official Roblox Studio bootstrapper to update or repair Studio...
start "" /wait "RobloxStudioLauncherBeta.exe"
pause
