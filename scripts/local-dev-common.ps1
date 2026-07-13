function Get-LocalComposeProjectName {
    param([string]$ProjectRoot, [string]$ProjectNameOverride)

    if (-not [string]::IsNullOrWhiteSpace($ProjectNameOverride)) {
        $projectName = $ProjectNameOverride.Trim()
        if ($projectName -notmatch '^[a-z0-9][a-z0-9_-]*$') {
            throw 'LOCAL_COMPOSE_PROJECT_NAME must contain only lowercase letters, digits, hyphens, and underscores'
        }
        return $projectName
    }

    $fullPath = [IO.Path]::GetFullPath($ProjectRoot)
    $normalizedPath = $fullPath.TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    ).ToLowerInvariant()
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($normalizedPath))
    } finally {
        $sha256.Dispose()
    }
    $suffix = [BitConverter]::ToString($hash).Replace('-', '').Substring(0, 12).ToLowerInvariant()
    return "meaning-log-$suffix"
}

function Get-LocalComposeArguments {
    param([string]$ProjectRoot, [string]$ProjectNameOverride)

    $projectName = Get-LocalComposeProjectName $ProjectRoot $ProjectNameOverride
    return @('compose', '--project-directory', $ProjectRoot, '--project-name', $projectName)
}

function New-LocalComposeRollbackPlan {
    param(
        [string[]]$ManagedServices,
        [string[]]$ExistingServices,
        [string[]]$RunningServices
    )

    $existingServiceSet = @{}
    foreach ($service in $ExistingServices) {
        if (-not [string]::IsNullOrWhiteSpace($service)) {
            $existingServiceSet[$service] = $true
        }
    }
    $runningServiceSet = @{}
    foreach ($service in $RunningServices) {
        if (-not [string]::IsNullOrWhiteSpace($service)) {
            $runningServiceSet[$service] = $true
        }
    }

    $removeProject = $existingServiceSet.Count -eq 0
    $removeServices = @()
    $stopServices = @()
    if (-not $removeProject) {
        $removeServices = @($ManagedServices | Where-Object {
                -not $existingServiceSet.ContainsKey($_)
            })
        $stopServices = @($ManagedServices | Where-Object {
                $existingServiceSet.ContainsKey($_) -and
                -not $runningServiceSet.ContainsKey($_)
            })
    }

    return [pscustomobject]@{
        RemoveProject = $removeProject
        RemoveServices = $removeServices
        StopServices = $stopServices
    }
}

function Assert-NativeCommandSucceeded {
    param([string]$Description, [int]$ExitCode)

    if ($ExitCode -ne 0) {
        throw "$Description failed with exit code $ExitCode."
    }
}

function ConvertFrom-ComposeEnvironmentOutput {
    param([string[]]$Lines)

    $values = @{}
    foreach ($line in $Lines) {
        $pair = $line.Split('=', 2)
        if ($pair.Length -eq 2) {
            $values[$pair[0]] = $pair[1]
        }
    }
    return $values
}

function Get-LocalEnvironmentValues {
    param([string]$ProjectRoot)

    $output = & docker compose --project-directory $ProjectRoot config --environment
    Assert-NativeCommandSucceeded 'Reading the Docker Compose environment' $LASTEXITCODE
    return ConvertFrom-ComposeEnvironmentOutput $output
}

function Assert-MailFromAddress {
    param([string]$Address)

    try {
        $mailAddress = [Net.Mail.MailAddress]::new($Address)
    } catch {
        throw 'MAIL_FROM in .env must be a valid email address'
    }
    $normalizedHost = $mailAddress.Host.TrimEnd('.')
    if ($normalizedHost.Equals('example', [StringComparison]::OrdinalIgnoreCase) -or
        $normalizedHost.EndsWith('.example', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'MAIL_FROM in .env must not use the example domain'
    }
}

function Test-LocalProcessOwnsListeningPort {
    param([Diagnostics.Process]$Process, [int]$Port)

    if ($null -eq $Process -or $Process.HasExited) {
        return $false
    }

    $connections = @(Get-NetTCPConnection `
        -State Listen `
        -LocalPort $Port `
        -ErrorAction SilentlyContinue)
    foreach ($connection in $connections) {
        $currentProcessId = [int]$connection.OwningProcess
        $visitedProcessIds = @{}
        while ($currentProcessId -gt 0 -and -not $visitedProcessIds.ContainsKey($currentProcessId)) {
            if ($currentProcessId -eq $Process.Id) {
                return $true
            }
            $visitedProcessIds[$currentProcessId] = $true
            $currentProcess = Get-CimInstance `
                -ClassName Win32_Process `
                -Filter "ProcessId = $currentProcessId" `
                -ErrorAction SilentlyContinue
            if ($null -eq $currentProcess) {
                break
            }
            $currentProcessId = [int]$currentProcess.ParentProcessId
        }
    }
    return $false
}

function Wait-ForLocalProcessListeningPort {
    param(
        [Diagnostics.Process]$Process,
        [int]$Port,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($null -eq $Process -or $Process.HasExited) {
            return $false
        }
        if (Test-LocalProcessOwnsListeningPort $Process $Port) {
            return $true
        }
        Start-Sleep -Milliseconds 250
    }
    return $false
}

function Get-DetachedProcessParameters {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$OutputPath,
        [string]$ErrorPath
    )

    return @{
        FilePath = $FilePath
        ArgumentList = $Arguments
        WorkingDirectory = $WorkingDirectory
        RedirectStandardOutput = $OutputPath
        RedirectStandardError = $ErrorPath
        WindowStyle = 'Hidden'
    }
}

function Start-DetachedLocalProcess {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory,
        [string]$OutputPath,
        [string]$ErrorPath
    )

    $parameters = Get-DetachedProcessParameters `
        -FilePath $FilePath `
        -Arguments $Arguments `
        -WorkingDirectory $WorkingDirectory `
        -OutputPath $OutputPath `
        -ErrorPath $ErrorPath
    return Start-Process @parameters -PassThru
}

function Stop-LocalProcessTree {
    param([Diagnostics.Process]$Process)

    if ($null -eq $Process -or $Process.HasExited) {
        return
    }

    & taskkill.exe /PID $Process.Id /T /F 2>$null | Out-Null
    $Process.WaitForExit(5000) | Out-Null
    if (-not $Process.HasExited) {
        throw "Failed to stop local process tree with PID $($Process.Id)"
    }
}

function Get-LocalProcessStatePath {
    param([string]$ProjectRoot)

    return Join-Path $ProjectRoot 'logs\local-processes.json'
}

function ConvertTo-LocalProcessRecord {
    param([Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return $null
    }
    return [ordered]@{
        Id = $Process.Id
        StartTimeFileTimeUtc = $Process.StartTime.ToFileTimeUtc()
    }
}

function Write-LocalProcessState {
    param(
        [string]$Path,
        [string]$ComposeProjectName,
        [Diagnostics.Process]$BackendProcess,
        [Diagnostics.Process]$FrontendProcess
    )

    $state = [ordered]@{
        ComposeProjectName = $ComposeProjectName
        Backend = ConvertTo-LocalProcessRecord $BackendProcess
        Frontend = ConvertTo-LocalProcessRecord $FrontendProcess
    }
    $temporaryPath = "$Path.tmp"
    $state | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $temporaryPath
    Move-Item -Force -LiteralPath $temporaryPath -Destination $Path
}

function Stop-LocalTrackedProcess {
    param($Record, [string]$Name)

    if ($null -eq $Record) {
        return
    }
    $process = Get-Process -Id ([int]$Record.Id) -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return
    }
    if ($process.StartTime.ToFileTimeUtc() -ne [long]$Record.StartTimeFileTimeUtc) {
        throw "Refusing to stop ${Name}: PID $($Record.Id) belongs to a different process"
    }
    Stop-LocalProcessTree $process
}

function Stop-LocalTrackedProcesses {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }
    try {
        $state = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
    } catch {
        throw "Cannot read local process state: $Path"
    }

    Stop-LocalTrackedProcess $state.Frontend 'frontend'
    Stop-LocalTrackedProcess $state.Backend 'backend'
    return $state.ComposeProjectName
}

function Test-ListeningPort {
    param([int]$Port)

    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1)
}

function Assert-ListeningPortAvailable {
    param([int]$Port, [string]$ServiceName)

    if (Test-ListeningPort $Port) {
        throw "Cannot start ${ServiceName}: port $Port is already in use. Stop the existing process and run this script again so the current local configuration is applied."
    }
}
