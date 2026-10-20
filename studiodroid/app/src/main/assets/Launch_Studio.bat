@echo off
setlocal enabledelayedexpansion
set "VERSIONS=%LOCALAPPDATA%\Roblox\Versions"
if not exist "%VERSIONS%" goto installer
for /f "delims=" %%D in ('dir /b /ad /o-d "%VERSIONS%\version-*" 2^>nul') do (
  if exist "%VERSIONS%\%%D\RobloxStudioBeta.exe" (
    start "" "%VERSIONS%\%%D\RobloxStudioBeta.exe"
    exit /b 0
  )
)
:installer
echo Studio installation was not found. Starting the official installer/updater.
if exist "%~dp0RobloxStudioLauncherBeta.exe" start "" "%~dp0RobloxStudioLauncherBeta.exe"
pause
