package app.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 留言板 Web 应用 (Java 翻译版) — 含贪吃蛇联机服务入口。
 *
 * Java 移植说明：
 * - Flask 31 个路由 → 31 个 Java 方法，使用 @GetMapping/@PostMapping 注解（保留 Spring 风格）
 * - 用户/留言/订单等数据原本存在 JSON 文件，Java 版用 in-memory Map（实际生产用 SQLite）
 * - 密码哈希：Python 用 werkzeug.scrypt；Java 用 BCryptPasswordEncoder（Spring Security）
 * - WebSocket 挂载由 SnakeServer 提供（实际生产用 src/main/java/com/starstack/ws/）
 * - 此文件保留作为 Python app.py 的 Java 翻译存档，不会被 Maven 编译
 *
 * 实际生产版本位于 src/main/java/com/starstack/controller/ 下 12 个 controller 类。
 */
public final class App {

    // ============== 静态配置 ==============
    private static final String BASE_DIR = new File(".").getAbsolutePath();
    private static final File AVATAR_FOLDER = new File(BASE_DIR, "static/avatars");
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "gif", "webp");
    static { AVATAR_FOLDER.mkdirs(); }

    private static final File MESSAGES_FILE = new File(BASE_DIR, "messages.json");
    private static final File USERS_FILE = new File(BASE_DIR, "users.json");

    // 内存数据存储（实际用 SQLite）
    private static List<Map<String, Object>> messages = new ArrayList<>();
    private static Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();
    private static Map<String, Map<String, Object>> pendingOrders = new ConcurrentHashMap<>();

    // ============== 固定任务 ==============
    public static final List<Map<String, Object>> FIXED_TASKS = List.of(
        Map.of("id", "post_message", "name", "首次留言", "desc", "在留言板发布消息", "reward", 20),
        Map.of("id", "buy_item", "name", "首次购买", "desc", "在商店购买商品", "reward", 30),
        Map.of("id", "use_item", "name", "首次使用物品", "desc", "在背包使用物品", "reward", 15),
        Map.of("id", "spend_master", "name", "消费达人", "desc", "累计消费达到500积分", "reward", 50)
    );

    public static final List<Map<String, Object>> DAILY_TASK_POOL = List.of(
        Map.of("id", "daily_login", "name", "每日登录", "desc", "登录网站", "reward", 10),
        Map.of("id", "daily_post", "name", "每日留言", "desc", "在留言板发布一条消息", "reward", 15),
        Map.of("id", "daily_visit", "name", "每日浏览", "desc", "访问网站3个不同页面", "reward", 5),
        Map.of("id", "daily_buy", "name", "每日购物", "desc", "在商店购买任意商品", "reward", 20),
        Map.of("id", "daily_use", "name", "每日使用", "desc", "在背包使用任意物品", "reward", 18),
        Map.of("id", "daily_share", "name", "每日分享", "desc", "分享网站链接（模拟）", "reward", 12)
    );

    // ============== VIP 系统 ==============
    public static final Map<String, Map<String, Object>> VIP_TIERS = new LinkedHashMap<>();
    static {
        VIP_TIERS.put("VIP",   Map.of("daily_bonus", 10,  "label", "VIP"));
        VIP_TIERS.put("SVIP",  Map.of("daily_bonus", 50,  "label", "SVIP"));
        VIP_TIERS.put("SSVIP", Map.of("daily_bonus", 100, "label", "SSVIP"));
    }
    public static final Map<Integer, Double> VIP_DURATION_MULTIPLIER = Map.of(30, 1.0, 90, 2.5, 365, 8.0);
    public static final Map<String, Integer> VIP_BASE_MONTHLY_PRICE = Map.of("VIP", 50, "SVIP", 100, "SSVIP", 800);
    public static final Map<String, Map<String, Object>> VIP_PACKAGES = new LinkedHashMap<>();
    static {
        for (Map.Entry<String, Integer> tier : VIP_BASE_MONTHLY_PRICE.entrySet()) {
            for (Map.Entry<Integer, Double> days : VIP_DURATION_MULTIPLIER.entrySet()) {
                String key = tier.getKey() + "-" + days.getKey();
                Map<String, Object> pkg = new LinkedHashMap<>();
                pkg.put("tier", tier.getKey());
                pkg.put("duration_days", days.getKey());
                pkg.put("price_cny", (int)(tier.getValue() * days.getValue()));
                pkg.put("daily_bonus", ((Number) VIP_TIERS.get(tier.getKey()).get("daily_bonus")).intValue());
                pkg.put("label", tier.getKey() + " · " + days.getKey() + " 天");
                VIP_PACKAGES.put(key, pkg);
            }
        }
    }

    public static final List<String> VIP_TUTORIAL_WHITELIST = List.of(
        "编程秘籍", "C++ 入门", "node.js 入门",
        "前端三剑客 入门", "Python 入门", "Python后端 入门"
    );
    public static final List<String> VIP_MEDIA_WHITELIST = List.of("音乐播放器", "影视播放器");

    // ============== MC 服务器 ==============
    public static final Map<String, Object> MC_SERVER_CONFIG = Map.of(
        "host", "starstack.example.com",
        "port", 25565,
        "version", "1.20.4",
        "motd", "⭐ StarStack VIP 专属服务器"
    );

    // ============== 积分充值套餐 ==============
    public static final Map<String, Map<String, Object>> RECHARGE_PACKAGES = new LinkedHashMap<>();
    static {
        RECHARGE_PACKAGES.put("RECHARGE-100",  Map.of("label", "入门充值",  "price_cny", 10,  "points", 100,  "bonus_points", 0));
        RECHARGE_PACKAGES.put("RECHARGE-500",  Map.of("label", "标准充值",  "price_cny", 50,  "points", 600,  "bonus_points", 100));
        RECHARGE_PACKAGES.put("RECHARGE-1000", Map.of("label", "高级充值",  "price_cny", 100, "points", 1500, "bonus_points", 300));
        RECHARGE_PACKAGES.put("RECHARGE-2000", Map.of("label", "豪华充值",  "price_cny", 200, "points", 3500, "bonus_points", 800));
    }

    // ============== 商品数据 ==============
    public static final Map<String, Map<String, Object>> ITEM_DATA = new LinkedHashMap<>();
    static {
        ITEM_DATA.put("编程秘籍",       Map.of("price", 199,  "base_use_reward", 50,  "base_sell_reward", 99,  "desc", "📖 学习编程秘籍，提升你的代码能力", "effect", "📖 解锁《编程思想》电子书，提升编程思维", "content_page", "programming_secrets.html"));
        ITEM_DATA.put("降噪耳机",       Map.of("price", 399,  "base_use_reward", 100, "base_sell_reward", 199, "desc", "🎧 戴上降噪耳机，隔绝噪音，专注提升", "effect", "🎵 解锁降噪耳机，可播放白/粉红/棕噪声", "content_page", "headphones.html"));
        ITEM_DATA.put("C++ 入门",       Map.of("price", 599,  "base_use_reward", 150, "base_sell_reward", 299, "desc", "⌨️ 从零开始学习 C++，掌握编程核心技能", "effect", "⚡ 解锁《C++ 入门》视频教程，共 314 集，循序渐进", "content_page", "cpp_tutorial.html"));
        ITEM_DATA.put("电竞鼠标",       Map.of("price", 149,  "base_use_reward", 40,  "base_sell_reward", 74,  "desc", "🖱️ 掌控精准，反应迅速，电竞利器", "effect", "🎯 获得「精准瞄准」加持，游戏胜率提升", "content_page", null));
        ITEM_DATA.put("node.js 入门",   Map.of("price", 399,  "base_use_reward", 100, "base_sell_reward", 199, "desc", "🚀 掌握 Node.js，轻松构建高性能后端服务", "effect", "📘 解锁《Node.js 入门》电子书，快速上手服务端开发", "content_page", "nodejs_tutorial.html"));
        ITEM_DATA.put("前端三剑客 入门", Map.of("price", 499,  "base_use_reward", 120, "base_sell_reward", 249, "desc", "🌐 HTML + CSS + JavaScript，零基础搭建现代化网页", "effect", "🎨 获得「前端开发」实战项目源码，从零到部署", "content_page", "frontend_tutorial.html"));
        ITEM_DATA.put("Python 入门",    Map.of("price", 99,   "base_use_reward", 25,  "base_sell_reward", 49,  "desc", "🐍 打开Python大门，开启编程之旅", "effect", "🎬 观看《Python入门》视频教程，快速上手", "content_page", "python_tutorial.html"));
        ITEM_DATA.put("Python后端 入门", Map.of("price", 399,  "base_use_reward", 100, "base_sell_reward", 199, "desc", "🐍 掌握 Python 后端开发，构建高效 Web 服务", "effect", "📘 解锁《Python后端 入门》视频教程，共 29 集，从零到部署", "content_page", "backend_getting_started.html"));
        ITEM_DATA.put("赛博 T 恤",      Map.of("price", 199,  "base_use_reward", 50,  "base_sell_reward", 99,  "desc", "👕 穿上赛博T恤，魅力值飙升，成为焦点", "effect", "👾 获得「赛博光环」，让代码自带RGB效果", "content_page", null));
        ITEM_DATA.put("游戏手柄",       Map.of("price", 299,  "base_use_reward", 80,  "base_sell_reward", 149, "desc", "🎮 握紧手柄，畅游游戏世界", "effect", "🎮 解锁游戏中心，畅玩《我的世界》等游戏！", "content_page", "game_center.html"));
        ITEM_DATA.put("音乐播放器",     Map.of("price", 2999, "base_use_reward", 800, "base_sell_reward", 1499, "desc", "🎵 顶级音乐播放器，无损音质，沉浸体验", "effect", "🎶 解锁音乐播放功能，随时享受旋律", "content_page", "music_player.html"));
        ITEM_DATA.put("影视播放器",     Map.of("price", 799,  "base_use_reward", 200, "base_sell_reward", 399, "desc", "🎬 高清影视播放器，海量资源，畅享视听盛宴", "effect", "📺 解锁影视中心，观看最新电影和电视剧", "content_page", "video_player.html"));
    }

    // ============== 游戏数据 ==============
    public static final Map<String, Map<String, Object>> GAME_LIST = new LinkedHashMap<>();
    static {
        GAME_LIST.put("我的世界",       Map.of("price", 0,    "desc", "沙盒创造，无限创意", "icon", "⛏️"));
        GAME_LIST.put("成语接龙",       Map.of("price", 5000, "desc", "考验你的成语储备", "icon", "📚"));
        GAME_LIST.put("水果忍者",       Map.of("price", 1500, "desc", "切水果，解压神器", "icon", "🍉"));
        GAME_LIST.put("抛硬币小游戏",   Map.of("price", 500,  "desc", "抛硬币，运气大挑战", "icon", "🪙"));
        GAME_LIST.put("打砖块",         Map.of("price", 800,  "desc", "经典打砖块，挑战高分", "icon", "🧱"));
        GAME_LIST.put("飞扬的小鸟",     Map.of("price", 600,  "desc", "控制小鸟穿越管道", "icon", "🐦"));
        GAME_LIST.put("2048",           Map.of("price", 1000, "desc", "合并数字，挑战2048", "icon", "🔢"));
        GAME_LIST.put("扫雷",           Map.of("price", 800,  "desc", "经典扫雷游戏", "icon", "💣"));
        GAME_LIST.put("雷电战机",       Map.of("price", 1000, "desc", "飞行射击，躲避敌机", "icon", "✈️"));
        GAME_LIST.put("杀戮尖塔",       Map.of("price", 1500, "desc", "卡牌策略，挑战高塔", "icon", "🏛️"));
        GAME_LIST.put("贪吃蛇大作战",   Map.of("price", 900,  "desc", "多人竞技贪吃蛇", "icon", "🐍"));
        GAME_LIST.put("俄罗斯方块",     Map.of("price", 800,  "desc", "经典俄罗斯方块", "icon", "🧩"));
    }

    private static final Pattern PHONE_RE = Pattern.compile("^1[3-9]\\d{9}$");

    private App() {}

    // ============== 数据加载/保存 ==============
    @SuppressWarnings("unchecked")
    public static void loadMessages() {
        if (!MESSAGES_FILE.exists()) return;
        try {
            String json = Files.readString(MESSAGES_FILE.toPath(), StandardCharsets.UTF_8);
            messages = (List<Map<String, Object>>) MiniJson.parse(json);
        } catch (Exception e) {
            messages = new ArrayList<>();
        }
    }

    public static void saveMessages() {
        try {
            Files.writeString(MESSAGES_FILE.toPath(), MiniJson.stringify(messages), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("save_messages failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static void loadUsers() {
        if (!USERS_FILE.exists()) return;
        try {
            String json = Files.readString(USERS_FILE.toPath(), StandardCharsets.UTF_8);
            users = (Map<String, Map<String, Object>>) MiniJson.parse(json);
        } catch (Exception e) {
            users = new ConcurrentHashMap<>();
        }
        initMissingFields();
    }

    public static void saveUsers() {
        try {
            Files.writeString(USERS_FILE.toPath(), MiniJson.stringify(users), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("save_users failed: " + e.getMessage());
        }
    }

    /** 兼容旧用户：补齐缺失字段。 */
    @SuppressWarnings("unchecked")
    public static void initMissingFields() {
        for (Map.Entry<String, Map<String, Object>> e : new ArrayList<>(users.entrySet())) {
            String username = e.getKey();
            Map<String, Object> user = e.getValue();
            if (!user.containsKey("tasks")) user.put("tasks", new LinkedHashMap<>());
            if (!user.containsKey("daily_tasks_completed")) user.put("daily_tasks_completed", new LinkedHashMap<>());
            if (!user.containsKey("daily_visit_count")) user.put("daily_visit_count", 0);
            if (!user.containsKey("last_visit_date")) user.put("last_visit_date", "");
            if (!user.containsKey("last_post_date")) user.put("last_post_date", "");
            if (!user.containsKey("total_spent")) user.put("total_spent", 0);
            if (!user.containsKey("unlocked_content")) user.put("unlocked_content", new ArrayList<String>());
            if (!user.containsKey("effects")) user.put("effects", new LinkedHashMap<String, Boolean>());
            if (!user.containsKey("is_admin")) user.put("is_admin", "顾璟瑶".equals(username));
            if (!user.containsKey("admin_daily_points_date")) user.put("admin_daily_points_date", "");
            if (!user.containsKey("admin_daily_points_count")) user.put("admin_daily_points_count", 0);
            if (!user.containsKey("mouse_effect_config")) user.put("mouse_effect_config", Map.of(
                "enabled", true, "color_mode", "rainbow", "shape", "circle"));
            if (!user.containsKey("vip_tier")) user.put("vip_tier", "");
            if (!user.containsKey("vip_expires_at")) user.put("vip_expires_at", "");
            if (!user.containsKey("last_vip_bonus_date")) user.put("last_vip_bonus_date", "");
            if (!user.containsKey("vip_purchase_history")) user.put("vip_purchase_history", new ArrayList<>());
            if (!user.containsKey("vip_pending_order_id")) user.put("vip_pending_order_id", "");
            if (!user.containsKey("recharge_history")) user.put("recharge_history", new ArrayList<>());
            if (!user.containsKey("avatar")) user.put("avatar", "default:none");
        }
        saveUsers();
    }

    public static boolean isValidPhone(String phone) { return phone != null && PHONE_RE.matcher(phone).matches(); }

    // ============== 任务系统 ==============
    public static List<Map<String, Object>> getTodayDailyTasks() {
        // 真实实现按日期播种采样 3 个；这里用简单 hash 选 3 个
        String today = LocalDate.now().toString();
        int seed = today.hashCode();
        List<Map<String, Object>> copy = new ArrayList<>(DAILY_TASK_POOL);
        Collections.shuffle(copy, new java.util.Random(seed));
        return copy.subList(0, 3);
    }

    @SuppressWarnings("unchecked")
    public static String getTaskStatus(String taskId, Map<String, Object> user, String taskType) {
        String today = LocalDate.now().toString();
        if ("fixed".equals(taskType)) {
            Map<String, Object> tasks = (Map<String, Object>) user.getOrDefault("tasks", new LinkedHashMap<>());
            Map<String, Object> rec = (Map<String, Object>) tasks.get(taskId);
            if (rec != null && Boolean.TRUE.equals(rec.get("completed"))) return "completed";
            return "available";
        }
        Map<String, Object> completed = (Map<String, Object>) user.getOrDefault("daily_tasks_completed", new LinkedHashMap<>());
        return today.equals(completed.get(taskId)) ? "completed" : "available";
    }

    @SuppressWarnings("unchecked")
    public static boolean autoCompleteTask(String username, String taskId) {
        Map<String, Object> user = users.get(username);
        if (user == null) return false;

        for (Map<String, Object> t : FIXED_TASKS) {
            if (taskId.equals(t.get("id"))) {
                if ("completed".equals(getTaskStatus(taskId, user, "fixed"))) return false;
                Map<String, Object> tasks = (Map<String, Object>) user.computeIfAbsent("tasks", k -> new LinkedHashMap<>());
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("completed", true);
                rec.put("last_date", LocalDate.now().toString());
                tasks.put(taskId, rec);
                user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + ((Number) t.get("reward")).intValue());
                saveUsers();
                return true;
            }
        }

        for (Map<String, Object> t : getTodayDailyTasks()) {
            if (taskId.equals(t.get("id"))) {
                if ("completed".equals(getTaskStatus(taskId, user, "daily"))) return false;
                Map<String, Object> completed = (Map<String, Object>) user.computeIfAbsent("daily_tasks_completed", k -> new LinkedHashMap<>());
                completed.put(taskId, LocalDate.now().toString());
                user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + ((Number) t.get("reward")).intValue());
                saveUsers();
                return true;
            }
        }
        return false;
    }

    // ============== 管理员每日积分 ==============
    public static void grantAdminDailyPoints(String username) {
        Map<String, Object> user = users.get(username);
        if (user == null || !Boolean.TRUE.equals(user.get("is_admin"))) return;
        String today = LocalDate.now().toString();
        String lastDate = (String) user.getOrDefault("admin_daily_points_date", "");
        int count = ((Number) user.getOrDefault("admin_daily_points_count", 0)).intValue();
        if (!today.equals(lastDate)) {
            user.put("admin_daily_points_date", today);
            user.put("admin_daily_points_count", 0);
            count = 0;
            saveUsers();
        }
        if (count < 2) {
            user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + 10000);
            user.put("admin_daily_points_count", count + 1);
            saveUsers();
            System.out.println("[管理员] " + username + " 获得每日 10000 积分奖励 (第" + (count + 1) + "次)");
        }
    }

    // ============== VIP 系统 ==============
    /** 返回 [is_vip, tier, expires_at_str, days_left]。 */
    public static Object[] isUserVip(Map<String, Object> user) {
        String tier = (String) user.getOrDefault("vip_tier", "");
        String exp = (String) user.getOrDefault("vip_expires_at", "");
        if (tier.isEmpty() || exp.isEmpty()) return new Object[]{false, "", null, 0};
        try {
            LocalDateTime expiresAt = LocalDateTime.parse(exp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime now = LocalDateTime.now();
            if (expiresAt.isBefore(now) || expiresAt.isEqual(now)) return new Object[]{false, tier, expiresAt, 0};
            long days = ChronoUnit.DAYS.between(now, expiresAt) + 1;
            return new Object[]{true, tier, expiresAt, Math.max(days, 1)};
        } catch (Exception e) {
            return new Object[]{false, "", null, 0};
        }
    }

    @SuppressWarnings("unchecked")
    public static void grantVipDailyBonus(String username) {
        Map<String, Object> user = users.get(username);
        if (user == null) return;
        Object[] vip = isUserVip(user);
        if (!(Boolean) vip[0]) return;
        String tier = (String) vip[1];
        String today = LocalDate.now().toString();
        if (today.equals(user.get("last_vip_bonus_date"))) return;
        Map<String, Object> tierInfo = VIP_TIERS.getOrDefault(tier, Map.of());
        int bonus = ((Number) tierInfo.getOrDefault("daily_bonus", 0)).intValue();
        if (bonus <= 0) return;
        user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + bonus);
        user.put("last_vip_bonus_date", today);
        saveUsers();
        System.out.println("[VIP] " + username + " (" + tier + ") 获得每日 " + bonus + " 积分奖励 (剩余 " + vip[3] + " 天)");
    }

    public static void cleanupExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        for (String k : new ArrayList<>(pendingOrders.keySet())) {
            Map<String, Object> v = pendingOrders.get(k);
            if (v.get("paid_at") != null && !((String) v.get("paid_at")).isEmpty()) continue;
            try {
                LocalDateTime created = LocalDateTime.parse(
                    (String) v.get("created_at"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (ChronoUnit.SECONDS.between(created, now) > 15 * 60) {
                    pendingOrders.remove(k);
                }
            } catch (Exception e) { pendingOrders.remove(k); }
        }
    }

    // ============== 商品价格 ==============
    public static double getDailyMultiplier(String itemName) {
        String today = LocalDate.now().toString();
        String key = today + "_" + itemName;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long hashVal = 0;
            for (int i = 0; i < 4; i++) hashVal = (hashVal << 8) | (hash[i] & 0xff);
            return 0.8 + (Math.abs(hashVal) % 1000) / 1000.0 * 0.4;
        } catch (Exception e) { return 1.0; }
    }

    public static int getDailyReward(String itemName, String rewardType) {
        Map<String, Object> item = ITEM_DATA.get(itemName);
        if (item == null) return 0;
        int base = ((Number) item.get("use".equals(rewardType) ? "base_use_reward" : "base_sell_reward")).intValue();
        return (int)(base * getDailyMultiplier(itemName));
    }

    public static String getItemEffect(String itemName) {
        Map<String, Object> item = ITEM_DATA.get(itemName);
        return item != null ? (String) item.get("effect") : "";
    }

    public static boolean allowedFile(String filename) {
        if (filename == null || !filename.contains(".")) return false;
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    // ============== 路由（31 个，对应 Flask app.py）==============
    // 真实生产版本拆分为 12 个 Spring Boot Controller；这里合并为一个类保持 Python 结构

    public Map<String, Object> home(Map<String, Object> session) {
        String username = (String) session.get("username");
        Long userPoints = null;
        List<String> unlockedContent = new ArrayList<>();
        boolean mouseEffectEnabled = false;
        String avatar = null;
        boolean hasCyberTshirt = false;
        boolean hasClock = false;
        if (username != null && users.containsKey(username)) {
            Map<String, Object> user = users.get(username);
            grantAdminDailyPoints(username);
            grantVipDailyBonus(username);
            userPoints = ((Number) user.getOrDefault("points", 0)).longValue();
            unlockedContent = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
            Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
            mouseEffectEnabled = effects.getOrDefault("mouse_trail", false);
            avatar = (String) user.getOrDefault("avatar", "default:none");
            hasCyberTshirt = effects.getOrDefault("cyber_tshirt", false);
            hasClock = unlockedContent.contains("时钟");
            String today = LocalDate.now().toString();
            if (!today.equals(user.get("last_visit_date"))) {
                user.put("daily_visit_count", 1);
                user.put("last_visit_date", today);
            } else {
                user.put("daily_visit_count", ((Number) user.getOrDefault("daily_visit_count", 0)).intValue() + 1);
            }
            saveUsers();
            if (((Number) user.getOrDefault("daily_visit_count", 0)).intValue() >= 3) {
                autoCompleteTask(username, "daily_visit");
                userPoints = ((Number) users.get(username).getOrDefault("points", 0)).longValue();
            }
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("messages", messages);
        model.put("username", username);
        model.put("user_points", userPoints);
        model.put("unlocked_content", unlockedContent);
        model.put("mouse_effect_enabled", mouseEffectEnabled);
        model.put("avatar", avatar);
        model.put("has_cyber_tshirt", hasCyberTshirt);
        model.put("has_clock", hasClock);
        model.put("template", "board.html");
        return model;
    }

    public Map<String, Object> messageBoard(Map<String, Object> session) {
        String username = (String) session.get("username");
        Long userPoints = null;
        List<String> unlockedContent = new ArrayList<>();
        boolean mouseEffectEnabled = false;
        boolean isAdmin = false;
        String avatar = null;
        boolean hasClock = false;
        if (username != null && users.containsKey(username)) {
            Map<String, Object> user = users.get(username);
            grantAdminDailyPoints(username);
            grantVipDailyBonus(username);
            userPoints = ((Number) user.getOrDefault("points", 0)).longValue();
            unlockedContent = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
            Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
            mouseEffectEnabled = effects.getOrDefault("mouse_trail", false);
            isAdmin = Boolean.TRUE.equals(user.get("is_admin"));
            avatar = (String) user.getOrDefault("avatar", "default:none");
            hasClock = unlockedContent.contains("时钟");
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("messages", messages);
        model.put("username", username);
        model.put("user_points", userPoints);
        model.put("unlocked_content", unlockedContent);
        model.put("mouse_effect_enabled", mouseEffectEnabled);
        model.put("is_admin", isAdmin);
        model.put("avatar", avatar);
        model.put("has_clock", hasClock);
        model.put("template", "message_board.html");
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> contentPage(Map<String, Object> session, String itemName) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("redirect", "/login");
        Map<String, Object> item = ITEM_DATA.get(itemName);
        if (item == null || item.get("content_page") == null) return Map.of("status", 404, "msg", "该商品无内容页");
        Object[] vip = isUserVip(user);
        boolean vipAllowed = (Boolean) vip[0] && VIP_TUTORIAL_WHITELIST.contains(itemName);
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        if (!unlocked.contains(itemName) && !vipAllowed) {
            return Map.of("redirect", "/store", "flash", "请先购买并使用「" + itemName + "」解锁内容");
        }
        grantAdminDailyPoints(username);
        grantVipDailyBonus(username);
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", item.get("content_page"));
        model.put("item_name", itemName);
        model.put("username", username);
        model.put("unlocked_content", unlocked);
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        model.put("has_clock", unlocked.contains("时钟"));
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(Map<String, Object> session, String message) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/post");
        if (message == null || message.isEmpty()) return Map.of("redirect", "/post");
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("name", username);
        msg.put("msg", message);
        messages.add(msg);
        saveMessages();
        autoCompleteTask(username, "post_message");
        Map<String, Object> user = users.get(username);
        if (user != null) {
            String today = LocalDate.now().toString();
            if (!today.equals(user.get("last_post_date"))) {
                autoCompleteTask(username, "daily_post");
                user.put("last_post_date", today);
                saveUsers();
            }
        }
        return Map.of("redirect", "/post");
    }

    public Map<String, Object> delete(Map<String, Object> session, int index) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/post");
        if (index >= 0 && index < messages.size()) {
            Map<String, Object> msg = messages.get(index);
            Map<String, Object> user = users.get(username);
            boolean isAdmin = user != null && Boolean.TRUE.equals(user.get("is_admin"));
            if (username.equals(msg.get("name")) || isAdmin) {
                messages.remove(index);
                saveMessages();
            }
        }
        return Map.of("redirect", "/post");
    }

    public Map<String, Object> register(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Map.of("redirect", "/register", "flash", "用户名和密码都不能为空");
        }
        if (password.length() < 8 || password.length() > 16) {
            return Map.of("redirect", "/register", "flash", "密码长度必须在8到16位之间");
        }
        if (users.containsKey(username)) {
            return Map.of("redirect", "/register", "flash", "用户名已存在");
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("password_hash", password);  // 真实生产用 BCryptPasswordEncoder.encode()
        user.put("phone", "");
        user.put("points", 100);
        user.put("inventory", new LinkedHashMap<>());
        user.put("tasks", new LinkedHashMap<>());
        user.put("daily_tasks_completed", new LinkedHashMap<>());
        user.put("daily_visit_count", 0);
        user.put("last_visit_date", "");
        user.put("last_post_date", "");
        user.put("total_spent", 0);
        user.put("unlocked_content", new ArrayList<String>());
        user.put("effects", new LinkedHashMap<>());
        user.put("is_admin", "顾璟瑶".equals(username));
        user.put("admin_daily_points_date", "");
        user.put("admin_daily_points_count", 0);
        user.put("mouse_effect_config", Map.of("enabled", true, "color_mode", "rainbow", "shape", "circle"));
        user.put("avatar", "default:none");
        user.put("vip_tier", "");
        user.put("vip_expires_at", "");
        user.put("last_vip_bonus_date", "");
        user.put("vip_purchase_history", new ArrayList<>());
        user.put("vip_pending_order_id", "");
        user.put("recharge_history", new ArrayList<>());
        users.put(username, user);
        saveUsers();
        return Map.of("redirect", "/login?next=/", "flash", "注册成功，请登录");
    }

    public Map<String, Object> login(Map<String, Object> session, String username, String password, String nextUrl) {
        if (username != null && users.containsKey(username)) {
            Map<String, Object> user = users.get(username);
            String storedHash = (String) user.get("password_hash");
            if (storedHash != null && storedHash.equals(password)) {  // 真实生产用 BCryptPasswordEncoder.matches()
                session.put("username", username);
                grantAdminDailyPoints(username);
                grantVipDailyBonus(username);
                autoCompleteTask(username, "daily_login");
                return Map.of("redirect", nextUrl != null ? nextUrl : "/");
            }
        }
        return Map.of("redirect", "/login?next=" + (nextUrl != null ? nextUrl : "/"), "flash", "用户名或密码错误");
    }

    public Map<String, Object> logout(Map<String, Object> session) {
        session.remove("username");
        return Map.of("redirect", "/");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> settings(Map<String, Object> session, String oldPwd, String newPwd, String confirmPwd) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/settings");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        grantAdminDailyPoints(username);
        grantVipDailyBonus(username);
        if (oldPwd != null && newPwd != null && confirmPwd != null) {
            String storedHash = (String) user.get("password_hash");
            if (!storedHash.equals(oldPwd)) return Map.of("redirect", "/settings", "flash", "原密码错误");
            if (!newPwd.equals(confirmPwd)) return Map.of("redirect", "/settings", "flash", "两次新密码不一致");
            if (newPwd.length() < 8 || newPwd.length() > 16) return Map.of("redirect", "/settings", "flash", "新密码长度必须在8~16位之间");
            user.put("password_hash", newPwd);
            saveUsers();
            return Map.of("redirect", "/settings", "flash", "密码更新成功");
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "settings.html");
        model.put("username", username);
        model.put("user", user);
        model.put("phone", user.get("phone"));
        model.put("points", user.getOrDefault("points", 0));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        model.put("unlocked_content", unlocked);
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("has_clock", unlocked.contains("时钟"));
        model.put("MC_SERVER_CONFIG", MC_SERVER_CONFIG);
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateAvatar(Map<String, Object> session, String action, String filename, byte[] fileBytes) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "msg", "请先登录");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        if ("reset".equals(action)) {
            user.put("avatar", "default:none");
            saveUsers();
            return Map.of("success", true, "msg", "已恢复默认头像", "avatar_url", "default:none");
        }
        if (filename == null || filename.isEmpty()) return Map.of("status", 400, "msg", "未选择文件");
        if (!allowedFile(filename)) return Map.of("status", 400, "msg", "不支持的文件格式");
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        long ts = System.currentTimeMillis() / 1000;
        String newFilename = username + "_" + ts + "." + ext;
        try {
            Files.write(Paths.get(AVATAR_FOLDER.getAbsolutePath(), newFilename), fileBytes);
        } catch (IOException e) {
            return Map.of("status", 500, "msg", "保存失败");
        }
        String avatarUrl = "/static/avatars/" + newFilename;
        user.put("avatar", avatarUrl);
        saveUsers();
        return Map.of("success", true, "msg", "头像上传成功", "avatar_url", avatarUrl);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> store(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/store");
        Map<String, Object> user = users.get(username);
        grantAdminDailyPoints(username);
        grantVipDailyBonus(username);
        Object[] vip = isUserVip(user);
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "store.html");
        model.put("username", username);
        model.put("points", user.getOrDefault("points", 0));
        model.put("items", ITEM_DATA);
        model.put("unlocked_content", unlocked);
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        model.put("has_clock", unlocked.contains("时钟"));
        model.put("packages", VIP_PACKAGES);
        model.put("is_vip", vip[0]);
        model.put("vip_tier", vip[1]);
        model.put("vip_days_left", vip[3]);
        model.put("vip_expires_at_str", user.getOrDefault("vip_expires_at", ""));
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buy(Map<String, Object> session, String item) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        if (item == null || !ITEM_DATA.containsKey(item)) return Map.of("status", 400, "success", false, "msg", "无效商品");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        int price = ((Number) ITEM_DATA.get(item).get("price")).intValue();
        if ((Boolean) isUserVip(user)[0]) price = (int) (price * 0.9);
        long cur = ((Number) user.getOrDefault("points", 0)).longValue();
        if (cur < price) return Map.of("status", 400, "success", false, "msg", "积分不足");
        user.put("points", cur - price);
        Map<String, Integer> inv = (Map<String, Integer>) user.computeIfAbsent("inventory", k -> new LinkedHashMap<>());
        inv.put(item, inv.getOrDefault(item, 0) + 1);
        long totalSpent = ((Number) user.getOrDefault("total_spent", 0)).longValue() + price;
        user.put("total_spent", totalSpent);
        saveUsers();
        autoCompleteTask(username, "buy_item");
        if (totalSpent >= 500) autoCompleteTask(username, "spend_master");
        autoCompleteTask(username, "daily_buy");
        return Map.of("success", true, "msg", "成功购买 " + item, "new_points", user.get("points"), "inventory", inv);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> inventory(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/inventory");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        grantAdminDailyPoints(username);
        grantVipDailyBonus(username);
        Map<String, Integer> inv = (Map<String, Integer>) user.getOrDefault("inventory", new LinkedHashMap<>());
        inv.entrySet().removeIf(e -> e.getValue() <= 0);
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "inventory.html");
        model.put("username", username);
        model.put("inventory", inv);
        model.put("points", user.getOrDefault("points", 0));
        model.put("item_data", ITEM_DATA);
        model.put("unlocked_content", unlocked);
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        model.put("has_clock", unlocked.contains("时钟"));
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> useItem(Map<String, Object> session, String item, String action) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        if (item == null || !ITEM_DATA.containsKey(item) || !Arrays.asList("use", "sell").contains(action)) {
            return Map.of("status", 400, "success", false, "msg", "无效操作");
        }
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        Map<String, Integer> inv = (Map<String, Integer>) user.getOrDefault("inventory", new LinkedHashMap<>());
        if (inv.getOrDefault(item, 0) <= 0) return Map.of("status", 400, "success", false, "msg", "没有 " + item);

        if ("use".equals(action)) {
            int reward = getDailyReward(item, "use");
            String effect = getItemEffect(item);
            inv.put(item, inv.get(item) - 1);
            if (inv.get(item) <= 0) inv.remove(item);
            user.put("inventory", inv);
            user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + reward);
            saveUsers();
            autoCompleteTask(username, "use_item");
            autoCompleteTask(username, "daily_use");
            Map<String, Object> itemInfo = ITEM_DATA.get(item);
            String redirectUrl = null;
            String contentPage = (String) itemInfo.get("content_page");
            if (contentPage != null) {
                List<String> unlocked = (List<String>) user.computeIfAbsent("unlocked_content", k -> new ArrayList<String>());
                if (!unlocked.contains(item)) {
                    unlocked.add(item);
                    saveUsers();
                }
                redirectUrl = "/content/" + item;
            }
            if ("影视播放器".equals(item)) redirectUrl = "/video_player";
            // 时钟 / 降噪耳机 / 电竞鼠标 / 赛博T恤：解锁标记
            if (Arrays.asList("时钟", "降噪耳机").contains(item)) {
                List<String> unlocked = (List<String>) user.computeIfAbsent("unlocked_content", k -> new ArrayList<String>());
                if (!unlocked.contains(item)) {
                    unlocked.add(item);
                    saveUsers();
                }
            }
            if ("电竞鼠标".equals(item)) {
                Map<String, Boolean> effects = (Map<String, Boolean>) user.computeIfAbsent("effects", k -> new LinkedHashMap<>());
                effects.put("mouse_trail", true);
                saveUsers();
            }
            if ("赛博 T 恤".equals(item)) {
                Map<String, Boolean> effects = (Map<String, Boolean>) user.computeIfAbsent("effects", k -> new LinkedHashMap<>());
                effects.put("cyber_tshirt", true);
                saveUsers();
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("msg", "使用了 " + item + "，获得 " + reward + " 积分");
            resp.put("new_points", user.get("points"));
            resp.put("inventory", inv);
            resp.put("effect", effect);
            resp.put("redirect_url", redirectUrl);
            return resp;
        }
        // sell
        int sellReward = getDailyReward(item, "sell");
        inv.put(item, inv.get(item) - 1);
        if (inv.get(item) <= 0) inv.remove(item);
        user.put("inventory", inv);
        user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + sellReward);
        saveUsers();
        return Map.of("success", true, "msg", "出售 " + item + "，获得 " + sellReward + " 积分",
                      "new_points", user.get("points"), "inventory", inv);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> tasksPage(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/tasks");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        grantAdminDailyPoints(username);
        grantVipDailyBonus(username);
        List<Map<String, Object>> fixedWithStatus = new ArrayList<>();
        for (Map<String, Object> t : FIXED_TASKS) {
            Map<String, Object> tt = new LinkedHashMap<>(t);
            tt.put("status", getTaskStatus((String) t.get("id"), user, "fixed"));
            tt.put("type", "fixed");
            fixedWithStatus.add(tt);
        }
        List<Map<String, Object>> dailyWithStatus = new ArrayList<>();
        for (Map<String, Object> t : getTodayDailyTasks()) {
            Map<String, Object> tt = new LinkedHashMap<>(t);
            tt.put("status", getTaskStatus((String) t.get("id"), user, "daily"));
            tt.put("type", "daily");
            dailyWithStatus.add(tt);
        }
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "tasks.html");
        model.put("username", username);
        model.put("fixed_tasks", fixedWithStatus);
        model.put("daily_tasks", dailyWithStatus);
        model.put("points", user.getOrDefault("points", 0));
        model.put("unlocked_content", unlocked);
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        model.put("has_clock", unlocked.contains("时钟"));
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> gameCenter(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/game_center");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        if (!unlocked.contains("游戏手柄")) {
            return Map.of("redirect", "/store", "flash", "请先购买并使用游戏手柄解锁游戏中心");
        }
        grantVipDailyBonus(username);
        List<String> unlockedGames = new ArrayList<>();
        for (String g : GAME_LIST.keySet()) if (unlocked.contains(g)) unlockedGames.add(g);
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "game_center.html");
        model.put("username", username);
        model.put("points", user.getOrDefault("points", 0));
        model.put("games", GAME_LIST);
        model.put("unlocked_games", unlockedGames);
        model.put("unlocked_content", unlocked);
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        model.put("has_clock", unlocked.contains("时钟"));
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buyGame(Map<String, Object> session, String game) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        if (game == null || !GAME_LIST.containsKey(game)) return Map.of("status", 400, "success", false, "msg", "无效游戏");
        int price = ((Number) GAME_LIST.get(game).get("price")).intValue();
        if (price == 0) return Map.of("status", 400, "success", false, "msg", "该游戏免费，无需购买");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        if ((Boolean) isUserVip(user)[0]) price = (int) (price * 0.9);
        if (((Number) user.getOrDefault("points", 0)).longValue() < price) {
            return Map.of("status", 400, "success", false, "msg", "积分不足");
        }
        user.put("points", ((Number) user.get("points")).longValue() - price);
        List<String> unlocked = (List<String>) user.computeIfAbsent("unlocked_content", k -> new ArrayList<String>());
        if (!unlocked.contains(game)) unlocked.add(game);
        saveUsers();
        return Map.of("success", true, "msg", "成功购买游戏 " + game, "new_points", user.get("points"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> playGame(Map<String, Object> session, String gameName) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/play_game/" + gameName);
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        if (!"我的世界".equals(gameName)) {
            List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
            if (!unlocked.contains(gameName)) return Map.of("redirect", "/game_center", "flash", "请先购买该游戏");
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "play_game.html");
        model.put("game_name", gameName);
        model.put("username", username);
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMouseConfig(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "error", "未登录");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "error", "用户不存在");
        Map<String, Object> cfg = new LinkedHashMap<>((Map<String, Object>) user.getOrDefault(
            "mouse_effect_config", Map.of("enabled", true, "color_mode", "rainbow", "shape", "circle")));
        cfg.putIfAbsent("enabled", true);
        cfg.putIfAbsent("color_mode", "rainbow");
        cfg.putIfAbsent("shape", "circle");
        return cfg;
    }

    public Map<String, Object> saveMouseConfig(Map<String, Object> session, Boolean enabled, String colorMode, String shape) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "未登录");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        boolean en = enabled == null ? true : enabled;
        String cm = colorMode == null ? "rainbow" : colorMode;
        String sh = shape == null ? "circle" : shape;
        if (!Arrays.asList("rainbow", "cyan", "random").contains(cm)) cm = "rainbow";
        if (!Arrays.asList("circle", "square", "star").contains(sh)) sh = "circle";
        user.put("mouse_effect_config", Map.of("enabled", en, "color_mode", cm, "shape", sh));
        saveUsers();
        return Map.of("success", true);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> videoPlayer(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/video_player");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "msg", "用户不存在");
        List<String> unlocked = (List<String>) user.getOrDefault("unlocked_content", new ArrayList<String>());
        if (!unlocked.contains("影视播放器") && !(Boolean) isUserVip(user)[0]) {
            return Map.of("redirect", "/store", "flash", "请先购买并使用影视播放器解锁");
        }
        Map<String, Boolean> effects = (Map<String, Boolean>) user.getOrDefault("effects", new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "video_player.html");
        model.put("username", username);
        model.put("unlocked_content", unlocked);
        model.put("mouse_effect_enabled", effects.getOrDefault("mouse_trail", false));
        model.put("avatar", user.getOrDefault("avatar", "default:none"));
        model.put("has_cyber_tshirt", effects.getOrDefault("cyber_tshirt", false));
        model.put("has_clock", unlocked.contains("时钟"));
        return model;
    }

    // ============== VIP 路由 ==============
    public Map<String, Object> vipPage(Map<String, Object> session) {
        cleanupExpiredOrders();
        String username = (String) session.get("username");
        Object[] vip = new Object[]{false, "", null, 0};
        String vipExpiresAtStr = "";
        if (username != null && users.containsKey(username)) {
            vip = isUserVip(users.get(username));
            vipExpiresAtStr = (String) users.get(username).getOrDefault("vip_expires_at", "");
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "vip.html");
        model.put("username", username);
        model.put("packages", VIP_PACKAGES);
        model.put("is_vip", vip[0]);
        model.put("vip_tier", vip[1]);
        model.put("vip_days_left", vip[3]);
        model.put("vip_expires_at_str", vipExpiresAtStr);
        return model;
    }

    public Map<String, Object> buyVip(Map<String, Object> session, String pkgKey) {
        cleanupExpiredOrders();
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        if (pkgKey == null || !VIP_PACKAGES.containsKey(pkgKey)) return Map.of("status", 400, "success", false, "msg", "无效套餐");
        // 清掉该用户所有未支付订单
        for (String k : new ArrayList<>(pendingOrders.keySet())) {
            Map<String, Object> v = pendingOrders.get(k);
            if (username.equals(v.get("username")) && (v.get("paid_at") == null || ((String) v.get("paid_at")).isEmpty())) {
                pendingOrders.remove(k);
            }
        }
        Map<String, Object> pkg = VIP_PACKAGES.get(pkgKey);
        String orderId = UUID.randomUUID().toString().substring(0, 16);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("order_id", orderId);
        order.put("username", username);
        order.put("tier", pkg.get("tier"));
        order.put("duration_days", pkg.get("duration_days"));
        order.put("price_cny", pkg.get("price_cny"));
        order.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        order.put("paid_at", "");
        pendingOrders.put(orderId, order);
        user.put("vip_pending_order_id", orderId);
        saveUsers();
        return Map.of("success", true, "order_id", orderId, "redirect", "/vip_pay/" + orderId);
    }

    public Map<String, Object> vipPay(Map<String, Object> session, String orderId) {
        cleanupExpiredOrders();
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login");
        Map<String, Object> order = pendingOrders.get(orderId);
        if (order == null) return Map.of("status", 404, "msg", "订单不存在或已过期");
        if (!username.equals(order.get("username"))) return Map.of("status", 403, "msg", "无权访问该订单");
        if (order.get("paid_at") != null && !((String) order.get("paid_at")).isEmpty()) {
            return Map.of("redirect", "/settings");
        }
        Map<String, Object> pkg = VIP_PACKAGES.get(order.get("tier") + "-" + order.get("duration_days"));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "vip_pay.html");
        model.put("order", order);
        model.put("pkg", pkg);
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> vipPayConfirm(Map<String, Object> session, String orderId) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        Map<String, Object> order = pendingOrders.get(orderId);
        if (order == null || !username.equals(order.get("username"))) return Map.of("status", 400, "success", false, "msg", "订单无效");
        if (order.get("paid_at") != null && !((String) order.get("paid_at")).isEmpty()) return Map.of("status", 400, "success", false, "msg", "订单已支付");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        LocalDateTime now = LocalDateTime.now();
        String oldExpStr = (String) user.getOrDefault("vip_expires_at", "");
        LocalDateTime base = now;
        if (!oldExpStr.isEmpty()) {
            try {
                LocalDateTime oldExp = LocalDateTime.parse(oldExpStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (oldExp.isAfter(now)) base = oldExp;
            } catch (Exception ignored) {}
        }
        LocalDateTime newExp = base.plusDays(((Number) order.get("duration_days")).longValue());
        user.put("vip_tier", order.get("tier"));
        user.put("vip_expires_at", newExp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        List<Map<String, Object>> history = (List<Map<String, Object>>) user.computeIfAbsent("vip_purchase_history", k -> new ArrayList<>());
        history.add(Map.of(
            "tier", order.get("tier"),
            "duration_days", order.get("duration_days"),
            "price_cny", order.get("price_cny"),
            "paid_at", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "order_id", orderId
        ));
        user.put("vip_pending_order_id", "");
        order.put("paid_at", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        saveUsers();
        return Map.of("success", true, "msg", "VIP 开通成功", "redirect", "/settings");
    }

    // ============== 积分充值路由 ==============
    @SuppressWarnings("unchecked")
    public Map<String, Object> recharge(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login?next=/recharge");
        Map<String, Object> user = users.getOrDefault(username, new LinkedHashMap<>());
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "recharge.html");
        model.put("packages", RECHARGE_PACKAGES);
        model.put("points", user.getOrDefault("points", 0));
        return model;
    }

    public Map<String, Object> buyRecharge(Map<String, Object> session, String pkgKey) {
        cleanupExpiredOrders();
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        if (pkgKey == null || !RECHARGE_PACKAGES.containsKey(pkgKey)) return Map.of("status", 400, "success", false, "msg", "无效充值套餐");
        for (String k : new ArrayList<>(pendingOrders.keySet())) {
            Map<String, Object> v = pendingOrders.get(k);
            if (username.equals(v.get("username")) && (v.get("paid_at") == null || ((String) v.get("paid_at")).isEmpty())
                && "recharge".equals(v.get("kind"))) {
                pendingOrders.remove(k);
            }
        }
        Map<String, Object> pkg = RECHARGE_PACKAGES.get(pkgKey);
        String orderId = UUID.randomUUID().toString().substring(0, 16);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("order_id", orderId);
        order.put("kind", "recharge");
        order.put("username", username);
        order.put("pkg_key", pkgKey);
        order.put("price_cny", pkg.get("price_cny"));
        order.put("points", pkg.get("points"));
        order.put("bonus_points", pkg.get("bonus_points"));
        order.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        order.put("paid_at", "");
        pendingOrders.put(orderId, order);
        saveUsers();
        return Map.of("success", true, "order_id", orderId, "redirect", "/recharge_pay/" + orderId);
    }

    public Map<String, Object> rechargePay(Map<String, Object> session, String orderId) {
        cleanupExpiredOrders();
        String username = (String) session.get("username");
        if (username == null) return Map.of("redirect", "/login");
        Map<String, Object> order = pendingOrders.get(orderId);
        if (order == null || !"recharge".equals(order.get("kind"))) return Map.of("status", 404, "msg", "订单不存在或已过期");
        if (!username.equals(order.get("username"))) return Map.of("status", 403, "msg", "无权访问该订单");
        if (order.get("paid_at") != null && !((String) order.get("paid_at")).isEmpty()) return Map.of("redirect", "/settings");
        Map<String, Object> pkg = RECHARGE_PACKAGES.get(order.get("pkg_key"));
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("template", "recharge_pay.html");
        model.put("order", order);
        model.put("pkg", pkg);
        return model;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> rechargePayConfirm(Map<String, Object> session, String orderId) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        Map<String, Object> order = pendingOrders.get(orderId);
        if (order == null || !username.equals(order.get("username")) || !"recharge".equals(order.get("kind"))) {
            return Map.of("status", 400, "success", false, "msg", "订单无效");
        }
        if (order.get("paid_at") != null && !((String) order.get("paid_at")).isEmpty()) return Map.of("status", 400, "success", false, "msg", "订单已支付");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        int total = ((Number) order.get("points")).intValue() + ((Number) order.getOrDefault("bonus_points", 0)).intValue();
        user.put("points", ((Number) user.getOrDefault("points", 0)).longValue() + total);
        List<Map<String, Object>> history = (List<Map<String, Object>>) user.computeIfAbsent("recharge_history", k -> new ArrayList<>());
        history.add(Map.of(
            "pkg_key", order.get("pkg_key"),
            "price_cny", order.get("price_cny"),
            "points", order.get("points"),
            "bonus", order.getOrDefault("bonus_points", 0),
            "paid_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "order_id", orderId
        ));
        order.put("paid_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        saveUsers();
        return Map.of("success", true, "msg", "充值成功，到账 " + total + " 积分", "redirect", "/recharge");
    }

    // ============== API 路由 ==============
    public Map<String, Object> apiMcServerInfo(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null) return Map.of("status", 401, "success", false, "msg", "请先登录");
        Map<String, Object> user = users.get(username);
        if (user == null) return Map.of("status", 404, "success", false, "msg", "用户不存在");
        Object[] vip = isUserVip(user);
        if (!(Boolean) vip[0]) return Map.of("status", 403, "success", false, "msg", "需要 VIP 会员");
        return Map.of("success", true, "config", MC_SERVER_CONFIG);
    }

    public Map<String, Object> apiVipStatus(Map<String, Object> session) {
        String username = (String) session.get("username");
        if (username == null || !users.containsKey(username)) {
            return Map.of("is_vip", false, "tier", "", "days_left", 0, "expires_at", "");
        }
        Object[] vip = isUserVip(users.get(username));
        String expiresAt = "";
        if (vip[2] instanceof LocalDateTime dt) {
            expiresAt = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("is_vip", vip[0]);
        resp.put("tier", vip[1]);
        resp.put("days_left", vip[3]);
        resp.put("expires_at", expiresAt);
        return resp;
    }

    // ============== 入口 ==============
    public static void main(String[] args) {
        loadMessages();
        loadUsers();
        System.out.println("App 已加载 " + users.size() + " 个用户, " + messages.size() + " 条留言");
        System.out.println("提示：此为 Python app.py 的 Java 翻译存档，不会被 Maven 编译。");
        System.out.println("实际运行请用 start.bat / start.sh");
    }
}
