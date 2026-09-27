# StarStack

一个 Minecraft Paper 26.3 服务端插件，把 12 个独立功能模块（领地、AuthMe、EssentialsX、Claims、反作弊、Raspberry Pi mcpi 桥、SkinsRestorer、CoreProtect 等）合并到单个 8 MB 的 jar 里。

## 仓库结构

```
app/         PyInstaller 打包的 Windows 桌面客户端（已注册的 VIP 会员系统 + 微信支付模拟）
website/     Flask 聊天 + 支付网页服务（共享 users.json + messages.json）
mcplugin/    Paper 26.3 服务端插件源码
```

## mcplugin — 服务端插件

**核心设计**：所有第三方功能通过 `bridge` 模式直接调用，避免双 JavaPlugin 初始化的 `Plugin already initialized!` 错误。

- [mcplugin/StarStack.java](mcplugin/StarStack.java) — 主插件类（906 行）
  - 包含 12 个模块的 onEnable 初始化、命令 dispatcher（184 条命令）
  - TreeMiner / Claims / AntiCheat / MagisterAC / LuckPerms / CoreProtect / Mintconomy / RPTMarket / AuthMe / Essentials / RaspberryPi / SkinsRestorerBridge
- [mcplugin/SkinsRestorerBridge.java](mcplugin/SkinsRestorerBridge.java) — 把 SkinsRestorer 15.12.6 的源码（4410 个 .class 文件）直接打包进 StarStack.jar；通过 `SRBootstrapper.startPlugin()` 绕过 JavaPlugin 二次初始化
- [mcplugin/plugin.yml](mcplugin/plugin.yml) — 184 条命令清单 + 权限节点

### 重要说明

**11 个模块的 `.java` 源码已不在仓库**。这些源码在前几轮迭代中被清理，只保留最终的 `StarStack.java` 主文件 + `SkinsRestorerBridge.java`。如果想修改任何子模块的行为，需要：

1. 从备份 jar `StarStack-final11.jar`（8.2 MB，包含所有 12 个模块的 .class 文件）反编译，或
2. 重新从各项目上游获取源码（EssentialsX、SkinsRestorer、CoreProtect 等）

部署用的是 `StarStack-final11.jar` 二进制；`StarStack.java` 编译时通过把它当作 classpath 即可。

### 部署

```bash
# 编译
javac --release 21 -cp /path/to/spigot-api-26.x-R0.1-SNAPSHOT-shaded.jar \
    -d mcplugin/build-out mcplugin/*.java

# 打包（注意：必须把 SkinsRestorer 的 .class 文件也一起打进去）
jar cf StarStack.jar -C build-out .

# 部署
cp StarStack.jar mc/plugins/StarStack.jar
```

SkinsRestorer 的 shaded 依赖以 `.class` 形式直接放进 jar（位于 `net.skinsrestorer.*` 包下），无需额外 classpath。

## app + website — Python 端

`app/` 和 `website/` 共享 `users.json`（会员信息）和 `messages.json`（聊天记录），通过符号链接（`link_shared_json.{bat,sh}`）保持一致。

- `app/`：PyInstaller `--onefile` 打包成单个 `.exe`，启动后监听 socket 提供会员管理 + 模拟微信支付
- `website/`：Flask 网页服务，端口 80，提供聊天 UI + VIP 注册页

## 历史

最近一次 commit：`5276405 Add VIP membership + 微信支付模拟 + MC 服务器 integration`

---

中文维基：如果这篇文章是给别人看的，请用中文以外的英文或日文。