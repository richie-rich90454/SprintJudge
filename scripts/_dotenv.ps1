# Shared .env loader — dot-source from other scripts.
# Returns a hashtable of KEY=VALUE pairs from the first .env file found.
# Does NOT set environment variables directly; the caller does that so vars
# land in the correct scope.

function Get-DotEnvValues {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @{} }
    $result = @{}
    foreach ($raw in Get-Content $Path) {
        $line = $raw.Trim()
        if ($line.StartsWith("#") -or -not $line.Contains("=")) { continue }
        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { continue }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim().Trim('"').Trim("'")
        if (-not [string]::IsNullOrEmpty($key)) { $result[$key] = $val }
    }
    return $result
}

function Import-DotEnvIntoProcess {
    $_root = Split-Path $PSScriptRoot -Parent
    $candidates = @(
        (Join-Path $_root ".env"),
        (Join-Path (Get-Location).Path ".env")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            Write-Host "[env] Loading $(Resolve-Path $candidate)"
            $values = Get-DotEnvValues $candidate
            foreach ($entry in $values.GetEnumerator()) {
                $existing = [Environment]::GetEnvironmentVariable($entry.Key)
                if ([string]::IsNullOrEmpty($existing)) {
                    Set-Item -Path ("env:\" + $entry.Key) -Value $entry.Value
                    Write-Host "[env]   $($entry.Key) = $($entry.Value)"
                }
            }
            break   # first .env wins
        }
    }
}

Import-DotEnvIntoProcess
