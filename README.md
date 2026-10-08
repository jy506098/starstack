# StarStack

会员系统 + 游戏中心 + 留言板 Web 应用，从 Python Flask + vanilla JS 迁移到
**Spring Boot 3 + Thymeleaf + SQLite + TypeScript**。

## 运行

```bash
# Windows
start.bat

# Linux / macOS / Git Bash
./start.sh
```

脚本会自动 build（如果 `target/starstack.jar` 比源码旧）然后启动服务，
并在 4 秒后用默认浏览器打开 <http://localhost:5000/>。

也可以手动：

```bash
./mvnw -DskipTests package
java -jar target/starstack.jar
```

## 仓库结构

```
src/main/java/com/starstack/   Spring Boot 后端
  ├── StarStackApplication.java
  ├── config/        SecurityConfig, WebConfig, WebSocketConfig, DataSeeder
  ├── security/      WerkzeugScryptPasswordEncoder, AuthInterceptor
  ├── controller/    12 controllers (Auth/Home/Board/Store/.../Api)
  ├── ws/            SnakeWebSocketHandler, SnakeRoom, SnakeGameLoop
  ├── model/         User, Message, Order, VipPurchase, RechargeRecord, TaskRecord
  ├── repository/    UserRepository, MessageRepository, OrderRepository
  └── service/       UserService, BoardService, StoreService, TaskService, VipService, RechargeService, GameService, CatalogData

src/main/resources/
  ├── schema.sql                     首启动建表（Hibernate ddl-auto=validate）
  ├── application.yml
  ├── templates/                     Thymeleaf: 23 页面 + fragments/navbar.html
  └── static/                        CSS / images / avatars / js / ts build output

src/main/typescript/                 npm + tsc (frontend-maven-plugin)
  ├── tsconfig.json                  pages ES modules
  ├── tsconfig.games.json            games IIFE (module:none)
  └── src/
      ├── api/  common/  mouse-trail/  pages/  games/

data/starstack.db                    运行时生成 (SQLite, WAL mode)
mc/                                  Paper 服务端插件（独立模块，未迁移）
```

## 主要特性

- **认证**：BCrypt（新用户）+ werkzeug-scrypt（老用户）；首次登录自动 rehash
- **首启动迁移**：`DataSeeder` 自动从 `app/client/users.json` + `messages.json`
  导入老数据（迁移完成后文件改名为 `*.json.migrated`）
- **31 路由 → 12 controllers**：Auth/Home/Board/Store/Inventory/Tasks/Settings/
  Game/Vip/Recharge/Mc/Api
- **WebSocket 贪吃蛇**：80ms tick (`@Scheduled`)，单一 Tomcat 端口同时服务 HTTP + WS
- **VIP 充值**：4 个套餐，模拟微信支付 `confirm` 跳完成
- **TypeScript**：pages 用 ES modules，games 用 IIFE (`window.Games[name]`)
- **CSS**：单一 `style.css` + `themes.css`，无预处理器
- **数据隔离**：CSV/messages 历史保留原 `app/` 下的备份

## 端到端测试

```bash
./smoke.sh
```

会跑 31 个 HTTP/WebSocket 用例：注册/登录/充值/购买/游戏加载/WebSocket 握手。
最近一次：31 PASS / 0 FAIL。

## 历史

最近一次迁移：从 Flask + vanilla JS 迁移到 Spring Boot + TS（2026 年 10 月）。
原 `app/client/`、`app/website/`、`dist/StarStack.exe` 已删除。
