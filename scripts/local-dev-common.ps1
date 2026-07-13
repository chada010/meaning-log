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
    Start-Process @parameters
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
