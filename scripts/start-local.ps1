$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
$backendPath = Join-Path $projectRoot 'meaning-log-backend'
$frontendPath = Join-Path $projectRoot 'meaning-log-frontend'
$logPath = Join-Path $projectRoot 'logs'

function Get-DotEnvValues {
    param([string]$Path)

    $values = @{}
    Get-Content $Path | ForEach-Object {
        if ($_.Trim() -and -not $_.Trim().StartsWith('#')) {
            $pair = $_.Split('=', 2)
            if ($pair.Length -eq 2) {
                $values[$pair[0].Trim()] = $pair[1].Trim()
            }
        }
    }
    return $values
}

function Get-LocalEnvValue {
    param(
        [hashtable]$Values,
        [string]$Name,
        [string]$DefaultValue
    )

    if ($Values.ContainsKey($Name) -and $Values[$Name]) {
        return $Values[$Name]
    }
    return $DefaultValue
}

function Test-ListeningPort {
    param([int]$Port)

    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1)
}

function Wait-ForListeningPort {
    param([int]$Port, [int]$TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-ListeningPort $Port) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Wait-ForComposeHealth {
    param([string]$Service, [int]$TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $containerId = docker compose --project-directory $projectRoot ps -q $Service
        if ($containerId) {
            $health = docker inspect --format '{{.State.Health.Status}}' $containerId
            if ($health -eq 'healthy') {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Start-DetachedPowerShell {
    param([string]$Script)

    $bytes = [Text.Encoding]::Unicode.GetBytes($Script)
    $encodedScript = [Convert]::ToBase64String($bytes)
    Start-Process powershell.exe -ArgumentList '-NoProfile', '-EncodedCommand', $encodedScript -WindowStyle Hidden
}

function Invoke-WithProcessEnvironment {
    param(
        [hashtable]$Variables,
        [scriptblock]$Action
    )

    $previousValues = @{}
    try {
        foreach ($entry in $Variables.GetEnumerator()) {
            $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
        & $Action
    } finally {
        foreach ($entry in $previousValues.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
    }
}

if (-not (Test-Path $envPath)) {
    throw "Missing local configuration: $envPath"
}
if (-not (Test-Path (Join-Path $frontendPath 'node_modules'))) {
    throw 'Frontend dependencies are missing. Run npm install in meaning-log-frontend first.'
}

$localEnv = Get-DotEnvValues $envPath
$requiredNames = @(
    'MYSQL_DATABASE',
    'MYSQL_USER',
    'MYSQL_PASSWORD',
    'MYSQL_PORT',
    'REDIS_PORT',
    'MAIL_HOST',
    'MAIL_PORT',
    'MAIL_USERNAME',
    'MAIL_PASSWORD',
    'MAIL_FROM'
)
foreach ($name in $requiredNames) {
    if (-not $localEnv[$name]) {
        throw "Missing $name in .env"
    }
}
if ($localEnv.MAIL_PASSWORD -eq 'your-resend-api-key') {
    throw 'MAIL_PASSWORD in .env still contains the example placeholder'
}

$mailPort = 0
if (-not [int]::TryParse($localEnv.MAIL_PORT, [ref]$mailPort) -or $mailPort -lt 1 -or $mailPort -gt 65535) {
    throw 'MAIL_PORT in .env must be an integer between 1 and 65535'
}
try {
    $null = [Net.Mail.MailAddress]::new($localEnv.MAIL_FROM)
} catch {
    throw 'MAIL_FROM in .env must be a valid email address'
}

docker info --format '{{.ServerVersion}}' | Out-Null
New-Item -ItemType Directory -Force -Path $logPath | Out-Null
docker compose --project-directory $projectRoot up -d

foreach ($service in 'mysql', 'redis') {
    if (-not (Wait-ForComposeHealth $service 60)) {
        throw "Docker service did not become healthy: $service"
    }
}

if (-not (Test-ListeningPort 8080)) {
    $backendLog = Join-Path $logPath 'backend-local.log'
    $backendScript = "Set-Location '$backendPath'; & '.\mvnw.cmd' spring-boot:run *> '$backendLog'"
    $backendEnvironment = @{
        SPRING_PROFILES_ACTIVE = 'local'
        SPRING_DATASOURCE_USERNAME = $localEnv.MYSQL_USER
        SPRING_DATASOURCE_PASSWORD = $localEnv.MYSQL_PASSWORD
        SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:$($localEnv.MYSQL_PORT)/$($localEnv.MYSQL_DATABASE)?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
        SPRING_DATA_REDIS_HOST = 'localhost'
        SPRING_DATA_REDIS_PORT = $localEnv.REDIS_PORT
        MAIL_HOST = $localEnv.MAIL_HOST
        MAIL_PORT = $localEnv.MAIL_PORT
        MAIL_USERNAME = $localEnv.MAIL_USERNAME
        MAIL_PASSWORD = $localEnv.MAIL_PASSWORD
        MAIL_FROM = $localEnv.MAIL_FROM
        MAIL_SMTP_SSL_ENABLE = Get-LocalEnvValue $localEnv 'MAIL_SMTP_SSL_ENABLE' 'true'
        MAIL_SMTP_STARTTLS_ENABLE = Get-LocalEnvValue $localEnv 'MAIL_SMTP_STARTTLS_ENABLE' 'false'
        MAIL_CONNECTION_TIMEOUT_MS = Get-LocalEnvValue $localEnv 'MAIL_CONNECTION_TIMEOUT_MS' '10000'
        MAIL_READ_TIMEOUT_MS = Get-LocalEnvValue $localEnv 'MAIL_READ_TIMEOUT_MS' '10000'
        MAIL_WRITE_TIMEOUT_MS = Get-LocalEnvValue $localEnv 'MAIL_WRITE_TIMEOUT_MS' '10000'
    }
    Invoke-WithProcessEnvironment $backendEnvironment {
        Start-DetachedPowerShell $backendScript
    }
}

if (-not (Test-ListeningPort 5173)) {
    $frontendLog = Join-Path $logPath 'frontend-local.log'
    $frontendScript = "Set-Location '$frontendPath'; & npm.cmd run dev *> '$frontendLog'"
    Start-DetachedPowerShell $frontendScript
}

if (-not (Wait-ForListeningPort 8080 45)) {
    throw "Backend did not start. See $logPath\backend-local.log"
}
if (-not (Wait-ForListeningPort 5173 30)) {
    throw "Frontend did not start. See $logPath\frontend-local.log"
}

Write-Host 'Local services are ready:'
Write-Host '  Frontend: http://localhost:5173'
Write-Host '  Backend:  http://localhost:8080'
