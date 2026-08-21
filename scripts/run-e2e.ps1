# Install Playwright browsers and run the E2E suite against the Vite dev server.
$frontend = Join-Path (Split-Path $PSScriptRoot -Parent) "frontend"
Write-Host "Ensuring Playwright browsers are installed..."
npx --prefix $frontend playwright install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
npm run test:e2e --prefix $frontend @args
exit $LASTEXITCODE
