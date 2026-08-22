# Build (once) and run SprintJudge with the production profile on Windows.
# Requires: JDK 25 on PATH. Optional env: SPRINTJUDGE_DB_PATH, SPRINTJUDGE_PORT,
# SPRINTJUDGE_EXECUTOR_MODE, SPRINTJUDGE_MS_CLIENT_ID / _SECRET / _TENANT_ID.
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    & "$root\mvnw.cmd" -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $jar = Join-Path $root "target\sprintjudge.jar"
    if (-not (Test-Path $jar)) { Write-Error "Jar not found at $jar"; exit 1 }
    $heap = if ($env:SPRINTJUDGE_HEAP) { $env:SPRINTJUDGE_HEAP } else { "1g" }
    Write-Host "Starting SprintJudge (prod) on http://localhost:$($env:SPRINTJUDGE_PORT ?? '8080') heap=$heap"
    & java "-XX:+UseZGC" `
           "-Xms$heap" "-Xmx$heap" `
           "-XX:+UseStringDeduplication" `
           "-XX:+PerfDisableSharedMem" `
           "-XX:+UseCompactObjectHeaders" `
           -jar $jar "--spring.profiles.active=prod" @args
} finally {
    Pop-Location
}
