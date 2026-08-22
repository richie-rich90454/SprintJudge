# Start the SprintJudge frontend dev server (installs deps on first run).
$frontend = Join-Path (Split-Path $PSScriptRoot -Parent) "frontend"
if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
    Write-Host "Installing frontend dependencies..."
    npm install --prefix $frontend
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
npm run dev --prefix $frontend @args
