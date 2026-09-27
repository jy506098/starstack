# jy.yoliyo.cn 公网转发配置

把 Minecraft 服务器（内网 `10.0.0.124:25565`）通过域名 `jy.yoliyo.cn` 暴露到公网的完整配置。

```
[玩家] ──> jy.yoliyo.cn (DNS) ──> <公网 IP>:25565 ──> [路由器端口转发] ──> 10.0.0.124:25565 ──> [MC 服务器]
```

## 0. 准备工作

确认服务器本机有 `10.0.0.124` 这个 IP：

```powershell
ipconfig
# 看到类似:
#   IPv4 地址 . . . . . . . . . . . . : 10.0.0.124
```

如果没有，说明这台机器不在 `10.0.0.0/24` 网段，或者没拿到这个 IP。需要：

- 给本机网卡**手动指定 IP**：`10.0.0.124` / 子网掩码 `255.255.255.0` / 网关 `10.0.0.1`（按你路由器的实际情况调整）
- 或者改 `server.properties` 里的 `server-ip` 为本机真实 IP

## 1. 路由器端口转发

在路由器后台（一般是 `192.168.1.1` 或 `10.0.0.1`）添加一条端口转发规则：

| 字段 | 值 |
| ---- | ---- |
| 服务名称 / 备注 | `MC Server`（随便起） |
| 外部端口 (External Port) | `25565` |
| 内部 IP (Internal IP) | `10.0.0.124`（服务器本机） |
| 内部端口 (Internal Port) | `25565` |
| 协议 (Protocol) | `TCP`（有些路由器要选 `TCP/UDP`） |

保存后，从公网用 `telnet <你的公网IP> 25565` 验证端口能通。

## 2. 公网 IP

打开 https://ifconfig.me 或 https://ip.cn 查看，记下你的公网 IP（下面用 `<你的公网IP>` 占位）。

> ⚠️ 家用宽带一般是**动态 IP**，每次重启光猫/路由器都可能变。如果经常变，需要：
>
> - 在路由器启用 DDNS（动态 DNS），或者
> - 用 cron 定时调用 DDNS 服务商的 API 更新 `jy.yoliyo.cn` 的 A 记录。

## 3. DNS 记录

在 `yoliyo.cn` 的 DNS 服务商控制台添加 A 记录：

| 字段 | 值 |
| ---- | ---- |
| 主机记录 (Host) | `jy` |
| 记录类型 (Type) | `A` |
| 记录值 (Value) | `<你的公网IP>`（比如 `203.0.113.50`） |
| TTL | `600`（10 分钟）或自动 |

生效后：

```
$ ping jy.yoliyo.cn
PING jy.yoliyo.cn (<你的公网IP>): 56 data bytes
```

## 4. 防火墙

服务器主机的防火墙需要放行 25565 端口（本机入站 + 出站）。最简单的办法：

- 右键 [setup-firewall.bat](setup-firewall.bat) → 以管理员身份运行
- 或 PowerShell 管理员执行 [setup-server.ps1](setup-server.ps1)

## 5. SRV 记录（可选）

如果未来端口不是默认的 `25565`，需要添加 SRV 记录让客户端能找到非标准端口。这里用默认 25565，**不需要** SRV。

如果以后改端口（比如 `25575`），需要再加一条：

| 字段 | 值 |
| ---- | ---- |
| 主机记录 | `_minecraft._tcp.jy` |
| 记录类型 | `SRV` |
| 优先级 | `0` |
| 权重 | `5` |
| 端口 | `25575` |
| 目标 | `jy.yoliyo.cn` |

## 6. 验证

DNS 生效 + 端口转发配好后，按下面顺序验证：

```bash
# 1. DNS 解析正确
nslookup jy.yoliyo.cn
# 期望: Address: <你的公网IP>

# 2. 端口转发通了（从其他机器测）
nc -vz jy.yoliyo.cn 25565
# 或 PowerShell:
Test-NetConnection jy.yoliyo.cn -Port 25565

# 3. Minecraft 客户端连接
# 在 MC 多人游戏里添加服务器: jy.yoliyo.cn
# 看到绿色对勾 + 服务器在列表里 = 成功
```

最后一步最稳的测试方法：**用手机开 4G（不要用 WiFi）**，然后在 MC 客户端添加 `jy.yoliyo.cn` 看能不能连上。手机和服务器在不同的网络里才说明公网转发是通的。