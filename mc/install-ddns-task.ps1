# ===========================================
# 把 ddns-update.ps1 注册为 Windows 计划任务
# 每 5 分钟自动跑一次，公网 IP 变了就同步 DNS
# 右键 → 用 PowerShell 管理员身份运行
# ===========================================

$ScriptPath = Join-Path $PSScriptRoot "ddns-update.ps1"
$TaskName = "StarStack MC Server DDNS Sync"

# 检查管理员
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[错误] 请用管理员身份运行 PowerShell" -ForegroundColor Red
    pause
    exit 1
}

# 删掉旧的
Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue

# 注册新的：每 5 分钟跑一次
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`""
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Minutes 5) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

Register-ScheduledTask -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -RunLevel Highest `
    -Description "每 5 分钟检查本机公网 IP，变化时自动更新 jy.yoliyo.cn 的阿里云 DNS A 记录" `
    -Force | Out-Null

Write-Host "✓ 已注册计划任务: $TaskName" -ForegroundColor Green
Write-Host ""
Write-Host "查看任务:   Get-ScheduledTask -TaskName '$TaskName'" -ForegroundColor Gray
Write-Host "立即跑一次: Start-ScheduledTask -TaskName '$TaskName'" -ForegroundColor Gray
Write-Host "删除任务:   Unregister-ScheduledTask -TaskName '$TaskName' -Confirm:`$false" -ForegroundColor Gray
Write-Host ""
Write-Host "下一步：在 ddns-config.json 里填好 AccessKey，再点 '立即跑一次' 测试。" -ForegroundColor Yellow