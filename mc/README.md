# StarStack Minecraft 服务器

## 服务器信息

| 项目 | 值 |
| ---- | ---- |
| 监听地址 | `10.0.0.124:25565` |
| 域名地址 | `jy.yoliyo.cn` |
| DNS A 记录 | 指向你的**公网 IP**（路由器 WAN IP） |
| 端口 | `25565` |
| 模式 | 生存模式 (Survival) |
| 难度 | 普通 (Normal) |
| 最大玩家 | 50 |
| 在线验证 | 开启 (正版) |

## 客户端连接方式

在 Minecraft 多人游戏里添加服务器，任选其一即可：

- 公网域名：`jy.yoliyo.cn`（推荐，所有人都能用）
- 内网 IP：`10.0.0.124`（仅本机所在局域网有效）

## 目录结构

```
mc/
├── server.properties      # 服务器主配置（IP/端口/MOTD 等）
├── eula.txt               # Minecraft EULA（必须为 true）
├── start.bat              # Windows 启动脚本
├── start.sh               # Linux / macOS 启动脚本
├── ops.json               # OP（管理员）列表
├── whitelist.json         # 白名单
├── banned-players.json    # 封禁玩家
├── banned-ips.json        # 封禁 IP
├── users.json             # 玩家缓存
├── domain-config.md       # jy.yoliyo.cn → 公网 IP → 10.0.0.124 转发配置
└── server.jar             # （需自行下载）Minecraft 官方服务器核心
```

## 启动步骤

1. **下载服务器核心**：前往 https://www.minecraft.net/en-us/download/server 下载 `server.jar`，放到本目录。
2. **确认 Java 已安装**：建议 JDK 17 或更高版本，运行 `java -version` 验证。
3. **配置防火墙**（任选一种）：
   - 一键脚本（推荐）：右键 `setup-firewall.bat` → 以管理员身份运行
   - PowerShell：右键 PowerShell → 以管理员身份运行 → 执行 `.\setup-server.ps1`
4. **配置 DNS + 端口转发**：在 `yoliyo.cn` 的 DNS 服务商添加 A 记录 `jy` → 你的**公网 IP**，并在路由器把外网 `25565/TCP` 转发到 `10.0.0.124:25565`（详见 [domain-config.md](domain-config.md)）。
5. **启动服务器**：
   - Windows：双击 `start.bat`
   - Linux/macOS：在终端执行 `./start.sh`

## 域名解析（jy.yoliyo.cn → 公网 IP → 10.0.0.124）

要让玩家在客户端里输入 `jy.yoliyo.cn` 就能连到服务器，需要三步配合：

1. 在 `yoliyo.cn` 的 DNS 服务商添加 A 记录：
   - 主机记录：`jy`
   - 记录类型：`A`
   - 记录值：你的**公网 IP**（WAN IP）
2. 在路由器做端口转发：外网 `25565/TCP` → `10.0.0.124:25565`。
3. 服务器本机 `ipconfig` 确认网卡上有 `10.0.0.124` 这个 IP。

具体配置步骤参见 [domain-config.md](domain-config.md)。

## 常用命令

```bash
# 添加 OP
op <玩家名>

# 切换难度
difficulty normal

# 保存世界
save-all

# 关闭服务器
stop
```

## 注意事项

- `server-ip=10.0.0.124` 表示服务器只监听本机的 `10.0.0.124` 网卡。请确认这台机器真的有 `10.0.0.124` 这个 IP，否则 Minecraft 会启动失败。
- 公网访问依赖**路由器端口转发**：外网 `25565/TCP` → `10.0.0.124:25565`。
- DNS 把 `jy.yoliyo.cn` 解析到你路由器的**公网 IP**（WAN IP），公网玩家才能连上。
- `online-mode=true` 表示只允许正版账号进入。若改成 `false`，则离线账号也能加入，但**请勿对公网开放离线服**，会有安全风险。
- `enforce-whitelist=true` 已开启白名单，只有 `whitelist.json` 里的玩家能进。修改后需在控制台跑 `whitelist reload` 生效。