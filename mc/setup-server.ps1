# ===========================================
# StarStack MC 服务器一键配置脚本 (PowerShell)
# 请右键 PowerShell，选择 "以管理员身份运行"，再执行本脚本
# 用法：cd 到 mc 目录后，执行 .\setup-server.ps1
# ===========================================

# 切换 UTF-8 编码，避免中文乱码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "StarStack MC 服务器配置脚本" -ForegroundColor Cyan
Write-Host "公网域名: jy.yoliyo.cn" -ForegroundColor Cyan
Write-Host "本机 IP:  10.0.0.124" -ForegroundColor Cyan
Write-Host "端口:     25565" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查管理员权限
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "[错误] 请用管理员身份运行 PowerShell 后再执行本脚本。" -ForegroundColor Red
    Write-Host "操作: 右键开始菜单的 PowerShell → 以管理员身份运行" -ForegroundColor Yellow
    pause
    exit 1
}

# 2. 放行 25565 端口
Write-Host "[1/3] 配置 Windows 防火墙放行 25565 端口..." -ForegroundColor Green
try {
    Remove-NetFirewallRule -DisplayName "Minecraft Server 25565" -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName "Minecraft Server 25565" `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort 25565 `
        -Action Allow `
        -Profile Any | Out-Null

    New-NetFirewallRule -DisplayName "Minecraft Server 25565 UDP" `
        -Direction Inbound `
        -Protocol UDP `
        -LocalPort 25565 `
        -Action Allow `
        -Profile Any | Out-Null

    Write-Host "      ✓ TCP/UDP 25565 已放行" -ForegroundColor Green
} catch {
    Write-Host "      ✗ 防火墙配置失败: $_" -ForegroundColor Red
}

# 3. 列出本机所有 IPv4 地址，供用户确认服务器本机的内网 IP（路由器端口转发要用）
Write-Host ""
Write-Host "[2/3] 本机 IPv4 地址（路由器端口转发需要用到）:" -ForegroundColor Green
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
    ForEach-Object {
        Write-Host "      $($_.IPAddress)  ($($_.InterfaceAlias))" -ForegroundColor Gray
    }

# 5. 获取公网 IP
Write-Host ""
Write-Host "      正在查询公网 IP..." -ForegroundColor Gray
try {
    $pubIp = (Invoke-WebRequest -Uri "https://ifconfig.me/ip" -UseBasicParsing -TimeoutSec 8).Content.Trim()
    Write-Host "      ✓ 你的公网 IP: $pubIp" -ForegroundColor Green
    Write-Host "      → 在域名服务商把 mc.starstack.com 的 A 记录设为 $pubIp" -ForegroundColor Green
} catch {
    Write-Host "      ⚠ 无法自动查询公网 IP，请手动访问 https://ifconfig.me 查看" -ForegroundColor Yellow
}

# 6. 验证 DNS
Write-Host ""
Write-Host "[3/3] 检查域名 jy.yoliyo.cn 的 DNS 解析..." -ForegroundColor Green
try {
    $dnsResult = Resolve-DnsName -Name "jy.yoliyo.cn" -ErrorAction Stop
    foreach ($record in $dnsResult) {
        if ($record.Type -eq "A") {
            Write-Host "      ✓ 解析结果: $($record.IPAddress)" -ForegroundColor Green
            if ($pubIp -and $record.IPAddress -eq $pubIp) {
                Write-Host "      ✓ 与你的公网 IP 一致，配置正确！" -ForegroundColor Green
            } else {
                Write-Host "      ⚠ 当前解析为 $($record.IPAddress)，与公网 IP 不一致" -ForegroundColor Yellow
            }
        }
    }
} catch {
    Write-Host "      ⚠ DNS 还未配置 (Non-existent domain)" -ForegroundColor Yellow
    Write-Host "      请到域名服务商控制台添加 A 记录:" -ForegroundColor Yellow
    Write-Host "        主机记录: jy" -ForegroundColor Yellow
    Write-Host "        记录类型: A" -ForegroundColor Yellow
    Write-Host "        记录值:   <你的公网IP>" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "配置完成！" -ForegroundColor Cyan
Write-Host "下一步: 把 server.jar 放到本目录后，双击 start.bat 启动服务器。" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
pause