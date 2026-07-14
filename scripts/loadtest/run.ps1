# Run k6 load test against loadtest backend via Docker (no local k6 install needed).
#
# Usage:
#   .\scripts\loadtest\run.ps1 -Scenario sync  -Vus 10  -Duration 15s -LogId 1
#   .\scripts\loadtest\run.ps1 -Scenario async -Vus 100 -Duration 60s -LogId 1

param(
    [ValidateSet('sync', 'async')]
    [Parameter(Mandatory = $true)][string]$Scenario,

    [Parameter(Mandatory = $true)][int]$Vus,

    [Parameter(Mandatory = $true)][string]$Duration,

    [Parameter(Mandatory = $true)][string]$LogId,

    [string]$BaseUrl = 'http://host.docker.internal:8080',
    [string]$UserEmail = 'test',
    [string]$UserPassword = 'test1234'
)

$ErrorActionPreference = 'Stop'

$scriptDir = $PSScriptRoot -replace '\\', '/'
$scriptFile = if ($Scenario -eq 'sync') { 'sync-ai.js' } else { 'async-ai.js' }

Write-Host "==> Scenario=$Scenario  VUs=$Vus  Duration=$Duration  LogId=$LogId" -ForegroundColor Cyan

docker run --rm `
    -v "${scriptDir}:/scripts" `
    -e "BASE_URL=$BaseUrl" `
    -e "USER_EMAIL=$UserEmail" `
    -e "USER_PASSWORD=$UserPassword" `
    -e "LOG_ID=$LogId" `
    -e "VUS=$Vus" `
    -e "DURATION=$Duration" `
    grafana/k6 run "/scripts/$scriptFile"
