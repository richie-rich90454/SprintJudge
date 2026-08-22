# Builds (when needed) and runs SprintJudge production on Windows.
# Compatible with Windows PowerShell 5.1 and 7+.
# Optional env: SPRINTJUDGE_DB_PATH, SPRINTJUDGE_PORT, SPRINTJUDGE_EXECUTOR_MODE,
# SPRINTJUDGE_MS_CLIENT_ID / _SECRET / _TENANT_ID. A .env next to the jar also works.
param([switch]$Build)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root "target\sprintjudge.jar"
$needsBuild = $Build -or (-not (Test-Path $jar))

if ($needsBuild) {
    & "$root\scripts\build-all.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if ($env:SPRINTJUDGE_HEAP) { $heap = $env:SPRINTJUDGE_HEAP } else { $heap = "1g" }
if ($env:SPRINTJUDGE_PORT) { $port = $env:SPRINTJUDGE_PORT } else { $port = "8080" }
Write-Host "Starting SprintJudge (prod) on http://localhost:$port heap=$heap"

& java "-XX:+UseZGC" "-Xms$heap" "-Xmx$heap" "-XX:+UseStringDeduplication" "-XX:+PerfDisableSharedMem" "-XX:+UseCompactObjectHeaders" -jar $jar "--spring.profiles.active=prod" @args
