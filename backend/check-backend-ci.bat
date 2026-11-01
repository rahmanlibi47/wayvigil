@echo off
setlocal

cd /d "%~dp0"

echo Checking Poetry configuration and lock file...
call poetry check --lock
if errorlevel 1 exit /b 1

echo Checking Black formatting...
call poetry run black --check app tests/test_navigation_api.py
if errorlevel 1 exit /b 1

echo Checking import order...
call poetry run isort --check-only app tests/test_navigation_api.py
if errorlevel 1 exit /b 1

echo Running Flake8...
call poetry run flake8 app
if errorlevel 1 exit /b 1

echo Running mypy...
call poetry run mypy app
if errorlevel 1 exit /b 1

echo Backend checks passed. Pytest was not run.
exit /b 0
