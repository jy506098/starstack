# ===========================================
# Restart StarStack MC server
# Kill existing Java, then re-launch via start.bat
# ===========================================

$mcDir = $PSScriptRoot
Set-Location $mcDir

Write-Host "[1/3] Stop existing Java processes..." -ForegroundColor Cyan
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  Killing PID $($_.Id)"
    Stop-Process -Id $_.Id -Force
}

Write-Host "[2/3] Wait for port 25565 to release..." -ForegroundColor Cyan
$maxWait = 15
$waited = 0
while ($waited -lt $maxWait) {
    $listening = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue
    if (-not $listening) { break }
    Start-Sleep -Seconds 1
    $waited++
}
if ($waited -ge $maxWait) {
    Write-Host "  [WARN] Port 25565 still bound after ${maxWait}s" -ForegroundColor Yellow
}

Write-Host "[3/3] Launching server..." -ForegroundColor Cyan
$startScript = Join-Path $mcDir "start.bat"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c `"$startScript`""
$psi.WorkingDirectory = $mcDir
$psi.UseShellExecute = $true
$psi.WindowStyle = "Normal"
[System.Diagnostics.Process]::Start($psi) | Out-Null

Write-Host "  Waiting 8s for boot..." -ForegroundColor Gray
Start-Sleep -Seconds 8

$proc = Get-Process java -ErrorAction SilentlyContinue
if ($proc) {
    $listen = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue
    if ($listen) {
        Write-Host "  OK - Server listening on $($listen.LocalAddress):$($listen.LocalPort)" -ForegroundColor Green
    } else {
        Write-Host "  WARN - Java running but 25565 not bound. Check the new cmd window for errors." -ForegroundColor Yellow
    }
} else {
    Write-Host "  FAIL - Java not running. Check the new cmd window." -ForegroundColor Red
}