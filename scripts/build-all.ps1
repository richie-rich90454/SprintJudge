# Builds everything for production: frontend SPA, then the fat jar that
# bundles it. Compatible with Windows PowerShell 5.1 and 7+.
param([switch]$SkipFrontend)
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_dotenv.ps1"
$root = Split-Path $PSScriptRoot -Parent

# Stop any running SprintJudge instance — Windows locks the jar during repackage.
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    $cmdline = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmdline -and $cmdline -match 'sprintjudge\.jar') {
        Write-Host "[build] Stopping running instance (PID $($_.Id))"
        Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep 1

if (-not $SkipFrontend) {
    Write-Host "== frontend =="
    $frontend = Join-Path $root "frontend"
    if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
        Write-Host "installing frontend dependencies (first run)..."
        npm install --prefix $frontend
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    npm run build --prefix $frontend
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "== backend fat jar =="
& "$root\mvnw.cmd" -q clean package "-DskipTests"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jar = Join-Path $root "target\sprintjudge.jar"
$dist = Join-Path $root "frontend\dist\index.html"
$jarSize = [math]::Round((Get-Item $jar).Length / 1MB, 1)
if ((Test-Path $jar) -and ($jarSize -gt 10)) {
    Write-Host "BUILD OK  jar=${jarSize} MB (fat jar with SPA + library)"
} else {
    Write-Host "BUILD INCOMPLETE — jar is ${jarSize} MB (expected >10 MB for fat jar)"
    exit 1
}
