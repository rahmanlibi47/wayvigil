@echo off
setlocal

cd /d "%~dp0"

echo Building and installing the debug app...
call gradlew.bat installDebug
if errorlevel 1 (
    echo.
    echo BUILD FAILED. The app was not launched.
    exit /b 1
)

echo.
echo BUILD SUCCESSFUL. Launching WayVigil AI...
adb shell monkey -p com.saferoute 1
if errorlevel 1 (
    echo.
    echo The app was installed, but could not be launched. Check that a device is connected and authorized with adb.
    exit /b 1
)

exit /b 0
