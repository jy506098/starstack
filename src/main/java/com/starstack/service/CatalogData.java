package com.starstack.service;

import com.starstack.dto.ContextView;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static catalog data — VIP packages, recharge packages, items, games,
 * tasks. Mirrors the constants previously inlined in app.py: VIP_TIERS,
 * VIP_PACKAGES, RECHARGE_PACKAGES, ITEM_DATA, GAME_LIST, FIXED_TASKS,
 * DAILY_TASK_POOL.
 *
 * Migration parity: the catalog is byte-for-byte equivalent to the Python
 * implementation so all behaviour is preserved.
 */
@Component
public class CatalogData {

    // ---------------- VIP ----------------
    public static final Map<String, Map<String, Object>> VIP_TIERS = buildVipTiers();
    private static Map<String, Map<String, Object>> buildVipTiers() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        m.put("VIP",   pair(10,  "VIP"));
        m.put("SVIP",  pair(50,  "SVIP"));
        m.put("SSVIP", pair(100, "SSVIP"));
        return m;
    }
    private static Map<String, Object> pair(int dailyBonus, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("daily_bonus", dailyBonus);
        m.put("label", label);
        return m;
    }

    public static final Map<Integer, Double> VIP_DURATION_MULTIPLIER;
    static {
        Map<Integer, Double> m = new LinkedHashMap<>();
        m.put(30,  1.0);
        m.put(90,  2.5);
        m.put(365, 8.0);
        VIP_DURATION_MULTIPLIER = m;
    }
    public static final Map<String, Integer> VIP_BASE_MONTHLY_PRICE;
    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("VIP",   50);
        m.put("SVIP",  100);
        m.put("SSVIP", 800);
        VIP_BASE_MONTHLY_PRICE = m;
    }

    public static final Map<String, Map<String, Object>> VIP_PACKAGES = buildVipPackages();
    private static Map<String, Map<String, Object>> buildVipPackages() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (var tier : VIP_BASE_MONTHLY_PRICE.keySet()) {
            int base = VIP_BASE_MONTHLY_PRICE.get(tier);
            int daily = (int) VIP_TIERS.get(tier).get("daily_bonus");
            for (var days : VIP_DURATION_MULTIPLIER.keySet()) {
                double mult = VIP_DURATION_MULTIPLIER.get(days);
                int price = (int) (base * mult);
                String key = tier + "-" + days;
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("tier", tier);
                v.put("duration_days", days);
                v.put("price_cny", price);
                v.put("daily_bonus", daily);
                v.put("label", tier + " · " + days + " 天");
                out.put(key, v);
            }
        }
        return out;
    }

    public static final List<String> VIP_TUTORIAL_WHITELIST = List.of(
        "编程秘籍", "C++ 入门", "node.js 入门",
        "前端三剑客 入门", "Python 入门", "Python后端 入门"
    );
    public static final List<String> VIP_MEDIA_WHITELIST = List.of(
        "音乐播放器", "影视播放器"
    );

    // ---------------- 充值 ----------------
    public static final Map<String, Map<String, Object>> RECHARGE_PACKAGES = buildRecharge();
    private static Map<String, Map<String, Object>> buildRecharge() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        m.put("RECHARGE-100",  recharge(10,  100,  0,   "入门充值"));
        m.put("RECHARGE-500",  recharge(50,  600,  100, "标准充值"));
        m.put("RECHARGE-1000", recharge(100, 1500, 300, "高级充值"));
        m.put("RECHARGE-2000", recharge(200, 3500, 800, "豪华充值"));
        return m;
    }
    private static Map<String, Object> recharge(int price, int points, int bonus, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("price_cny", price);
        m.put("points", points);
        m.put("bonus_points", bonus);
        return m;
    }

    // ---------------- 商品 ----------------
    public static final Map<String, Map<String, Object>> ITEM_DATA = buildItems();
    private static Map<String, Map<String, Object>> buildItems() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        m.put("编程秘籍",       item(199,  "解锁编程秘籍课程",        "新内容",          "tutorial"));
        m.put("C++ 入门",       item(199,  "解锁 C++ 入门课程",       "新内容",          "tutorial"));
        m.put("node.js 入门",   item(199,  "解锁 Node.js 入门",       "新内容",          "tutorial"));
        m.put("前端三剑客 入门", item(199,  "解锁前端 HTML/CSS/JS",   "新内容",          "tutorial"));
        m.put("Python 入门",    item(199,  "解锁 Python 入门课程",    "新内容",          "tutorial"));
        m.put("Python后端 入门", item(199,  "解锁 Python 后端入门",   "新内容",          "tutorial"));
        m.put("音乐播放器",      item(799,  "完整音乐播放器（含程序化曲库）", "音乐 + 音效", "media"));
        m.put("影视播放器",      item(2999, "完整影视中心",            "全剧集 + 多线路",  "media"));
        m.put("降噪耳机",       item(299,  "降噪耳机（白噪音）",      "专注模式 + 音效",  "accessory"));
        m.put("赛博T恤",        item(499,  "赛博风格 T 恤",           "流星雨背景 + 霓虹边框", "skin"));
        m.put("电竞鼠标",       item(599,  "电竞鼠标（鼠标拖尾）",    "鼠标拖尾可配置",   "accessory"));
        m.put("时钟",            item(99,   "导航栏时钟",              "导航栏时钟",       "accessory"));
        m.put("游戏手柄",       item(1299, "解锁游戏中心",            "12 款游戏",        "game_unlock"));
        return m;
    }
    private static Map<String, Object> item(int price, String desc, String effect, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price", price);
        m.put("desc", desc);
        m.put("effect", effect);
        m.put("type", type);
        return m;
    }

    // ---------------- 游戏 ----------------
    public static final List<String> GAME_LIST = List.of(
        "打砖块", "2048", "飞扬的小鸟", "水果忍者",
        "我的世界", "扫雷", "雷电战机", "俄罗斯方块",
        "抛硬币小游戏", "成语接龙"
    );
    public static final Map<String, Map<String, Object>> GAME_PRICES = buildGamePrices();
    private static Map<String, Map<String, Object>> buildGamePrices() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        m.put("打砖块", gp(99));
        m.put("2048", gp(99));
        m.put("飞扬的小鸟", gp(129));
        m.put("水果忍者", gp(199));
        m.put("我的世界", gp(299));
        m.put("扫雷", gp(99));
        m.put("雷电战机", gp(199));
        m.put("俄罗斯方块", gp(99));
        m.put("抛硬币小游戏", gp(49));
        m.put("成语接龙", gp(99));
        // 贪吃蛇大作战 — 免费联机游戏 (WebSocket 在 /snake_ws)
        m.put("贪吃蛇大作战", gp(0));
        return m;
    }
    private static Map<String, Object> gp(int price) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price", price);
        return m;
    }
    public static final String GAME_JS_DISPATCH = "dispatcher";
    /** Maps the legacy game_name to the JS module filename (under static/js/games/). */
    public static final Map<String, String> GAME_NAME_TO_JSFILE = Map.ofEntries(
        Map.entry("贪吃蛇大作战",   "snake.js"),
        Map.entry("打砖块",        "breakout.js"),
        Map.entry("2048",          "game2048.js"),
        Map.entry("飞扬的小鸟",     "flappy.js"),
        Map.entry("水果忍者",       "fruitninja.js"),
        Map.entry("我的世界",       "minecraft.js"),
        Map.entry("扫雷",          "minesweeper.js"),
        Map.entry("雷电战机",       "shooter.js"),
        Map.entry("俄罗斯方块",     "tetris.js"),
        Map.entry("抛硬币小游戏",   "coin.js"),
        Map.entry("成语接龙",       "idiomchain.js")
    );

    // ---------------- 任务 ----------------
    public static final List<Map<String, Object>> FIXED_TASKS = List.of(
        task("post_message", "发布一条留言",         50,  "fixed"),
        task("buy_item",     "在商店购买任意物品",   30,  "fixed"),
        task("use_item",     "使用一个物品",         30,  "fixed"),
        task("spend_master", "累计消费 500 积分",   200, "fixed")
    );
    public static final List<Map<String, Object>> DAILY_TASK_POOL = List.of(
        task("daily_visit", "访问 3 个不同页面",  5),
        task("daily_login", "每日登录",          10),
        task("daily_buy",   "今日购买 1 件商品",  15),
        task("daily_use",   "今日使用 1 件物品",  15),
        task("daily_post",  "今日发布 1 条留言",  10),
        task("daily_vip",   "VIP 当日在线",      5)
    );
    private static Map<String, Object> task(String id, String label, int reward) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("reward", reward);
        return m;
    }
    private static Map<String, Object> task(String id, String label, int reward, String type) {
        Map<String, Object> m = task(id, label, reward);
        m.put("type", type);
        return m;
    }

    public Map<String, Map<String, Object>> getVipTierMap() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        VIP_TIERS.forEach((k, v) -> m.put(k, new LinkedHashMap<>(v)));
        return m;
    }

    public ContextView.MCServerConfig getMcServer() {
        ContextView.MCServerConfig c = new ContextView.MCServerConfig();
        c.setHost("starstack.example.com");
        c.setPort(25565);
        c.setVersion("1.20.4");
        c.setMotd("⭐ StarStack VIP 专属服务器");
        return c;
    }
}