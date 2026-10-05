# ===========================================
# StarStack MC 服务器 - 阿里云 DDNS 自动更新
# 公网域名: jy.yoliyo.cn
# 检测本机公网 IP，变了就自动更新阿里云 DNS A 记录
# ===========================================

$ErrorActionPreference = "Stop"

# 1. 加载配置
$ConfigPath = Join-Path $PSScriptRoot "ddns-config.json"
if (-not (Test-Path $ConfigPath)) {
    Write-Host "[错误] 找不到 ddns-config.json，请先填好 AccessKey。" -ForegroundColor Red
    exit 1
}

try {
    $Config = Get-Content $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    Write-Host "[错误] ddns-config.json 格式不对: $_" -ForegroundColor Red
    exit 1
}

if ($Config.access_key_id -like "你的*") {
    Write-Host "[错误] 请先在 ddns-config.json 里填好 AccessKeyId / AccessKeySecret" -ForegroundColor Red
    exit 1
}

# 2. 检测当前公网 IP
function Get-PublicIP {
    try {
        $ip = (Invoke-WebRequest -Uri $Config.ip_check_url -UseBasicParsing -TimeoutSec 10).Content.Trim()
        if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') {
            throw "返回的 IP 格式不对: $ip"
        }
        return $ip
    } catch {
        Write-Host "[错误] 获取公网 IP 失败: $_" -ForegroundColor Red
        return $null
    }
}

# 3. URL 编码（阿里云要求的特殊规则）
function Aliyun-UrlEncode {
    param([string]$s)
    [System.Uri]::EscapeDataString($s) -replace '\+', '%20' -replace '\*', '%2A' -replace '%7E', '~'
}

# 4. 计算阿里云 API 签名
function Get-AliyunSignature {
    param(
        [hashtable]$Params,
        [string]$AccessKeySecret
    )
    $sorted = $Params.GetEnumerator() | Sort-Object Name
    $canonicalized = ($sorted | ForEach-Object {
        "$(Aliyun-UrlEncode $_.Name)=$(Aliyun-UrlEncode $_.Value)"
    }) -join "&"
    $stringToSign = "GET&$(Aliyun-UrlEncode '/')&$(Aliyun-UrlEncode $canonicalized)"
    $hmac = [System.Security.Cryptography.HMACSHA1]::new(
        [System.Text.Encoding]::UTF8.GetBytes("$AccessKeySecret&")
    )
    $hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($stringToSign))
    [Convert]::ToBase64String($hash)
}

# 5. 调用阿里云 DNS API
function Invoke-AliyunDns {
    param(
        [string]$Action,
        [hashtable]$Params
    )
    $common = @{
        Format           = "JSON"
        Version          = "2015-01-09"
        AccessKeyId      = $Config.access_key_id
        SignatureMethod  = "HMAC-SHA1"
        Timestamp        = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        SignatureVersion = "1.0"
        SignatureNonce   = [guid]::NewGuid().ToString()
    }
    foreach ($k in $common.Keys) { $Params[$k] = $common[$k] }
    $Params["Action"] = $Action

    $Params["Signature"] = Get-AliyunSignature -Params $Params -AccessKeySecret $Config.access_key_secret

    $query = ($Params.GetEnumerator() | ForEach-Object {
        "$(Aliyun-UrlEncode $_.Name)=$(Aliyun-UrlEncode $_.Value)"
    }) -join "&"

    $url = "https://alidns.aliyuncs.com/?$query"
    $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -Method GET -TimeoutSec 15
    ($resp.Content | ConvertFrom-Json)
}

# 6. 主流程
Write-Host "[$(Get-Date -Format 'HH:mm:ss')] DDNS 检查开始" -ForegroundColor Cyan

$newIP = Get-PublicIP
if (-not $newIP) { exit 1 }
Write-Host "  当前公网 IP: $newIP"

# 查记录
$query = @{
    SubDomain = "$($Config.sub_domain).$($Config.domain)"
    Type      = $Config.type
}
$recordResp = Invoke-AliyunDns -Action "DescribeSubDomainRecords" -Params $query

if ($recordResp.DomainRecords.Record) {
    $record = $recordResp.DomainRecords.Record[0]
    $oldIP = $record.Value
    $recordId = $record.RecordId
    Write-Host "  DNS 当前解析: $oldIP (RecordId=$recordId)"

    if ($oldIP -eq $newIP) {
        Write-Host "  ✓ IP 没变，无需更新" -ForegroundColor Green
        exit 0
    }

    Write-Host "  ⚠ IP 变了 ($oldIP → $newIP)，正在更新..." -ForegroundColor Yellow
    $update = Invoke-AliyunDns -Action "UpdateDomainRecord" -Params @{
        RecordId = $recordId
        RR       = $Config.sub_domain
        Type     = $Config.type
        Value    = $newIP
        TTL      = $Config.ttl
    }
    if ($update.RecordId) {
        Write-Host "  ✓ DNS 已更新到 $newIP" -ForegroundColor Green
    } else {
        Write-Host "  ✗ 更新失败: $($update | ConvertTo-Json -Depth 5)" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "  ⚠ 未找到 $((Join-Path $Config.sub_domain $Config.domain)) 的 $((Join-Path $Config.type 'A')) 记录，请先在阿里云控制台手动创建" -ForegroundColor Yellow
}

# 7. 顺便探测一下端口是否公网通
try {
    $tcp = Test-NetConnection -ComputerName "$($Config.sub_domain).$($Config.domain)" -Port 25565 -WarningAction SilentlyContinue -InformationLevel Quiet
    if ($tcp) {
        Write-Host "  ✓ jy.yoliyo.cn:25565 公网可达" -ForegroundColor Green
    } else {
        Write-Host "  ⚠ jy.yoliyo.cn:25565 公网仍不通（请检查路由器端口转发）" -ForegroundColor Yellow
    }
} catch {}

Write-Host "[$(Get-Date -Format 'HH:mm:ss')] 完成" -ForegroundColor Cyan