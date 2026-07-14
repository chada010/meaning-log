# Start backend with loadtest profile (foreground). Ctrl+C to stop.
#
# Prereqs:
#   1. .env exists (MAIL_PASSWORD, MAIL_FROM, etc.)
#   2. Docker deps running (MySQL/Redis/RabbitMQ)
#   3. Port 8080 free (stop start-local.ps1 first if used)
#
# Usage:
#   .\scripts\loadtest\start-backend.ps1
#
# Differences vs main start-local.ps1:
#   - SPRING_PROFILES_ACTIVE=loadtest (Mock Transport + sync baseline endpoint + no rate limit)
#   - Foreground (easy to watch logs, Ctrl+C to stop)
#   - No frontend
#   - No processState file (does not conflict with start-local.ps1)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backendPath = Join-Path $projectRoot 'meaning-log-backend'
$envPath = Join-Path $projectRoot '.env'
$commonScriptPath = Join-Path $projectRoot 'scripts\local-dev-common.ps1'

if (-not (Test-Path $envPath)) {
    throw "Missing $envPath -- run start-local.ps1 once first, or create .env manually"
}
. $commonScriptPath

Write-Host '==> Reading environment from .env' -ForegroundColor Cyan
$envValues = Get-LocalEnvironmentValues $projectRoot
foreach ($key in $envValues.Keys) {
    if ([string]::IsNullOrEmpty($key)) { continue }
    [Environment]::SetEnvironmentVariable($key, $envValues[$key], 'Process')
}

[Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', 'loadtest', 'Process')

if (Test-ListeningPort 8080) {
    throw 'Port 8080 is in use -- is the default backend from start-local.ps1 still running? Run stop-local.ps1 first.'
}

Write-Host '==> Starting backend with SPRING_PROFILES_ACTIVE=loadtest' -ForegroundColor Cyan
Write-Host '==> Ctrl+C to stop. Look for "Started MeaningLogBackendApplication" to confirm success.' -ForegroundColor Cyan

$mvnw = Join-Path $backendPath 'mvnw.cmd'
Push-Location $backendPath
try {
    & $mvnw spring-boot:run
} finally {
    Pop-Location
}
