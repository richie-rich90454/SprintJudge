# OpenQuiz Windows environment checker.
# Verifies every tool needed to build, run and test the project natively.
$ErrorActionPreference = "Continue"
$fail = $false

function Check($name, $script) {
    try {
        $raw = & $script 2>$null
        if ($LASTEXITCODE -ne 0 -and $null -eq $raw) { throw "not found" }
        $first = (($raw | Out-String).Trim() -split "\r?\n" | Select-Object -First 1)
        Write-Host ("[OK]   {0}: {1}" -f $name, $first)
    } catch {
        Write-Host ("[MISS] {0}" -f $name)
        return $false
    }
    return $true
}

function VersionOf($cmd, $toolArgs) {
    return (Check $cmd { & $cmd @toolArgs 2>&1 })
}

Write-Host "== OpenQuiz environment check ==" -NoNewline; Write-Host ""

$java = VersionOf "Java" @("-version")
$mavenOk = Test-Path "$PSScriptRoot\..\.mvn\wrapper\maven-wrapper.properties"
if ($mavenOk) { Write-Host "[OK]   Maven wrapper (.mvn/wrapper present — mvnw.cmd needs no Maven install)" }
else { Write-Host "[MISS] Maven wrapper"; $fail = $true }

VersionOf "Node" @("--version") | Out-Null
VersionOf "npm" @("--version") | Out-Null

# Native executor toolchains (openquiz.executor.mode=native)
foreach ($tool in @("gcc", "g++", "javac", "python", "node")) {
    $found = Get-Command $tool -ErrorAction SilentlyContinue
    if ($found) { Write-Host ("[OK]   native toolchain: {0}" -f $tool) }
    else { Write-Host ("[WARN] native toolchain missing: {0} (needed only for that OJ language)" -f $tool) }
}

# WSL alternative (openquiz.executor.mode=wsl)
$wsl = Get-Command wsl -ErrorAction SilentlyContinue
if ($wsl) {
    Write-Host "[OK]   WSL available (set OPENQUIZ_EXECUTOR_MODE=wsl for isolation-parity runs)"
} else {
    Write-Host "[WARN] WSL not installed (optional; native mode covers Windows testing)"
}

# Real-time antivirus can silently kill or delete freshly compiled unsigned
# binaries (classic MinGW-on-Windows trap): gcc exits 0 but the .exe vanishes,
# or cc1/as die with no stderr. Guide the fix instead of failing mysteriously.
try {
    $rtp = (Get-MpComputerStatus -ErrorAction Stop).RealTimeProtectionEnabled
    if ($rtp) {
        $tmpDir = Join-Path (Split-Path $PSScriptRoot -Parent) "executor\tmp"
        Write-Host "[WARN] Real-time antivirus is ON."
        Write-Host ("       If OJ compiles fail silently or binaries vanish, add an exclusion for:")
        Write-Host ("       {0}" -f $tmpDir)
    } else {
        Write-Host "[OK]   Real-time protection off (compiled OJ binaries unaffected)"
    }
} catch {
    Write-Host "[INFO] Antivirus status unavailable (Get-MpComputerStatus requires the Defender module)"
}

if (-not (Test-Path "$PSScriptRoot\..\frontend\node_modules")) {
    Write-Host "[INFO] frontend deps not installed yet -> scripts\dev-frontend.ps1 installs on first run"
}

if ($fail) { Write-Host "`nRESULT: required tools missing." ; exit 1 }
Write-Host "`nRESULT: environment ready."
