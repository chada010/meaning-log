$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
$backendPath = Join-Path $projectRoot 'meaning-log-backend'
$frontendPath = Join-Path $projectRoot 'meaning-log-frontend'
$logPath = Join-Path $projectRoot 'logs'
$commonScriptPath = Join-Path $PSScriptRoot 'local-dev-common.ps1'

. $commonScriptPath

$processStatePath = Get-LocalProcessStatePath $projectRoot

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

function Wait-ForComposeHealth {
    param([string[]]$ComposeArguments, [string]$Service, [int]$TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $containerId = & docker @ComposeArguments ps -q $Service
        Assert-NativeCommandSucceeded "Inspecting Docker Compose service $Service" $LASTEXITCODE
        if ($containerId) {
            $health = & docker inspect --format '{{.State.Health.Status}}' $containerId
            Assert-NativeCommandSucceeded "Inspecting Docker health for service $Service" $LASTEXITCODE
            if ($health -eq 'healthy') {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    return $false
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
if (Test-Path -LiteralPath $processStatePath) {
    throw 'Local process state already exists. Run scripts\stop-local.ps1 before starting again.'
}

& docker info --format '{{.ServerVersion}}' | Out-Null
Assert-NativeCommandSucceeded 'Checking Docker availability' $LASTEXITCODE
$localEnv = Get-LocalEnvironmentValues $projectRoot
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
Assert-MailFromAddress $localEnv.MAIL_FROM

Assert-ListeningPortAvailable 8080 'backend'
Assert-ListeningPortAvailable 5173 'frontend'

$composeProjectNameOverride = Get-LocalEnvValue $localEnv 'LOCAL_COMPOSE_PROJECT_NAME' ''
$composeArguments = Get-LocalComposeArguments $projectRoot $composeProjectNameOverride
$composeProjectName = Get-LocalComposeProjectName $projectRoot $composeProjectNameOverride

New-Item -ItemType Directory -Force -Path $logPath | Out-Null
& docker @composeArguments up -d
Assert-NativeCommandSucceeded 'Starting Docker Compose dependencies' $LASTEXITCODE

foreach ($service in 'mysql', 'redis') {
    if (-not (Wait-ForComposeHealth $composeArguments $service 60)) {
        throw "Docker service did not become healthy: $service"
    }
}

$backendProcess = $null
$frontendProcess = $null
$startupSucceeded = $false
try {
    $backendLog = Join-Path $logPath 'backend-local.log'
    $backendErrorLog = Join-Path $logPath 'backend-local-error.log'
    $backendExecutable = Join-Path $backendPath 'mvnw.cmd'
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
    $backendProcess = Invoke-WithProcessEnvironment $backendEnvironment {
        Start-DetachedLocalProcess `
            -FilePath $backendExecutable `
            -Arguments @('spring-boot:run') `
            -WorkingDirectory $backendPath `
            -OutputPath $backendLog `
            -ErrorPath $backendErrorLog
    }
    Write-LocalProcessState $processStatePath $composeProjectName $backendProcess $null
    if (-not (Wait-ForLocalProcessListeningPort $backendProcess 8080 45)) {
        throw "Backend did not start. See $backendLog and $backendErrorLog"
    }

    $frontendLog = Join-Path $logPath 'frontend-local.log'
    $frontendErrorLog = Join-Path $logPath 'frontend-local-error.log'
    $npmExecutable = (Get-Command npm.cmd -ErrorAction Stop).Source
    $frontendProcess = Start-DetachedLocalProcess `
        -FilePath $npmExecutable `
        -Arguments @('run', 'dev') `
        -WorkingDirectory $frontendPath `
        -OutputPath $frontendLog `
        -ErrorPath $frontendErrorLog
    Write-LocalProcessState $processStatePath $composeProjectName $backendProcess $frontendProcess
    if (-not (Wait-ForLocalProcessListeningPort $frontendProcess 5173 30)) {
        throw "Frontend did not start. See $frontendLog and $frontendErrorLog"
    }
    $startupSucceeded = $true
} finally {
    if (-not $startupSucceeded) {
        Stop-LocalProcessTree $frontendProcess
        Stop-LocalProcessTree $backendProcess
        Remove-Item -ErrorAction SilentlyContinue -LiteralPath $processStatePath
    }
}

Write-Host 'Local services are ready:'
Write-Host '  Frontend: http://localhost:5173'
Write-Host '  Backend:  http://localhost:8080'
Write-Host "  Docker project: $composeProjectName"
