@echo off
setlocal

cd /d "%~dp0"
call gradlew.bat --max-workers=4 --build-cache ktlintCheck testDebugUnitTest compileReleaseKotlin assembleDebug
set "RESULT=%ERRORLEVEL%"

if "%RESULT%"=="0" (
    echo Android CI checks and build succeeded.
) else (
    echo Android CI checks or build failed with exit code %RESULT%.
)

exit /b %RESULT%
