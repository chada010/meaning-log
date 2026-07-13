$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$commonScriptPath = Join-Path $PSScriptRoot 'local-dev-common.ps1'

. $commonScriptPath

$processStatePath = Get-LocalProcessStatePath $projectRoot
$composeProjectName = Stop-LocalTrackedProcesses $processStatePath

& docker info --format '{{.ServerVersion}}' | Out-Null
Assert-NativeCommandSucceeded 'Checking Docker availability' $LASTEXITCODE
if (-not $composeProjectName) {
    $localEnv = Get-LocalEnvironmentValues $projectRoot
    $composeProjectName = Get-LocalComposeProjectName `
        $projectRoot `
        $localEnv.LOCAL_COMPOSE_PROJECT_NAME
}
$composeArguments = Get-LocalComposeArguments $projectRoot $composeProjectName
& docker @composeArguments down
Assert-NativeCommandSucceeded 'Stopping Docker Compose dependencies' $LASTEXITCODE
Remove-Item -ErrorAction SilentlyContinue -LiteralPath $processStatePath

Write-Host "Stopped local applications and Docker dependencies for project: $composeProjectName"
