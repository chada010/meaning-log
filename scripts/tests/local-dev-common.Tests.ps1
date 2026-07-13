$ErrorActionPreference = 'Stop'

$scriptsPath = Split-Path -Parent $PSScriptRoot
$commonScriptPath = Join-Path $scriptsPath 'local-dev-common.ps1'

. $commonScriptPath

$startLocalPath = Join-Path $scriptsPath 'start-local.ps1'

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

$parseErrors = $null
$tokens = $null
$startLocalAst = [Management.Automation.Language.Parser]::ParseFile(
    $startLocalPath,
    [ref]$tokens,
    [ref]$parseErrors
)
Assert-Equal 0 @($parseErrors).Count 'The local startup script must parse successfully.'
$timeoutAssignment = $startLocalAst.Find({
        param($node)
        $node -is [Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left -is [Management.Automation.Language.VariableExpressionAst] -and
        $node.Left.VariablePath.UserPath -eq 'composeHealthTimeoutSeconds'
    }, $true)
Assert-True ($null -ne $timeoutAssignment) `
    'Local startup must declare a Compose health timeout.'
$composeHealthTimeoutSeconds = 0
$timeoutParsed = [int]::TryParse(
    $timeoutAssignment.Right.Extent.Text,
    [ref]$composeHealthTimeoutSeconds
)
Assert-True $timeoutParsed 'The Compose health timeout must be an integer literal.'
Assert-True ($composeHealthTimeoutSeconds -ge 120) `
    'Compose health waiting must cover the MySQL health-check window.'

$dockerCommands = @($startLocalAst.FindAll({
        param($node)
        $node -is [Management.Automation.Language.CommandAst] -and
        $node.GetCommandName() -eq 'docker'
    }, $true))
$composeUpCommand = $dockerCommands | Where-Object { $_.Extent.Text -match '\bup\s+-d\b' } |
    Select-Object -First 1
$composeDownCommand = $dockerCommands | Where-Object { $_.Extent.Text -match '\bdown\b' } |
    Select-Object -First 1
Assert-True ($null -ne $composeUpCommand) 'Local startup must start Compose dependencies.'
Assert-True ($null -ne $composeDownCommand) 'Local startup must define Compose rollback.'
$startupTry = $startLocalAst.FindAll({
        param($node)
        $node -is [Management.Automation.Language.TryStatementAst]
    }, $true) | Where-Object {
        $_.Body.Extent.StartOffset -le $composeUpCommand.Extent.StartOffset -and
        $_.Body.Extent.EndOffset -ge $composeUpCommand.Extent.EndOffset
    } | Select-Object -First 1
Assert-True ($null -ne $startupTry) `
    'Compose startup must be protected by the startup rollback block.'
Assert-True (
    $startupTry.Finally.Extent.StartOffset -le $composeDownCommand.Extent.StartOffset -and
    $startupTry.Finally.Extent.EndOffset -ge $composeDownCommand.Extent.EndOffset
) 'Compose rollback must run from the startup finally block.'

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
$legacyProjectName = Get-LocalComposeProjectName $testRoot 'meaning-log'
Assert-Equal 'meaning-log' $legacyProjectName 'An explicit legacy project name must be preserved.'
$legacyComposeArguments = @(Get-LocalComposeArguments $testRoot 'meaning-log')
Assert-Equal 'meaning-log' $legacyComposeArguments[4] `
    'Compose arguments must use the explicit legacy project name.'
$invalidProjectNameCaught = $false
try {
    Get-LocalComposeProjectName $testRoot 'Meaning Log' | Out-Null
} catch {
    $invalidProjectNameCaught = $_.Exception.Message -like 'LOCAL_COMPOSE_PROJECT_NAME must contain*'
}
Assert-True $invalidProjectNameCaught 'An unsafe Compose project name must be rejected.'

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

$composeEnvironment = ConvertFrom-ComposeEnvironmentOutput @(
    'MYSQL_PASSWORD=password with spaces',
    'MAIL_PASSWORD=resend=value=with=equals'
)
Assert-Equal 'password with spaces' $composeEnvironment.MYSQL_PASSWORD `
    'Compose-parsed values must be passed through without further dotenv parsing.'
Assert-Equal 'resend=value=with=equals' $composeEnvironment.MAIL_PASSWORD `
    'Environment values may contain equals signs.'

$exampleMailFromCaught = $false
try {
    Assert-MailFromAddress 'noreply@your-verified-domain.example'
} catch {
    $exampleMailFromCaught = $_.Exception.Message -eq 'MAIL_FROM in .env must not use the example domain'
}
Assert-True $exampleMailFromCaught 'The example MAIL_FROM domain must stop local startup.'
$bareExampleMailFromCaught = $false
try {
    Assert-MailFromAddress 'noreply@example'
} catch {
    $bareExampleMailFromCaught = $_.Exception.Message -eq 'MAIL_FROM in .env must not use the example domain'
}
Assert-True $bareExampleMailFromCaught 'The bare example domain must stop local startup.'
$trailingDotExampleMailFromCaught = $false
try {
    Assert-MailFromAddress 'noreply@example.'
} catch {
    $trailingDotExampleMailFromCaught = `
        $_.Exception.Message -eq 'MAIL_FROM in .env must not use the example domain'
}
Assert-True $trailingDotExampleMailFromCaught `
    'An example domain with a trailing dot must stop local startup.'
Assert-MailFromAddress 'noreply@example.com'

$quote = [char]39
$quotedRoot = "C:\Users\O${quote}Brien\meaning-log"
$quotedExecutable = Join-Path $quotedRoot 'meaning-log-backend\mvnw.cmd'
$quotedOutput = Join-Path $quotedRoot 'logs\backend-local.log'
$quotedError = Join-Path $quotedRoot 'logs\backend-local-error.log'
$processParameters = Get-DetachedProcessParameters `
    -FilePath $quotedExecutable `
    -Arguments @('spring-boot:run') `
    -WorkingDirectory $quotedRoot `
    -OutputPath $quotedOutput `
    -ErrorPath $quotedError
Assert-Equal $quotedRoot $processParameters.WorkingDirectory `
    'A working directory containing an apostrophe must remain intact.'
Assert-Equal $quotedExecutable $processParameters.FilePath `
    'An executable path containing an apostrophe must remain intact.'
Assert-Equal $quotedOutput $processParameters.RedirectStandardOutput `
    'An output path containing an apostrophe must remain intact.'
Assert-Equal $quotedError $processParameters.RedirectStandardError `
    'An error path containing an apostrophe must remain intact.'

$processStatePath = Join-Path ([IO.Path]::GetTempPath()) "meaning-log-process-state-$PID.json"
$testProcess = Start-Process powershell.exe `
    -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 30' `
    -WindowStyle Hidden `
    -PassThru
try {
    Write-LocalProcessState $processStatePath 'meaning-log-test' $testProcess $null
    Assert-True (Test-Path -LiteralPath $processStatePath) `
        'Starting an application process must persist its identity.'
    $stoppedProjectName = Stop-LocalTrackedProcesses $processStatePath
    $testProcess.WaitForExit(5000) | Out-Null
    Assert-Equal 'meaning-log-test' $stoppedProjectName `
        'The tracked Compose project name must survive the process state round trip.'
    Assert-True $testProcess.HasExited 'Startup rollback must stop a process created by the script.'
    Assert-True (Test-Path -LiteralPath $processStatePath) `
        'Process state must remain available until Compose dependencies also stop.'
    Assert-Equal 'meaning-log-test' (Stop-LocalTrackedProcesses $processStatePath) `
        'A retry after the application process exits must retain the Compose project name.'
    Remove-Item -LiteralPath $processStatePath
} finally {
    if (-not $testProcess.HasExited) {
        Stop-Process -Id $testProcess.Id -Force
    }
    Remove-Item -ErrorAction SilentlyContinue -LiteralPath $processStatePath
}

$reusedPidProcess = Start-Process powershell.exe `
    -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 30' `
    -WindowStyle Hidden `
    -PassThru
try {
    $pidReuseCaught = $false
    $wrongRecord = [pscustomobject]@{
        Id = $reusedPidProcess.Id
        StartTimeFileTimeUtc = 0
    }
    try {
        Stop-LocalTrackedProcess $wrongRecord 'test process'
    } catch {
        $pidReuseCaught = $_.Exception.Message -like 'Refusing to stop test process*'
    }
    Assert-True $pidReuseCaught 'A reused PID must not allow an unrelated process to be stopped.'
    Assert-True (-not $reusedPidProcess.HasExited) `
        'The process with a mismatched identity must remain running.'
} finally {
    if (-not $reusedPidProcess.HasExited) {
        Stop-Process -Id $reusedPidProcess.Id -Force
    }
}

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try {
    $occupiedPort = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $currentProcess = Get-Process -Id $PID
    Assert-True (Wait-ForLocalProcessListeningPort $currentProcess $occupiedPort 2) `
        'Readiness must recognize a port owned by the started process.'
    $portConflictCaught = $false
    try {
        Assert-ListeningPortAvailable $occupiedPort 'test service'
    } catch {
        $portConflictCaught = $_.Exception.Message -like "*port $occupiedPort is already in use*"
    }
    Assert-True $portConflictCaught 'An occupied application port must stop local startup.'

    $unrelatedProcess = Start-Process powershell.exe `
        -ArgumentList '-NoProfile', '-Command', 'Start-Sleep -Seconds 30' `
        -WindowStyle Hidden `
        -PassThru
    try {
        Assert-True (-not (Wait-ForLocalProcessListeningPort $unrelatedProcess $occupiedPort 1)) `
            'A port owned by another process must not satisfy readiness.'
    } finally {
        if (-not $unrelatedProcess.HasExited) {
            Stop-Process -Id $unrelatedProcess.Id -Force
        }
    }

} finally {
    $listener.Stop()
}

$childListenerScript = @"
`$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $occupiedPort)
`$listener.Start()
try { Start-Sleep -Seconds 30 } finally { `$listener.Stop() }
"@
$childListenerCommand = [Convert]::ToBase64String(
    [Text.Encoding]::Unicode.GetBytes($childListenerScript)
)
$wrapperScript = "& powershell.exe -NoProfile -EncodedCommand $childListenerCommand"
$wrapperCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($wrapperScript))
$wrapperProcess = Start-Process powershell.exe `
    -ArgumentList '-NoProfile', '-EncodedCommand', $wrapperCommand `
    -WindowStyle Hidden `
    -PassThru
try {
    Assert-True (Wait-ForLocalProcessListeningPort $wrapperProcess $occupiedPort 5) `
        'Readiness must recognize a port owned by a descendant process.'
} finally {
    Stop-LocalProcessTree $wrapperProcess
}

Write-Output 'local-dev-common tests passed'
