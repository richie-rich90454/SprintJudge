# Verifies a real production-profile launch of SprintJudge on Windows.
# Builds (unless -SkipBuild), boots the jar detached, probes HTTP + WebSocket,
# prints a PASS/FAIL table, always stops the process. Exit 0 = all pass.
param(
    [int]$Port = 8091,
    [switch]$SkipBuild
)
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
$runDir = Join-Path $root "target\prodtest"
New-Item -ItemType Directory -Force $runDir | Out-Null
$results = [System.Collections.Generic.List[string]]::new()
function Pass($n) { $results.Add("PASS  $n"); Write-Host "  [PASS] $n" -ForegroundColor Green }
function Fail($n, $d) { $results.Add("FAIL  $n :: $d"); Write-Host "  [FAIL] $n :: $d" -ForegroundColor Red }

# 0) build
if (-not $SkipBuild) {
    Write-Host "== packaging =="
    & "$root\mvnw.cmd" -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { Fail "package" "mvnw exited $LASTEXITCODE"; $results | ForEach-Object { $_ }; exit 1 }
}

# 1) stop any prior instance
$pidFile = Join-Path $runDir "pid.txt"
if (Test-Path $pidFile) {
    $old = Get-Content $pidFile -ErrorAction SilentlyContinue
    if ($old) { Stop-Process -Id $old -Force -ErrorAction SilentlyContinue }
}
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'sprintjudge\.jar' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep 2

# 2) fresh database
$db = Join-Path $runDir "sprintjudge.db"
Remove-Item "$db*" -Force -ErrorAction SilentlyContinue

# 3) launch detached
$outLog = Join-Path $runDir "out.log"; $errLog = Join-Path $runDir "err.log"
Remove-Item $outLog, $errLog -Force -ErrorAction SilentlyContinue
$env:SPRINTJUDGE_PORT = "$Port"
$env:SPRINTJUDGE_DB_PATH = $db
$p = Start-Process java `
    -ArgumentList '-XX:+UseZGC','-Xms512m','-Xmx512m','-XX:+UseStringDeduplication',
                  '-jar',(Join-Path $root 'target\sprintjudge.jar'),'--spring.profiles.active=prod' `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
    -PassThru -WindowStyle Hidden
$p.Id | Set-Content $pidFile
Write-Host "== launched pid $($p.Id) on :$Port =="

# 4) wait for boot
$booted = $false
for ($i = 0; $i -lt 40; $i++) {
    try {
        $r = Invoke-WebRequest "http://localhost:$Port/api/public/quizzes" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) { $booted = $true; break }
    } catch { Start-Sleep -Milliseconds 1500 }
}

if (-not $booted) {
    Fail "boot" "service did not become healthy"
    Get-Content $outLog -Tail 25 -ErrorAction SilentlyContinue
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    $results | ForEach-Object { $_ }; exit 1
}
Pass "boot (healthy in ~$([math]::Round(($i+1)*1.5,0))s)"

# 5a) public REST
try {
    $r = Invoke-WebRequest "http://localhost:$Port/api/public/quizzes" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -eq 200 -and $r.Content.Trim() -eq "[]") { Pass "public REST 200 []" }
    else { Fail "public REST" "status=$($r.StatusCode) body=$($r.Content)" }
} catch { Fail "public REST" $_.Exception.Message }

# 5b) admin surface must NOT be anonymous
$adminCode = 0
try {
    $req = [System.Net.HttpWebRequest]::Create("http://localhost:$Port/api/admin/metrics")
    $req.AllowAutoRedirect = $false; $req.Timeout = 5000
    $resp = $req.GetResponse(); $adminCode = [int]$resp.StatusCode; $resp.Close()
} catch [System.Net.WebException] {
    if ($_.Exception.Response) { $adminCode = [int]$_.Exception.Response.StatusCode }
    else { Fail "admin auth wall" $_.Exception.Message }
}
if ($adminCode -ge 300 -and $adminCode -le 399 -or $adminCode -in 401,403) { Pass "admin auth wall ($adminCode)" }
elseif ($adminCode -eq 0) { }
else { Fail "admin auth wall" "metrics returned $adminCode anonymously" }

# 5c) WebSocket join with an invalid PIN must yield an ERROR frame
try {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource(8000)
    $ws.ConnectAsync([Uri]"ws://localhost:$Port/ws", $cts.Token).Wait()
    $bytes = [Text.Encoding]::UTF8.GetBytes('{"type":"JOIN","pin":"000000","name":"Probe"}')
    $ws.SendAsync([ArraySegment[byte]]::new($bytes), 'Text', $true, $cts.Token).Wait()
    $buf = New-Object byte[] 8192
    $res = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $cts.Token).Result
    $msg = [Text.Encoding]::UTF8.GetString($buf, 0, $res.Count)
    if ($msg -match '"type"\s*:\s*"ERROR"' -and $msg -match 'Invalid PIN') { Pass "websocket ERROR frame" }
    else { Fail "websocket ERROR frame" $msg }
    $ws.Dispose()
} catch { Fail "websocket probe" $_.Exception.Message }

# 6) shutdown
Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
Write-Host "== summary =="
$results | ForEach-Object { $_ }
if (($results | Where-Object { $_ -like 'FAIL*' }).Count -gt 0) { exit 1 }
Write-Host "ALL CHECKS PASSED" -ForegroundColor Green
exit 0
