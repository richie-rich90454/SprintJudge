# Shared .env parser — returns a hashtable of KEY=VALUE pairs.
# Callers use these values directly (as JVM args or explicit env sets)
# instead of relying on PowerShell env: drive scoping.
function Get-SprintJudgeConfig {
    param([string]$Root)
    $config = @{}
    foreach ($candidate in @((Join-Path $Root ".env"), ".env")) {
        if (Test-Path $candidate) {
            foreach ($raw in (Get-Content $candidate)) {
                $line = $raw.Trim()
                if ($line.StartsWith("#") -or -not $line.Contains("=")) { continue }
                $eq = $line.IndexOf("=")
                if ($eq -lt 1) { continue }
                $key = $line.Substring(0, $eq).Trim()
                $val = $line.Substring($eq + 1).Trim().Trim('"').Trim("'")
                if (-not [string]::IsNullOrEmpty($key) -and -not $config.ContainsKey($key)) {
                    $config[$key] = $val
                }
            }
            break
        }
    }
    return $config
}

function Get-ConfigOrDefault {
    param([hashtable]$Config, [string]$Key, [string]$Default)
    if ($Config.ContainsKey($Key) -and -not [string]::IsNullOrEmpty($Config[$Key])) {
        return $Config[$Key]
    }
    return $Default
}
