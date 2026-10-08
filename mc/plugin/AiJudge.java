import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AI-judge moderation layer for StarStackmc.
 *
 * Watches chat + abnormal movement, calls Claude API for verdict, applies rules:
 *   - cheat suspicion           → 30-day ban
 *   - recruitment to other MC   → 7/15/30-day ban
 *   - 10 kicks accumulated      → 1-hour ban
 *   - 5 bans accumulated        → permanent ban
 *
 * Config:  plugins/StarStackmc/ai-config.yml
 * History: plugins/StarStackmc/ai-history.dat  (UUID-keyed, persistent)
 */
public class AiJudge implements Listener {

    private final StarStack plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Gson gson = new Gson();

    private File configFile;
    private File historyFile;

    // --- Config (loaded from ai-config.yml) ---
    private String apiKey = "";
    private String model = "claude-haiku-4-5";
    private String endpoint = "https://api.anthropic.com/v1/messages";
    private boolean enabled = false;
    private int moveDistanceThreshold = 4;
    private double yJumpThreshold = 1.5;
    private int chatFloodThreshold = 5;       // messages within chatFloodWindowSecs
    private int chatFloodWindowSecs = 60;
    private long apiTimeoutMs = 8000;
    private double cheatConfidenceThreshold = 0.7;
    private double recruitmentConfidenceThreshold = 0.6;

    // --- Per-player in-memory ring buffers ---
    private final Map<UUID, Deque<String>> recentActions = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<String>> recentChats = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> recentChatCount = new ConcurrentHashMap<>();  // rolling 60s window
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private static final int ACTION_WINDOW = 30;
    private static final int CHAT_WINDOW = 10;

    // --- Persistent per-player history ---
    private final Map<String, History> history = new ConcurrentHashMap<>();  // key = lowercase name

    static class History {
        int kicks = 0;
        int bans = 0;
        boolean permaban = false;
    }

    static class Verdict {
        boolean cheatSuspicion;
        int recruitmentSeverity;   // 0 / 7 / 15 / 30
        double confidence;
        String reason = "";
    }

    public AiJudge(StarStack plugin) {
        this.plugin = plugin;
    }

    // ───────────────── register / save ─────────────────

    public void register() {
        configFile = new File(plugin.getDataFolder(), "ai-config.yml");
        historyFile = new File(plugin.getDataFolder(), "ai-history.dat");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        loadConfig();
        loadHistory();

        if (enabled) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            // Periodic flood scanner: every 10 s, prune stale chat counters
            Bukkit.getScheduler().runTaskTimer(plugin, this::pruneChatCounters, 200L, 200L);
            plugin.getLogger().info("AiJudge enabled (model=" + model + ")");
        } else {
            plugin.getLogger().warning("AiJudge disabled: set apiKey in plugins/StarStackmc/ai-config.yml to enable");
        }
    }

    public void save() {
        saveHistory();
    }

    // ───────────────── config ─────────────────

    private void loadConfig() {
        if (!configFile.exists()) {
            writeDefaultConfig();
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        apiKey = cfg.getString("apiKey", "");
        endpoint = cfg.getString("endpoint", endpoint);
        model = cfg.getString("model", model);
        moveDistanceThreshold = cfg.getInt("move-distance-threshold", 4);
        yJumpThreshold = cfg.getDouble("y-jump-threshold", 1.5);
        chatFloodThreshold = cfg.getInt("chat-message-threshold", 5);
        chatFloodWindowSecs = cfg.getInt("chat-window-seconds", 60);
        apiTimeoutMs = cfg.getLong("api-timeout-ms", 8000L);
        cheatConfidenceThreshold = cfg.getDouble("cheat-confidence-threshold", 0.7);
        recruitmentConfidenceThreshold = cfg.getDouble("recruitment-confidence-threshold", 0.6);
        enabled = !apiKey.trim().isEmpty();
    }

    private void writeDefaultConfig() {
        try {
            configFile.createNewFile();
            FileConfiguration cfg = new YamlConfiguration();
            cfg.set("# StarStackmc AiJudge configuration", null);
            cfg.set("apiKey", "");
            cfg.set("endpoint", "https://api.anthropic.com/v1/messages");
            cfg.set("model", "claude-haiku-4-5");
            cfg.set("move-distance-threshold", 4);
            cfg.set("y-jump-threshold", 1.5);
            cfg.set("chat-message-threshold", 5);
            cfg.set("chat-window-seconds", 60);
            cfg.set("api-timeout-ms", 8000);
            cfg.set("cheat-confidence-threshold", 0.7);
            cfg.set("recruitment-confidence-threshold", 0.6);
            cfg.save(configFile);
            plugin.getLogger().info("Generated default ai-config.yml at " + configFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to write ai-config.yml: " + e.getMessage());
        }
    }

    // ───────────────── history persistence ─────────────────

    private void loadHistory() {
        if (!historyFile.exists()) return;
        int loaded = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                try {
                    History h = new History();
                    h.kicks = Integer.parseInt(parts[1]);
                    h.bans = Integer.parseInt(parts[2]);
                    h.permaban = Boolean.parseBoolean(parts[3]);
                    history.put(parts[0].toLowerCase(), h);
                    loaded++;
                } catch (Exception ignore) { /* skip */ }
            }
            plugin.getLogger().info("Loaded " + loaded + " AiJudge history entries");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load ai-history.dat: " + e.getMessage());
        }
    }

    private void saveHistory() {
        if (historyFile == null) return;
        File tmp = new File(historyFile.getParentFile(), "ai-history.dat.tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write("# AiJudge history: <lowercase-name> <kicks> <bans> <permaban>");
            w.newLine();
            for (Map.Entry<String, History> e : history.entrySet()) {
                History h = e.getValue();
                w.write(e.getKey() + " " + h.kicks + " " + h.bans + " " + h.permaban);
                w.newLine();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save ai-history.dat: " + e.getMessage());
            return;
        }
        if (historyFile.exists() && !historyFile.delete()) {
            plugin.getLogger().warning("Could not remove old ai-history.dat");
            return;
        }
        if (!tmp.renameTo(historyFile)) {
            plugin.getLogger().warning("Could not rename ai-history.dat.tmp");
        }
    }

    // ───────────────── event handlers ─────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;
        Player p = event.getPlayer();
        if (plugin.getAuthMe() != null && plugin.getAuthMe().isAuthenticated(p)) {
            // Only judge authenticated players
        } else {
            return; // skip unauthenticated
        }
        String msg = event.getMessage();
        String name = p.getName();

        // Record chat in ring buffer
        Deque<String> chats = recentChats.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (chats) {
            if (chats.size() >= CHAT_WINDOW) chats.pollFirst();
            chats.offerLast("[" + System.currentTimeMillis() + "] " + name + ": " + msg);
        }

        // Rolling flood counter
        long now = System.currentTimeMillis();
        lastChatTime.put(p.getUniqueId(), now);
        int count = recentChatCount.merge(p.getUniqueId(), 1, Integer::sum);

        // Trigger if chat looks like recruitment OR player is flooding
        boolean looksLikeRecruitment = containsRecruitmentSignal(msg);
        boolean isFlooding = count >= chatFloodThreshold;

        if (looksLikeRecruitment || isFlooding) {
            String trigger = looksLikeRecruitment ? "recruitment-pattern" : "chat-flood";
            recordAction(p.getUniqueId(), "chat:" + trigger + " count=" + count);
            requestJudgment(p, trigger);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player p = event.getPlayer();
        if (plugin.getAuthMe() != null && !plugin.getAuthMe().isAuthenticated(p)) return;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        boolean teleportLike = horizDist > moveDistanceThreshold;
        boolean flightLike = dy > yJumpThreshold && p.getGameMode() != org.bukkit.GameMode.CREATIVE
                && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;

        if (teleportLike || flightLike) {
            String detail = String.format("move: from=(%.1f,%.1f,%.1f) to=(%.1f,%.1f,%.1f) Δh=%.1f Δy=%.2f",
                    event.getFrom().getX(), event.getFrom().getY(), event.getFrom().getZ(),
                    event.getTo().getX(), event.getTo().getY(), event.getTo().getZ(),
                    horizDist, dy);
            recordAction(p.getUniqueId(), detail);
            requestJudgment(p, teleportLike ? "teleport-pattern" : "flight-pattern");
        }
    }

    private void recordAction(UUID id, String action) {
        Deque<String> buf = recentActions.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (buf) {
            if (buf.size() >= ACTION_WINDOW) buf.pollFirst();
            buf.offerLast("[" + System.currentTimeMillis() + "] " + action);
        }
    }

    private void pruneChatCounters() {
        long cutoff = System.currentTimeMillis() - chatFloodWindowSecs * 1000L;
        Iterator<Map.Entry<UUID, Long>> it = lastChatTime.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() < cutoff) {
                recentChatCount.remove(e.getKey());
                it.remove();
            }
        }
    }

    private static boolean containsRecruitmentSignal(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("http://") || lower.contains("https://") || lower.contains("://")
                || lower.contains("加群") || lower.contains("加好友")
                || lower.contains("来玩") || lower.contains("服务器") || lower.contains("私服")
                || lower.contains("ip ") || lower.contains("ip:")
                || msg.matches(".*\\b\\d{1,3}(\\.\\d{1,3}){3}\\b.*");   // IP literal
    }

    // ───────────────── judgment ─────────────────

    private void requestJudgment(Player p, String trigger) {
        if (!enabled || apiKey.isEmpty()) return;

        UUID id = p.getUniqueId();
        // Cooldown: skip if same player judged within last 30 s
        Long lastAt = judgmentCooldown.get(id);
        long now = System.currentTimeMillis();
        if (lastAt != null && now - lastAt < 30_000L) return;
        judgmentCooldown.put(id, now);

        String name = p.getName();
        String actions;
        String chats;
        Deque<String> a = recentActions.get(id);
        Deque<String> c = recentChats.get(id);
        synchronized (a == null ? new ArrayDeque<>() : a) {
            actions = (a == null || a.isEmpty()) ? "(none)" : String.join("\n", a);
        }
        synchronized (c == null ? new ArrayDeque<>() : c) {
            chats = (c == null || c.isEmpty()) ? "(none)" : String.join("\n", c);
        }

        String userContent = "玩家 " + name + " 最近 30 秒的服务器事件：\n\n"
                + "[动作]\n" + actions + "\n\n"
                + "[聊天]\n" + chats + "\n\n"
                + "触发原因: " + trigger + "\n\n"
                + "判定 JSON（仅返回 JSON 本身，不要任何其他文字或 ``` 围栏）:\n"
                + "{\"cheat\":bool,\"recruitment_severity\":0|7|15|30,\"confidence\":0..1,\"reason\":\"一句话中文原因\"}";

        String systemPrompt = "你是 Minecraft 服务器 AI 仲裁官。判定玩家是否违规：\n"
                + "- cheat=true 表示开挂嫌疑（飞行、瞬移、连发等）\n"
                + "- recruitment_severity 0/7/15/30 表示拉人到别的服务器的天数（0=无嫌疑）\n"
                + "- confidence 0..1 表示判定确信度\n"
                + "- reason 一句话中文\n"
                + "仅返回 JSON，不要任何其他文字。";

        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 256);
            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userContent);
            messages.add(msg);
            body.add("messages", messages);

            JsonObject sys = new JsonObject();
            sys.addProperty("type", "text");
            sys.addProperty("text", systemPrompt);
            body.add("system", sys);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(apiTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return http.send(req, HttpResponse.BodyHandlers.ofString());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .orTimeout(apiTimeoutMs + 1000, TimeUnit.MILLISECONDS)
                    .thenAccept(resp -> {
                        if (resp == null) return;
                        try {
                            if (resp.statusCode() != 200) {
                                plugin.getLogger().warning("AiJudge: Claude API status " + resp.statusCode());
                                return;
                            }
                            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                            JsonArray content = json.has("content") ? json.getAsJsonArray("content") : null;
                            if (content == null || content.isEmpty()) return;
                            String text = null;
                            for (JsonElement el : content) {
                                if (el.isJsonObject() && el.getAsJsonObject().has("text")) {
                                    text = el.getAsJsonObject().get("text").getAsString();
                                    break;
                                }
                            }
                            if (text == null) return;
                            Verdict v = parseVerdict(text);
                            if (v == null) return;
                            // Dispatch back to main thread
                            Bukkit.getScheduler().runTask(plugin, () -> applyVerdict(p, v));
                        } catch (Exception e) {
                            plugin.getLogger().warning("AiJudge: failed to parse response: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("AiJudge: API call failed: " + e.getMessage());
        }
    }

    private final Map<UUID, Long> judgmentCooldown = new ConcurrentHashMap<>();

    /** Extract the JSON object from Claude's response (may have ```json``` fences). */
    private Verdict parseVerdict(String text) {
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            String jsonStr = text.substring(start, end + 1);
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            Verdict v = new Verdict();
            v.cheatSuspicion = obj.has("cheat") && obj.get("cheat").getAsBoolean();
            v.recruitmentSeverity = obj.has("recruitment_severity")
                    ? obj.get("recruitment_severity").getAsInt() : 0;
            v.confidence = obj.has("confidence") ? obj.get("confidence").getAsDouble() : 0.0;
            v.reason = obj.has("reason") ? obj.get("reason").getAsString() : "";
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    // ───────────────── rule application ─────────────────

    private void applyVerdict(Player p, Verdict v) {
        if (p == null || !p.isOnline()) return;
        History h = history.computeIfAbsent(p.getName().toLowerCase(), k -> new History());

        // Rule 1: 开挂嫌疑 → 30 天封禁
        if (v.cheatSuspicion && v.confidence >= cheatConfidenceThreshold) {
            long ms = 30L * 24L * 60L * 60L * 1000L;
            String reason = "AI 判定开挂嫌疑: " + v.reason;
            plugin.getLogger().info("AiJudge cheat verdict for " + p.getName()
                    + " (conf=" + v.confidence + "): " + v.reason);
            banAndRecord(p, ms, reason);
            return;
        }

        // Rule 2: 拉人到别的服务器 → 7/15/30 天封禁
        if (v.recruitmentSeverity >= 7 && v.confidence >= recruitmentConfidenceThreshold) {
            int days = v.recruitmentSeverity;
            long ms = days * 24L * 60L * 60L * 1000L;
            String reason = "AI 判定拉人到别的服务器: " + v.reason;
            plugin.getLogger().info("AiJudge recruitment verdict for " + p.getName()
                    + " (sev=" + days + "d, conf=" + v.confidence + "): " + v.reason);
            banAndRecord(p, ms, reason);
            return;
        }

        // Below thresholds: do nothing (log only)
        plugin.getLogger().info("AiJudge no-action for " + p.getName()
                + " (cheat=" + v.cheatSuspicion + " rec=" + v.recruitmentSeverity
                + " conf=" + v.confidence + "): " + v.reason);
    }

    /** Ban + record ban counter + check permaban threshold. */
    private void banAndRecord(Player p, long ms, String reason) {
        banPlayer(p.getName(), ms, reason, "AiJudge");
        History h = history.computeIfAbsent(p.getName().toLowerCase(), k -> new History());
        h.bans++;
        saveHistory();
        if (h.bans >= 5 && !h.permaban) {
            h.permaban = true;
            saveHistory();
            // Upgrade to permanent: remove temporary ban, add indefinite
            Bukkit.getBanList(BanList.Type.NAME).pardon(p.getName());
            Bukkit.getBanList(BanList.Type.NAME).addBan(p.getName(),
                    "累计被封禁 5 次，永久封禁（最近原因: " + reason + "）",
                    null, "AiJudge");
            plugin.getLogger().warning("AiJudge permanently banned " + p.getName() + " (5 bans total)");
        }
    }

    /** Ban helper (NAME ban list with expiry + kick). */
    private void banPlayer(String name, long durationMs, String reason, String source) {
        Date expires = durationMs > 0 ? new Date(System.currentTimeMillis() + durationMs) : null;
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, expires, source);
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            String kickMsg;
            if (expires == null) {
                kickMsg = "§c你已被永久封禁\n§f原因: §e" + reason;
            } else {
                kickMsg = "§c你已被封禁\n§f原因: §e" + reason
                        + "\n§f到期: §e" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(expires);
            }
            p.kickPlayer(kickMsg);
        }
    }

    // ───────────────── external API for other modules ─────────────────

    /** Called by StarStack#kickPlayer to record a kick in history. */
    public void recordKick(Player p) {
        if (p == null) return;
        History h = history.computeIfAbsent(p.getName().toLowerCase(), k -> new History());
        h.kicks++;
        saveHistory();
        if (h.kicks >= 10 && !h.permaban) {
            // 10 kicks → 1-hour ban
            long ms = 60L * 60L * 1000L;
            String reason = "累计被踢出 10 次";
            banPlayer(p.getName(), ms, reason, "AiJudge");
            plugin.getLogger().info("AiJudge: " + p.getName() + " banned 1h after 10 kicks");
            // Note: do NOT increment h.bans — this is a kick-triggered ban, not a verdict ban
        }
    }
}
