function Get-LocalComposeProjectName {
    param([string]$ProjectRoot)

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
    param([string]$ProjectRoot)

    $projectName = Get-LocalComposeProjectName $ProjectRoot
    return @('compose', '--project-directory', $ProjectRoot, '--project-name', $projectName)
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
    if ($mailAddress.Host.EndsWith('.example', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'MAIL_FROM in .env must not use the example domain'
    }
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
