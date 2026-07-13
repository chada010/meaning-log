$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$commonScriptPath = Join-Path $PSScriptRoot 'local-dev-common.ps1'

. $commonScriptPath

& docker info --format '{{.ServerVersion}}' | Out-Null
Assert-NativeCommandSucceeded 'Checking Docker availability' $LASTEXITCODE
$localEnv = Get-LocalEnvironmentValues $projectRoot
$composeProjectNameOverride = $localEnv.LOCAL_COMPOSE_PROJECT_NAME
$composeArguments = Get-LocalComposeArguments $projectRoot $composeProjectNameOverride
$composeProjectName = Get-LocalComposeProjectName $projectRoot $composeProjectNameOverride
& docker @composeArguments down
Assert-NativeCommandSucceeded 'Stopping Docker Compose dependencies' $LASTEXITCODE

Write-Host "Stopped Docker dependencies for project: $composeProjectName"
