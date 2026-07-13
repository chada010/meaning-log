$ErrorActionPreference = 'Stop'

$scriptsPath = Split-Path -Parent $PSScriptRoot
$commonScriptPath = Join-Path $scriptsPath 'local-dev-common.ps1'

. $commonScriptPath

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)

    if ($Expected -ne $Actual) {
        throw "$Message Expected '$Expected', got '$Actual'."
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)

    if (-not $Condition) {
        throw $Message
    }
}

$testRoot = Join-Path ([IO.Path]::GetTempPath()) 'MeaningLogWorkspaceA'
$sameRootWithTrailingSeparator = $testRoot + [IO.Path]::DirectorySeparatorChar
$otherRoot = Join-Path ([IO.Path]::GetTempPath()) 'MeaningLogWorkspaceB'

$projectName = Get-LocalComposeProjectName $testRoot
Assert-Equal $projectName (Get-LocalComposeProjectName $sameRootWithTrailingSeparator) `
    'Compose project name must be stable for equivalent paths.'
Assert-True ($projectName -ne (Get-LocalComposeProjectName $otherRoot)) `
    'Compose project names must differ between workspaces.'
Assert-True ($projectName -match '^meaning-log-[a-f0-9]{12}$') `
    'Compose project name must use the expected safe format.'
$composeArguments = @(Get-LocalComposeArguments $testRoot)
Assert-Equal 5 $composeArguments.Count 'Compose arguments must include the isolated project name.'
Assert-Equal '--project-name' $composeArguments[3] 'Compose arguments must declare the project name.'
Assert-Equal $projectName $composeArguments[4] 'Compose arguments must use the workspace project name.'

$nativeFailureCaught = $false
& cmd.exe /c exit 7
$nativeExitCode = $LASTEXITCODE
try {
    Assert-NativeCommandSucceeded 'Test command' $nativeExitCode
} catch {
    $nativeFailureCaught = $_.Exception.Message -eq 'Test command failed with exit code 7.'
}
Assert-True $nativeFailureCaught 'A non-zero native exit code must throw a descriptive error.'
Assert-NativeCommandSucceeded 'Test command' 0

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try {
    $occupiedPort = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $portConflictCaught = $false
    try {
        Assert-ListeningPortAvailable $occupiedPort 'test service'
    } catch {
        $portConflictCaught = $_.Exception.Message -like "*port $occupiedPort is already in use*"
    }
    Assert-True $portConflictCaught 'An occupied application port must stop local startup.'
} finally {
    $listener.Stop()
}

Write-Output 'local-dev-common tests passed'
