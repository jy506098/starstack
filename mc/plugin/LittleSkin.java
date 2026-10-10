/*
 * LittleSkin — Yggdrasil provider switcher + per-player inventory persistence.
 *
 * Authentication is handled by the server JVM's authlib-injector
 * (started with `-javaagent:authlib-injector.jar=<providerBaseUrl>`).
 * The Mojang authlib implementation inside the server is replaced at
 * boot, so any player that connects with a Yggdrasil-verifiable token
 * (LittleSkin, TCSkins, Mojang, etc.) is allowed in.
 *
 * This module:
 *   1. Tracks which Yggdrasil provider authlib-injector is currently
 *      configured for, and lets admins swap it at runtime via
 *      `/ls provider <name>` (the change is informational — the actual
 *      authlib-injector URL is set at JVM start, so the operator must
 *      restart the server with a new `-javaagent:…=…` for it to take
 *      effect. We log this on every switch).
 *   2. Persists each authenticated player's inventory under their
 *      Minecraft name (so swapping providers, which changes the
 *      underlying UUID, does not wipe items). The data is keyed by the
 *      player's *display name* (Player#getName), not the offline UUID,
 *      because:
 *        - "one character one player" semantics: each MC name is bound
 *          to a single Yggdrasil character.
 *        - The Yggdrasil character UUID may differ across providers;
 *          using the MC name gives a stable key.
 *   3. Logs a startup warning if Paper's vanilla NBT writer is still
 *      enabled (would double-save and conflict with our snapshots).
 *
 * File layout under plugins/StarStackmc/:
 *   - littleskin-config.yml : provider / baseUrl / per-provider URL table
 *   - littleskin-bindings.dat :  displayName|lsUuid|provider|lastLogin
 *   - lsInventory.dat         :  displayName|<gson-snapshot>
 *   - littleskin-tokens.dat   :  (kept for future refresh logic; unused)
 *
 * Requires spigot.yml `players.disable-saving: true`.
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LittleSkin implements Listener {

    private final StarStack plugin;
    private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private static final String DEFAULT_PROVIDER = "littleskin";
    private static final String DEFAULT_BASE_URL = "https://littleskin.cn/api/yggdrasil";
    /** TCSkins preconfigured. Note: as of 2026-10-10 the tcscraft.com apex
     *  is on Tencent EdgeOne and returns 567 WAF blocks from many ISPs.
     *  Operators should verify reachability before switching. */
    private static final String TCSKINS_BASE_URL = "https://tcsskins.tcscraft.com/api/yggdrasil";
    private static final String MOJANG_BASE_URL   = "https://sessionserver.mojang.com/session/minecraft";

    private volatile String providerName = DEFAULT_PROVIDER;
    /** Informational only — authlib-injector reads its URL from the JVM
     *  -javaagent flag, not from here. The log line is to remind the
     *  operator which provider the next restart will use. */
    private volatile String baseUrl = DEFAULT_BASE_URL;

    private File configFile;
    private YamlConfiguration cfg;
    private File bindingsFile;
    private File inventoryFile;
    private File tokensFile;

    // name -> lsUuid (for reference; not used for storage)
    private final Map<String, String> bindings = new ConcurrentHashMap<>();
    // name -> inventory snapshot (canonical storage)
    private final Map<String, InventorySnapshot> inventoryByName = new ConcurrentHashMap<>();

    public LittleSkin(StarStack plugin) { this.plugin = plugin; }

    public void register() {
        File df = plugin.getDataFolder();
        df.mkdirs();
        configFile    = new File(df, "littleskin-config.yml");
        bindingsFile  = new File(df, "littleskin-bindings.dat");
        inventoryFile = new File(df, "lsInventory.dat");
        tokensFile    = new File(df, "littleskin-tokens.dat");
        loadConfig();
        loadBindings();
        loadInventory();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTask(plugin, this::restoreOnlinePlayers);
        Bukkit.getScheduler().runTask(plugin, this::warnIfPaperSaveEnabled);

        plugin.getLogger().info("[LittleSkin] registered (provider=" + providerName
                + ", base=" + baseUrl
                + ", bindings=" + bindings.size()
                + ", inventory=" + inventoryByName.size() + ")");
    }

    public void save() {
        saveBindings();
        saveInventory();
    }

    public String getProviderName() { return providerName; }
    public String getBaseUrl()      { return baseUrl; }

    /** authlib-injector has already authenticated the player at the wire
     *  level by the time PlayerJoinEvent fires, so every Player that
     *  reaches this plugin is "authenticated" from our perspective.
     *  The legacy auth-timeout scheduler in StarStack can therefore
     *  safely treat every joined player as authenticated and skip the
     *  60-second kick / 5-fail ban path. */
    public boolean isAuthenticated(Player p) {
        return p != null && p.isOnline();
    }

    /** Switch the active Yggdrasil provider. NOTE: this only changes
     *  the displayed config. The real authlib-injector URL is fixed at
     *  JVM start via the -javaagent flag; a server restart with the
     *  matching URL is required for the switch to take effect on the
     *  wire. We log the mismatch so it's obvious. */
    public void setProvider(String name, String baseUrlOverride) {
        if (name == null || name.isBlank()) {
            providerName = cfg.getString("provider", DEFAULT_PROVIDER);
            baseUrl      = cfg.getString("baseUrl",  DEFAULT_BASE_URL);
        } else {
            providerName = name;
            if (baseUrlOverride != null && !baseUrlOverride.isBlank()) {
                baseUrl = baseUrlOverride;
            } else {
                String nested = cfg.getString("providers." + name + ".url");
                if (nested != null && !nested.isBlank()) {
                    baseUrl = nested;
                } else {
                    plugin.getLogger().warning("[LittleSkin] provider '" + name
                            + "' not in littleskin-config.yml; keep current base=" + baseUrl);
                    return;
                }
            }
        }
        cfg.set("provider", providerName);
        cfg.set("baseUrl", baseUrl);
        try { cfg.save(configFile); } catch (Exception ignored) {}
        plugin.getLogger().info("[LittleSkin] provider -> " + providerName
                + " base=" + baseUrl
                + " (restart server with -javaagent:authlib-injector.jar=" + baseUrl
                + " for wire-side switch)");
    }

    // ─── no-op command handlers kept for command-route compatibility ──────
    // The real auth happens via authlib-injector before PlayerJoinEvent,
    // so /login, /register, /logout are no-ops with friendly messages.

    public boolean handleRegister(CommandSender sender, String[] args) {
        sender.sendMessage("§7本服使用 §bauthlib-injector §7外置登录");
        sender.sendMessage("§7请在启动器（HMCL / PCL2）里添加 LittleSkin 或 TCSkins 验证后再连服");
        return true;
    }

    public boolean handleLogin(CommandSender sender, String[] args) {
        sender.sendMessage("§7本服使用 §bauthlib-injector §7外置登录, 启动器里登录即可, 无需在游戏内 /login");
        sender.sendMessage("§7当前 Yggdrasil 提供方: §b" + providerName
                + " §7(§f" + baseUrl + "§7)");
        return true;
    }

    public boolean handleLogout(CommandSender sender, String[] args) {
        sender.sendMessage("§7外置登录下 /logout 无效，直接退出游戏即可。物品已自动保存。");
        return true;
    }

    public boolean handleChangePassword(CommandSender sender, String[] args) {
        sender.sendMessage("§7请在 §bhttps://littlesk.in §7或对应皮肤站修改密码");
        return true;
    }

    // ─── listeners ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        snapshotAndSave(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        snapshotAndSave(e.getPlayer());
    }

    private void restoreOnlinePlayers() {
        // Players who were online before the plugin (re)load.
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventorySnapshot snap = inventoryByName.get(p.getName());
            if (snap != null) {
                applySnapshot(p, snap);
                plugin.getLogger().info("[LittleSkin] restored inventory for " + p.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        // Authlib-injector has already authenticated the player at this
        // point (the wire-level Mojang session check). If the player has
        // a saved inventory, apply it after a short delay so we don't
        // race with Paper's own state load.
        final Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            InventorySnapshot snap = inventoryByName.get(p.getName());
            if (snap != null) {
                applySnapshot(p, snap);
                p.sendMessage("§a✦ 已恢复你的存档 §7(共 "
                        + countItems(snap) + " 件物品)");
            } else {
                bindings.put(p.getName(),
                        p.getUniqueId() + "|" + System.currentTimeMillis() + "|" + providerName);
                saveBindings();
            }
        }, 5L);
    }

    private void snapshotAndSave(Player p) {
        if (p == null) return;
        String name = p.getName();
        if (name == null || name.isEmpty()) return;
        inventoryByName.put(name, captureSnapshot(p));
        save();
    }

    // ─── inventory snapshot / restore ────────────────────────────────────

    private InventorySnapshot captureSnapshot(Player p) {
        InventorySnapshot s = new InventorySnapshot();
        PlayerInventory inv = p.getInventory();
        s.main  = inv.getContents();
        s.armor = inv.getArmorContents();
        s.extra = inv.getExtraContents();
        s.ender = p.getEnderChest().getContents();
        s.xpLevel    = p.getLevel();
        s.xpProgress = p.getExp();
        try { s.gameMode = p.getGameMode().name(); } catch (Exception e) { s.gameMode = "SURVIVAL"; }
        return s;
    }

    private void applySnapshot(Player p, InventorySnapshot s) {
        if (s == null) return;
        try {
            if (s.main  != null) p.getInventory().setContents(s.main);
            if (s.armor != null) p.getInventory().setArmorContents(s.armor);
            if (s.extra != null) p.getInventory().setExtraContents(s.extra);
            if (s.ender != null) p.getEnderChest().setContents(s.ender);
            p.setLevel(s.xpLevel);
            p.setExp(s.xpProgress);
        } catch (Exception e) {
            plugin.getLogger().warning("[LittleSkin] applySnapshot failed for "
                    + p.getName() + ": " + e.getMessage());
        }
    }

    private static int countItems(InventorySnapshot s) {
        int c = 0;
        if (s.main != null) for (ItemStack i : s.main) if (i != null) c++;
        if (s.armor != null) for (ItemStack i : s.armor) if (i != null) c++;
        if (s.ender != null) for (ItemStack i : s.ender) if (i != null) c++;
        return c;
    }

    // ─── persistence ──────────────────────────────────────────────────────

    private void loadConfig() {
        cfg = YamlConfiguration.loadConfiguration(configFile);
        boolean missing = !configFile.exists();
        if (missing) {
            cfg.set("provider", DEFAULT_PROVIDER);
            cfg.set("baseUrl",  DEFAULT_BASE_URL);
            cfg.set("providers.littleskin.url",  DEFAULT_BASE_URL);
            cfg.set("providers.tcsskins.url",    TCSKINS_BASE_URL);
            cfg.set("providers.mojang.url",      MOJANG_BASE_URL);
            cfg.set("comment", "Restart the server with -javaagent:authlib-injector.jar=" +
                    "{baseUrl} to actually switch the wire-side auth provider");
            try { cfg.save(configFile); } catch (Exception ignored) {}
            plugin.getLogger().info("[LittleSkin] wrote " + configFile);
        }
        String p = cfg.getString("provider", DEFAULT_PROVIDER);
        String u = cfg.getString("baseUrl",  DEFAULT_BASE_URL);
        if (p != null && !p.isBlank()) providerName = p;
        if (u != null && !u.isBlank()) baseUrl = u;
    }

    private void loadBindings() {
        bindings.clear();
        if (bindingsFile == null || !bindingsFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(bindingsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sep = line.indexOf('|');
                if (sep < 0) continue;
                bindings.put(line.substring(0, sep), line.substring(sep + 1));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LittleSkin] failed to load bindings: " + e.getMessage());
        }
    }

    private void saveBindings() {
        if (bindingsFile == null) return;
        File tmp = new File(bindingsFile.getParentFile(), bindingsFile.getName() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write("# displayName|uuid|provider|lastLoginMs");
            w.newLine();
            for (Map.Entry<String, String> e : bindings.entrySet()) {
                w.write(e.getKey() + "|" + e.getValue());
                w.newLine();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LittleSkin] saveBindings failed: " + e.getMessage());
            return;
        }
        if (bindingsFile.exists() && !bindingsFile.delete()) return;
        if (!tmp.renameTo(bindingsFile)) {
            plugin.getLogger().warning("[LittleSkin] could not rename bindings tmp");
        }
    }

    private void loadInventory() {
        inventoryByName.clear();
        if (inventoryFile == null || !inventoryFile.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(inventoryFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sep = line.indexOf('|');
                if (sep < 0) continue;
                try {
                    String name = line.substring(0, sep);
                    String json = line.substring(sep + 1);
                    InventorySnapshot s = gson.fromJson(json, InventorySnapshot.class);
                    if (s != null) inventoryByName.put(name, s);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LittleSkin] failed to load inventory: " + e.getMessage());
        }
    }

    private void saveInventory() {
        if (inventoryFile == null) return;
        File tmp = new File(inventoryFile.getParentFile(), inventoryFile.getName() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write("# displayName|<gson-snapshot>");
            w.newLine();
            for (Map.Entry<String, InventorySnapshot> e : inventoryByName.entrySet()) {
                w.write(e.getKey() + "|" + gson.toJson(e.getValue()));
                w.newLine();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LittleSkin] saveInventory failed: " + e.getMessage());
            return;
        }
        if (inventoryFile.exists() && !inventoryFile.delete()) return;
        if (!tmp.renameTo(inventoryFile)) {
            plugin.getLogger().warning("[LittleSkin] could not rename inventory tmp");
        }
    }

    private void warnIfPaperSaveEnabled() {
        try {
            boolean saved = org.bukkit.Bukkit.spigot().getConfig().getBoolean("players.disable-saving", false);
            if (!saved) {
                plugin.getLogger().warning("[LittleSkin] spigot.yml `players.disable-saving` is NOT true");
                plugin.getLogger().warning("[LittleSkin] Paper will write offline-UUID .dat in addition to our snapshots.");
            }
        } catch (Exception e) {
            plugin.getLogger().info("[LittleSkin] could not read spigot config: " + e.getMessage());
        }
    }

    // ─── nested types ─────────────────────────────────────────────────────

    static class InventorySnapshot {
        ItemStack[] main;
        ItemStack[] armor;
        ItemStack[] extra;
        ItemStack[] ender;
        int xpLevel;
        float xpProgress;
        String gameMode;
    }
}
