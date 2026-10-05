$keep = 24668
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Id -ne $keep) {
        Write-Host "停止 Java PID $($_.Id) (启动于 $($_.StartTime))"
        Stop-Process -Id $_.Id -Force
    } else {
        Write-Host "保留 PID $keep (绑定 10.0.0.124:25565 的服务器进程)"
    }
}
Write-Host ""
Write-Host "剩余 Java 进程:"
Get-Process java -ErrorAction SilentlyContinue | Select-Object Id, StartTime, @{Name='CPU_sec';Expression={[math]::Round($_.CPU,1)}} | Format-Table -AutoSize