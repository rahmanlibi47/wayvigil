@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT_DIR=%~dp0"
set "COMPOSE_FILE=%ROOT_DIR%infrastructure\docker\docker-compose.yml"
set "ENV_FILE=%ROOT_DIR%.env.local"

docker info >nul 2>&1
if errorlevel 1 (
    echo Docker is not available. Start Docker Desktop and try again.
    pause
    exit /b 1
)

docker volume inspect docker_postgres_data >nul 2>&1
if errorlevel 1 (
    echo Creating persistent Postgres volume docker_postgres_data...
    docker volume create docker_postgres_data >nul
    if errorlevel 1 (
        echo Could not create the Postgres volume.
        pause
        exit /b 1
    )
)

docker image inspect docker-backend:latest >nul 2>&1
if errorlevel 1 (
    echo Local backend image is missing. Building docker-backend:latest...
    docker build -t docker-backend:latest -f "%ROOT_DIR%infrastructure\docker\Dockerfile.backend" "%ROOT_DIR%"
    if errorlevel 1 (
        echo Backend image build failed.
        pause
        exit /b 1
    )
)

if not exist "%ENV_FILE%" (
    call :generate_secret
    if errorlevel 1 exit /b 1
    >"%ENV_FILE%" echo SECRET_KEY=!SECRET_KEY!
) else (
    for /f "tokens=1,* delims==" %%A in ('findstr /b /c:"SECRET_KEY=" "%ENV_FILE%"') do set "SECRET_KEY=%%B"
    if not defined SECRET_KEY (
        call :generate_secret
        if errorlevel 1 exit /b 1
        >>"%ENV_FILE%" echo SECRET_KEY=!SECRET_KEY!
    )
)

echo Starting WayVigil PostGIS and backend services...
docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" up -d db backend
if errorlevel 1 (
    echo Startup failed. Check Docker Desktop and the Compose error above.
    pause
    exit /b 1
)

docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" ps
echo.
echo Backend URL: http://localhost:8000
echo Follow backend logs with:
echo docker compose --env-file "%ENV_FILE%" -f "%COMPOSE_FILE%" logs -f backend
exit /b 0

:generate_secret
for /f %%K in ('powershell -NoProfile -Command "$bytes = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [System.BitConverter]::ToString($bytes).Replace([string][char]45, [string]::Empty).ToLowerInvariant()"') do set "SECRET_KEY=%%K"
if not defined SECRET_KEY (
    echo Could not generate SECRET_KEY.
    pause
    exit /b 1
)
exit /b 0
