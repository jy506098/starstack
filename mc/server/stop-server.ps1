# ===========================================
# Stop StarStack MC server (kill Java processes)
# ===========================================

Write-Host "Stopping StarStack MC server..." -ForegroundColor Cyan
$procs = Get-Process java -ErrorAction SilentlyContinue
if (-not $procs) {
    Write-Host "No Java process running. Server already stopped." -ForegroundColor Yellow
    exit 0
}
$procs | ForEach-Object {
    Write-Host "Killing Java PID $($_.Id)"
    Stop-Process -Id $_.Id -Force
}

# Wait for port release
$waited = 0
while ($waited -lt 15) {
    $listening = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue
    if (-not $listening) { break }
    Start-Sleep -Seconds 1
    $waited++
}
if ($waited -lt 15) {
    Write-Host "OK - Server stopped, port 25565 released" -ForegroundColor Green
} else {
    Write-Host "WARN - Port 25565 still bound after 15s" -ForegroundColor Yellow
}