# DDNS 自动同步（jy.yoliyo.cn）

公网 IP 变了的时候，自动把阿里云 DNS 的 `jy.yoliyo.cn` A 记录同步过去。

## 1. 拿 AccessKey

阿里云 RAM 控制台: https://ram.console.aliyun.com/manage/access-keys

- 点「创建 AccessKey」
- 勾选编程访问
- 给这个 Key 加一个自定义权限策略，只授权 `alidns:DescribeSubDomainRecords` 和 `alidns:UpdateDomainRecord`（**最小权限**，避免 Key 泄露后灾难性后果）

权限策略 JSON 模板：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["alidns:DescribeSubDomainRecords", "alidns:UpdateDomainRecord"],
      "Resource": "acs:alidns:*:*:domain/yoliyo.cn"
    }
  ]
}
```

保存好 `AccessKeyId` 和 `AccessKeySecret`，关掉页面就再也看不到了。

## 2. 填配置

打开 [ddns-config.json](ddns-config.json)，把两个占位符替换成你的真实值：

```json
{
  "access_key_id": "LTAI5txxxxxxxxxxxx",
  "access_key_secret": "xxxxxxxxxxxxxxxxxxxxxxxx",
  ...
}
```

## 3. 先手动跑一次测试

```powershell
cd c:\Users\aa\Desktop\jyProject\StarStack\mc
powershell -ExecutionPolicy Bypass -File .\ddns-update.ps1
```

期望输出：

```
[HH:mm:ss] DDNS 检查开始
  当前公网 IP: 45.76.68.76
  DNS 当前解析: 120.77.69.19 (RecordId=xxxxx)
  ⚠ IP 变了 (120.77.69.19 → 45.76.68.76)，正在更新...
  ✓ DNS 已更新到 45.76.68.76
  ✓ jy.yoliyo.cn:25565 公网可达
[HH:mm:ss] 完成
```

## 4. 注册为 Windows 计划任务

右键 [install-ddns-task.ps1](install-ddns-task.ps1) → 用 PowerShell 管理员身份运行。

之后每隔 5 分钟会自动检测一次，公网 IP 变了就同步。

## 5. 故障排查

| 现象 | 原因 |
| ---- | ---- |
| `InvalidAccessKeyId` | AccessKeyId 填错了 |
| `SignatureDoesNotMatch` | AccessKeySecret 错了，或者系统时间不准（`w32tm /resync`） |
| `Forbidden.RAM` | RAM 权限没加 `alidns:UpdateDomainRecord` |
| `DomainRecordNotBelongToUser` | 域名不属于这个 AccessKey 对应的账号 |
| 公网 IP 探测超时 | 服务器本身没公网出口（家用内网机器，需要路由器允许脚本访问外网） |

查看最近日志：

```powershell
Get-WinEvent -LogName "Microsoft-Windows-TaskScheduler/Operational" |
  Where-Object { $_.Message -like "*StarStack MC*" } |
  Select-Object -First 10
```

## 6. 卸载

```powershell
Unregister-ScheduledTask -TaskName "StarStack MC Server DDNS Sync" -Confirm:$false
```