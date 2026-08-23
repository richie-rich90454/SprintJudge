# Builds everything for production: frontend SPA, then the fat jar that
. "$PSScriptRoot\_dotenv.ps1"
# bundles it. Compatible with Windows PowerShell 5.1 and 7+.
# Optional: -SkipFrontend to reuse an existing frontend/dist.
param([switch]$SkipFrontend)
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent

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
& "$root\mvnw.cmd" -q -DskipTests package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jar = Join-Path $root "target\sprintjudge.jar"
$dist = Join-Path $root "frontend\dist\index.html"
if ((Test-Path $jar) -and (Test-Path $dist)) {
    Write-Host ("BUILD OK  jar={0:N1} MB" -f ((Get-Item $jar).Length / 1MB))
} else {
    Write-Host "BUILD INCOMPLETE (jar or frontend dist missing)"
    exit 1
}
