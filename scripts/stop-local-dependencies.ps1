$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$commonScriptPath = Join-Path $PSScriptRoot 'local-dev-common.ps1'

. $commonScriptPath

$composeArguments = Get-LocalComposeArguments $projectRoot
$composeProjectName = Get-LocalComposeProjectName $projectRoot

& docker info --format '{{.ServerVersion}}' | Out-Null
Assert-NativeCommandSucceeded 'Checking Docker availability' $LASTEXITCODE
& docker @composeArguments down
Assert-NativeCommandSucceeded 'Stopping Docker Compose dependencies' $LASTEXITCODE

Write-Host "Stopped Docker dependencies for project: $composeProjectName"
