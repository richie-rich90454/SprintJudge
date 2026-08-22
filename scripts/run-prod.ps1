# Build (once) and run OpenQuiz with the production profile on Windows.
# Compatible with Windows PowerShell 5.1 and PowerShell 7+.
# Requires: JDK 25 on PATH. Optional env: SPRINTJUDGE_DB_PATH, SPRINTJUDGE_PORT,
# SPRINTJUDGE_EXECUTOR_MODE, SPRINTJUDGE_MS_CLIENT_ID / _SECRET / _TENANT_ID.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    & "$root\mvnw.cmd" -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $jar = Join-Path $root "target\sprintjudge.jar"
    if (-not (Test-Path $jar)) { Write-Error "Jar not found at $jar"; exit 1 }

    if ($env:SPRINTJUDGE_HEAP) { $heap = $env:SPRINTJUDGE_HEAP } else { $heap = "1g" }
    if ($env:SPRINTJUDGE_PORT) { $port = $env:SPRINTJUDGE_PORT } else { $port = "8080" }
    Write-Host "Starting SprintJudge (prod) on http://localhost:$port heap=$heap"

    & java "-XX:+UseZGC" "-Xms$heap" "-Xmx$heap" "-XX:+UseStringDeduplication" "-XX:+PerfDisableSharedMem" "-XX:+UseCompactObjectHeaders" -jar $jar "--spring.profiles.active=prod" @args
} finally {
    Pop-Location
}
