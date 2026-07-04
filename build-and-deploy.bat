@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: Project root and paths
set "PROJECT_DIR=C:\_Source\Synth-music"
set "APK_NAME=SynthMusic.apk"
set "BUILD_OUTPUT=%PROJECT_DIR%\app\build\outputs\apk\release\app-release.apk"
set "DEST_ONEDRIVE=%OneDrive%\Desktop\%APK_NAME%"
set "DEST_DESKTOP=%USERPROFILE%\Desktop\%APK_NAME%"

cd /d "%PROJECT_DIR%"
if errorlevel 1 (
    echo Failed to change directory to %PROJECT_DIR%
    exit /b 1
)

echo Building signed release APK...
call gradlew.bat :app:assembleRelease
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

if not exist "%BUILD_OUTPUT%" (
    echo APK not found at %BUILD_OUTPUT%
    exit /b 1
)

echo Copying APK to destinations with overwrite...

if defined OneDrive (
    copy /y "%BUILD_OUTPUT%" "%DEST_ONEDRIVE%" >nul
    if errorlevel 1 (
        echo Failed to copy to OneDrive Desktop.
    ) else (
        echo Copied to %DEST_ONEDRIVE%
    )
) else (
    echo OneDrive environment variable not set, skipping.
)

copy /y "%BUILD_OUTPUT%" "%DEST_DESKTOP%" >nul
if errorlevel 1 (
    echo Failed to copy to local Desktop.
    exit /b 1
) else (
    echo Copied to %DEST_DESKTOP%
)

echo Done.
exit /b 0
