# Run the full backend test suite on Windows (JUnit 5 + Mockito, no WSL needed).
. "$PSScriptRoot\\_dotenv.ps1"
$root = Split-Path $PSScriptRoot -Parent
& "$root\mvnw.cmd" test @args
exit $LASTEXITCODE
