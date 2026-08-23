# Builds (when needed) and runs SprintJudge production on Windows.
# Compatible with Windows PowerShell 5.1 and 7+.
# Reads configuration from .env (project root or CWD) and passes values
# directly to the JVM as system properties — no env var dependency.
param([switch]$Build)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
. "$root\scripts\_config.ps1"

$config = Get-SprintJudgeConfig -Root $root
$dbPath    = Get-ConfigOrDefault $config "SPRINTJUDGE_DB_PATH"    ""
$port      = Get-ConfigOrDefault $config "SPRINTJUDGE_PORT"       "8080"
$execMode  = Get-ConfigOrDefault $config "SPRINTJUDGE_EXECUTOR_MODE" "native"
$clientID  = Get-ConfigOrDefault $config "SPRINTJUDGE_MS_CLIENT_ID" ""
$clientSec = Get-ConfigOrDefault $config "SPRINTJUDGE_MS_CLIENT_SECRET" ""
$tenantId  = Get-ConfigOrDefault $config "SPRINTJUDGE_MS_TENANT_ID" "common"
$redirect  = Get-ConfigOrDefault $config "SPRINTJUDGE_OAUTH2_REDIRECT_URI" ""
$admins    = Get-ConfigOrDefault $config "SPRINTJUDGE_ADMIN_EMAILS" ""

$jar = Join-Path $root "target\sprintjudge.jar"
if ($Build -or (-not (Test-Path $jar))) {
    & "$root\scripts\build-all.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if ($env:SPRINTJUDGE_HEAP) { $heap = $env:SPRINTJUDGE_HEAP } else { $heap = "1g" }
Write-Host "Starting SprintJudge (prod) on http://localhost:$port heap=$heap"

# Build JVM args — each .env value becomes an explicit system property
$jvmArgs = @(
    "-XX:+UseZGC", "-Xms$heap", "-Xmx$heap",
    "-XX:+UseStringDeduplication", "-XX:+PerfDisableSharedMem",
    "-XX:+UseCompactObjectHeaders"
)
$appArgs = @("--spring.profiles.active=prod")

# Only pass properties that have actual values (let yml defaults handle the rest)
if ($dbPath)   { $appArgs += "--sprintjudge.db.path=$dbPath" }
if ($port)     { $appArgs += "--server.port=$port" }
if ($execMode) { $appArgs += "--sprintjudge.executor.mode=$execMode" }
if ($clientID) { $appArgs += "--spring.security.oauth2.client.registration.microsoft.client-id=$clientID" }
if ($clientSec){ $appArgs += "--spring.security.oauth2.client.registration.microsoft.client-secret=$clientSec" }
if ($tenantId) { $appArgs += "--spring.security.oauth2.client.provider.microsoft.authorization-uri=https://login.microsoftonline.com/$tenantId/oauth2/v2.0/authorize" }
if ($tenantId) { $appArgs += "--spring.security.oauth2.client.provider.microsoft.token-uri=https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token" }
if ($redirect) { $appArgs += "--spring.security.oauth2.client.registration.microsoft.redirect-uri=$redirect" }
if ($admins)   { $appArgs += "--sprintjudge.admin-emails=$admins" }

& java @jvmArgs -jar $jar @appArgs @args
