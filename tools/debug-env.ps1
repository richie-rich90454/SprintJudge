# Debug test for dotenv parsing
$testFile = Join-Path $env:TEMP "sprintjudge-test-env.txt"
Set-Content -Path $testFile -Value @(
    "# comment"
    "SPRINTJUDGE_TEST_KEY=hello_world"
    "SPRINTJUDGE_PORT=9999"
) -Encoding ASCII

Write-Host "--- raw content ---"
Get-Content $testFile | ForEach-Object { Write-Host "  [$_]" }

Write-Host "--- parsing ---"
foreach ($raw in (Get-Content $testFile)) {
    $line = $raw.Trim()
    if ($line.StartsWith("#") -or -not $line.Contains("=")) {
        Write-Host "  SKIP: [$line]"
        continue
    }
    $eq = $line.IndexOf("=")
    $key = $line.Substring(0, $eq).Trim()
    $val = $line.Substring($eq + 1).Trim().Trim('"').Trim("'")
    Write-Host "  SET: $key=[$val]"
    [Environment]::SetEnvironmentVariable($key, $val, "Process")
}

Write-Host "--- verify ---"
Write-Host "KEY=[$env:SPRINTJUDGE_TEST_KEY]"
Write-Host "PORT=[$env:SPRINTJUDGE_PORT]"
Remove-Item $testFile
