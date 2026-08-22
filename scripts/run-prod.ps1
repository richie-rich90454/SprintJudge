# Build (once) and run OpenQuiz with the production profile on Windows.
# Requires: JDK 25 on PATH. Optional env: OPENQUIZ_DB_PATH, OPENQUIZ_PORT,
# OPENQUIZ_EXECUTOR_MODE, OPENQUIZ_MS_CLIENT_ID / _SECRET / _TENANT_ID.
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    & "$root\mvnw.cmd" -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $jar = Join-Path $root "target\openquiz.jar"
    if (-not (Test-Path $jar)) { Write-Error "Jar not found at $jar"; exit 1 }
    $heap = if ($env:OPENQUIZ_HEAP) { $env:OPENQUIZ_HEAP } else { "1g" }
    Write-Host "Starting OpenQuiz (prod) on http://localhost:$($env:OPENQUIZ_PORT ?? '8080') heap=$heap"
    & java "-XX:+UseZGC" `
           "-Xms$heap" "-Xmx$heap" `
           "-XX:+UseStringDeduplication" `
           "-XX:+PerfDisableSharedMem" `
           "-XX:+UseCompactObjectHeaders" `
           -jar $jar "--spring.profiles.active=prod" @args
} finally {
    Pop-Location
}
