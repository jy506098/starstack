package app.client;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 贪吃蛇大作战 WebSocket 联机服务器 (Java 翻译版)。
 *
 * Java 移植说明：
 * - 房间逻辑（碰撞检测、食物生成、tick 推进）按 Python Room 类 1:1 port
 * - 真实运行版本位于 src/main/java/com/starstack/ws/SnakeRoom.java + SnakeWebSocketHandler.java
 * - 房间 tick 用 ScheduledExecutorService 替代 Python gevent.spawn(_game_loop)
 * - 此文件保留作为 Python 联机服务的 Java 翻译存档，不会被 Maven 编译
 */
public final class SnakeServer {

    // ---------------- 房间配置 ----------------
    public static final int ROOM_W = 40;
    public static final int ROOM_H = 30;
    public static final long TICK_MS = 80;        // 每帧逻辑 tick (毫秒)
    public static final int FOOD_COUNT = 30;      // 每房间目标食物数

    // 颜色池（玩家蛇身颜色循环使用）
    private static final String[] COLOR_POOL = {
        "#ff4d6d", "#ff8c42", "#ffd166", "#06d6a0", "#118ab2",
        "#8338ec", "#3a86ff", "#ef476f", "#f72585", "#4cc9f0"
    };

    // ---------------- 全局房间池 ----------------
    private static final Map<String, Room> ROOMS = new ConcurrentHashMap<>();
    // WebSocket session -> (room_id, player_name) 反向索引
    private static final Map<Object, String[]> WS_INDEX = new ConcurrentHashMap<>();
    private static final SecureRandom RAND = new SecureRandom();

    private SnakeServer() {}

    /** 一个游戏房间。 */
    public static final class Room {
        public final String id;
        public final Map<Object, Player> players = new ConcurrentHashMap<>();
        public final List<int[]> foods = Collections.synchronizedList(new ArrayList<>());
        public final long createdAt = System.currentTimeMillis();

        public Room(String id) { this.id = id; }

        public void addPlayer(Object ws, String name) {
            String color = COLOR_POOL[players.size() % COLOR_POOL.length];
            int spawnX = 5 + RAND.nextInt(ROOM_W - 10);
            int spawnY = 5 + RAND.nextInt(ROOM_H - 10);
            players.put(ws, new Player(name, spawnX, spawnY, color));
            refillFood();
        }

        public void removePlayer(Object ws) { players.remove(ws); }

        public void refillFood() {
            while (foods.size() < FOOD_COUNT) {
                int x = RAND.nextInt(ROOM_W);
                int y = RAND.nextInt(ROOM_H);
                int[] f = {x, y};
                if (!foods.contains(f)) foods.add(f);
            }
        }

        /** 推进一帧逻辑。 */
        public void tick() {
            for (Map.Entry<Object, Player> e : players.entrySet()) {
                Object ws = e.getKey();
                Player p = e.getValue();
                if (!p.alive) continue;
                // 应用待生效的方向（禁止 180° 反向）
                if (p.nextDx + p.dx != 0 || p.nextDy + p.dy != 0) {
                    p.dx = p.nextDx; p.dy = p.nextDy;
                }
                int[] head = p.snake.get(0);
                int newX = head[0] + p.dx, newY = head[1] + p.dy;
                // 撞墙
                if (newX < 0 || newX >= ROOM_W || newY < 0 || newY >= ROOM_H) {
                    p.alive = false; continue;
                }
                int[] newHead = {newX, newY};
                // 撞自己
                boolean selfHit = false;
                for (int i = 0; i < p.snake.size() - 1; i++) {
                    if (p.snake.get(i)[0] == newX && p.snake.get(i)[1] == newY) {
                        selfHit = true; break;
                    }
                }
                if (selfHit) { p.alive = false; continue; }
                // 撞其他玩家蛇身
                for (Map.Entry<Object, Player> o : players.entrySet()) {
                    if (o.getKey() == ws) continue;
                    for (int[] s : o.getValue().snake) {
                        if (s[0] == newX && s[1] == newY) {
                            p.alive = false; break;
                        }
                    }
                    if (!p.alive) break;
                }
                if (!p.alive) continue;
                p.snake.add(0, newHead);
                // 吃食物
                boolean ate = false;
                synchronized (foods) {
                    for (int i = 0; i < foods.size(); i++) {
                        int[] f = foods.get(i);
                        if (f[0] == newX && f[1] == newY) {
                            foods.remove(i); ate = true; break;
                        }
                    }
                }
                if (ate) { p.score += 10; refillFood(); }
                else { p.snake.remove(p.snake.size() - 1); }
            }
        }

        /** 生成下发到客户端的状态快照。 */
        public Map<String, Object> snapshot() {
            Map<String, Object> snap = new HashMap<>();
            snap.put("type", "state");
            snap.put("room", id);
            snap.put("w", ROOM_W);
            snap.put("h", ROOM_H);
            List<int[]> foodList;
            synchronized (foods) { foodList = new ArrayList<>(foods); }
            snap.put("foods", foodList);
            List<Map<String, Object>> ps = new ArrayList<>();
            for (Player p : players.values()) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", p.name);
                m.put("color", p.color);
                m.put("score", p.score);
                m.put("alive", p.alive);
                List<int[]> snakeCopy = new ArrayList<>(p.snake);
                m.put("snake", snakeCopy);
                ps.add(m);
            }
            snap.put("players", ps);
            snap.put("ts", System.currentTimeMillis());
            return snap;
        }
    }

    /** 单个玩家状态。 */
    public static final class Player {
        public String name;
        public List<int[]> snake = new ArrayList<>();
        public int dx = 1, dy = 0;
        public int nextDx = 1, nextDy = 0;
        public String color;
        public int score;
        public boolean alive = true;
        public long lastInputTs;

        public Player(String name, int spawnX, int spawnY, String color) {
            this.name = (name == null || name.isEmpty()) ? "玩家" + (SnakeServer.ROOMS.size() + 1) : name;
            this.color = color;
            this.snake.add(new int[]{spawnX, spawnY});
            this.snake.add(new int[]{spawnX - 1, spawnY});
            this.snake.add(new int[]{spawnX - 2, spawnY});
            this.lastInputTs = System.currentTimeMillis();
        }
    }

    // ---------------- 房间管理 ----------------
    public static Room getOrCreateRoom(String roomId) {
        return ROOMS.computeIfAbsent(roomId, Room::new);
    }

    private static void garbageCollectRooms() {
        long now = System.currentTimeMillis();
        for (String rid : new ArrayList<>(ROOMS.keySet())) {
            Room r = ROOMS.get(rid);
            if (r.players.isEmpty() && now - r.createdAt > 30_000) {
                ROOMS.remove(rid);
            }
        }
    }

    // ---------------- 消息协议 ----------------
    // 客户端 -> 服务端:
    //   {"type": "join",   "room": "default", "name": "Alice"}
    //   {"type": "input",  "dir": [dx, dy]}
    //   {"type": "leave"}
    // 服务端 -> 客户端:
    //   {"type": "welcome", "room": "default"}
    //   {"type": "state",   ...}
    //   {"type": "gameover", "winner": "..."}

    public static Map<String, Object> handleMessage(Object ws, Map<String, Object> msg) {
        if (msg == null) return error("invalid json");
        String[] entry = WS_INDEX.get(ws);
        if (entry == null) {
            if (!"join".equals(msg.get("type"))) return error("must join first");
            String roomId = String.valueOf(msg.getOrDefault("room", "default"));
            if (roomId.length() > 32) roomId = roomId.substring(0, 32);
            if (roomId.isEmpty()) roomId = "default";
            String name = String.valueOf(msg.getOrDefault("name", ""));
            if (name.length() > 16) name = name.substring(0, 16);
            Room room = getOrCreateRoom(roomId);
            room.addPlayer(ws, name);
            WS_INDEX.put(ws, new String[]{roomId, name});
            Map<String, Object> welcome = new HashMap<>();
            welcome.put("type", "welcome");
            welcome.put("room", roomId);
            welcome.put("name", name);
            return welcome;
        }
        String roomId = entry[0];
        Room room = ROOMS.get(roomId);
        if (room == null) return null;
        Player p = room.players.get(ws);
        if (p == null) return null;
        String type = String.valueOf(msg.get("type"));
        if ("input".equals(type)) {
            Object dObj = msg.get("dir");
            if (dObj instanceof List<?> d && d.size() == 2) {
                int dx = ((Number) d.get(0)).intValue();
                int dy = ((Number) d.get(1)).intValue();
                boolean validDir = (dx == 1 && dy == 0) || (dx == -1 && dy == 0)
                    || (dx == 0 && dy == 1) || (dx == 0 && dy == -1);
                if (validDir && (dx + p.dx != 0 || dy + p.dy != 0)) {
                    p.nextDx = dx; p.nextDy = dy;
                    p.lastInputTs = System.currentTimeMillis();
                }
            }
        } else if ("leave".equals(type)) {
            room.removePlayer(ws);
            WS_INDEX.remove(ws);
        }
        return null;
    }

    public static void onDisconnect(Object ws) {
        String[] entry = WS_INDEX.remove(ws);
        if (entry != null) {
            Room room = ROOMS.get(entry[0]);
            if (room != null) room.removePlayer(ws);
        }
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "error"); m.put("msg", msg); return m;
    }

    // ---------------- 主循环 ----------------
    private static ScheduledExecutorService LOOP;
    private static boolean LOOP_STARTED = false;

    public static void startLoop() {
        if (LOOP_STARTED) return;
        LOOP_STARTED = true;
        LOOP = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snake-game-loop");
            t.setDaemon(true);
            return t;
        });
        LOOP.scheduleAtFixedRate(() -> {
            try { gameLoop(); } catch (Throwable ignored) {}
        }, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    public static void stopLoop() {
        if (LOOP != null) LOOP.shutdownNow();
    }

    private static void gameLoop() {
        garbageCollectRooms();
        for (Room room : ROOMS.values()) {
            if (room.players.isEmpty()) continue;
            room.tick();
            Map<String, Object> snap = room.snapshot();
            for (Object ws : room.players.keySet()) {
                send(ws, snap);
            }
            // 全员死亡：选出 winner
            if (!room.players.isEmpty() && room.players.values().stream().allMatch(p -> !p.alive)) {
                Player winner = room.players.values().stream()
                    .max((a, b) -> Integer.compare(a.score, b.score)).orElse(null);
                Map<String, Object> over = new HashMap<>();
                over.put("type", "gameover");
                over.put("winner", winner != null ? winner.name : "");
                for (Object ws : room.players.keySet()) send(ws, over);
                // 重置玩家位置
                for (Player p : room.players.values()) {
                    p.alive = true;
                    int spawnX = 5 + RAND.nextInt(ROOM_W - 10);
                    int spawnY = 5 + RAND.nextInt(ROOM_H - 10);
                    p.snake.clear();
                    p.snake.add(new int[]{spawnX, spawnY});
                    p.snake.add(new int[]{spawnX - 1, spawnY});
                    p.snake.add(new int[]{spawnX - 2, spawnY});
                    p.dx = 1; p.dy = 0;
                    p.nextDx = 1; p.nextDy = 0;
                    p.score = 0;
                }
                room.refillFood();
            }
        }
    }

    /** 发送 JSON 消息给 ws（真实实现需要 JSON 序列化 + WebSocket 写入）。 */
    private static void send(Object ws, Map<String, Object> data) {
        // 占位：真实 Spring Boot 版本在 SnakeWebSocketHandler 里发送
    }

    // ---------------- 独立启动 ----------------
    public static void runStandalone(String host, int port) {
        // 占位：真实 Spring Boot 版本由 SnakeWebSocketConfig 注册 /snake_ws
        System.out.println("[SnakeServer] 独立模式启动 -> ws://" + host + ":" + port + "/");
        startLoop();
    }

    public static void main(String[] args) {
        runStandalone("0.0.0.0", 8080);
    }
}
