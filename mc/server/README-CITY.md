# 赛博朋克青色主题主城

## 城市布局

中心点: `(1000, 80, 1000)`，范围 `100×100`，高度最高 50 格

```
        N (-Z)
     ___|___
    |   |   |
  W-|   |   |-E (+X)
    |___|___|
        |
        S (+Z)
```

- **中央广场** (20×20)：浅蓝色地基 + 4 角发光柱 + 中央 5×5×50 全息玻璃塔（顶上有信标）
- **北塔** (z=-35, 45 高)：最高楼，"CYBER NETWORK" 招牌，玻璃幕墙 + 海晶灯顶层 + 天线
- **东塔** (x=35, 35 高)："DATA HUB"
- **南塔** (z=35, 30 高)："未来科技 LAB"
- **西塔** (x=-35, 25 高)："MARKET ZONE"
- **+ 形道路**：黑 + 青双层，边缘海晶灯
- **4 个路口广告柱**：玻璃柱 + "STAR 2077" 招牌
- **道边霓虹柱**：每 8 格一根

## 主色调

| 元素 | 方块 |
| ---- | ---- |
| 地基 | 黑色混凝土 + 青色混凝土 |
| 楼体 | 黑色混凝土框架 |
| 幕墙 | 青色 / 浅蓝色玻璃 |
| 灯条 | 海晶灯（满级亮度） |
| 招牌文字 | `aqua` / `dark_aqua` 颜色 |

## 加载和使用

服务器目前正在跑，datapack 已放到 `world/datapacks/cyber-city/`。

### 步骤 1：让服务器加载 datapack

Jy_Jy 进入游戏后**以 OP 身份**在聊天框输入：

```
/reload
```

成功的话聊天框会显示 `[Server] Reloading ResourceManager: ...` 然后 `[Server] Loaded 1 datapack(s)` 之类的。

### 步骤 2：一键建造

```
/function city:build
```

聊天框会出现：

```
[StarStack] Building Cyberpunk City...
[StarStack] Done! Click to TP
```

点击 "Click to TP" 自动传送到 `(1000, 81, 1000)`，世界出生点也设在那。

### 步骤 3（可选）：拆掉重建

```
/function city:clear
```

清空整个 100×100×80 的城市区域，重新跑 `/function city:build` 再建一次。

## 自定义

- 改 `cyber_city_gen.py` 后跑 `python cyber_city_gen.py` 重新生成
- 改完不用重启服务器，直接 `/reload` 然后 `/function city:build`

## 文件位置

```
mc/world/datapacks/cyber-city/
├── pack.mcmeta
└── data/city/functions/
    ├── build.mcfunction       # 主入口
    ├── clear.mcfunction       # 清除
    ├── platform.mcfunction    # 平台
    ├── roads.mcfunction       # 道路
    ├── plaza.mcfunction       # 中央广场
    ├── tower_north.mcfunction # 北塔
    ├── tower_east.mcfunction  # 东塔
    ├── tower_south.mcfunction # 南塔
    ├── tower_west.mcfunction  # 西塔
    └── neon.mcfunction        # 装饰霓虹灯
```