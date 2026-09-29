import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

/**
 * StarStackmc main plugin (internal class name kept as StarStack for binary
 * compatibility with the 11 sub-modules in StarStack-final11.jar — their
 * constructors expect a StarStack parameter type, not StarStackmc).
 * The plugin's user-visible name (plugin.yml: name) is "StarStackmc".
 */
public class StarStack extends JavaPlugin implements Listener {
    private File dataFile;
    private List<Territory> territories = new ArrayList<>();
    private long lastReset;
    private long resetIntervalMs = 1800000L;
    private boolean restoring = false;
    private static final String[] GAMES = {"bedwars", "skywars", "pvp", "survival", "tnt", "glass"};
    private Map<String, GameWorld> gameWorlds = new HashMap<>();
    private Map<UUID, String> playerGame = new HashMap<>();
    private Map<UUID, Integer> playerCount = new HashMap<>();
    private Map<String, Map<Integer, List<UUID>>> gameQueues = new HashMap<>();
    private static final Material[] skywarsRandomBlocks = {
            Material.GRASS_BLOCK, Material.STONE, Material.DIRT,
            Material.COBBLESTONE, Material.OAK_PLANKS, Material.SAND,
            Material.GRAVEL, Material.OAK_LOG, Material.IRON_ORE,
            Material.COAL_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
            Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE,
            Material.PUMPKIN, Material.MELON, Material.BOOKSHELF,
            Material.CRAFTING_TABLE, Material.FURNACE, Material.CHEST,
            Material.TNT, Material.SPONGE, Material.HAY_BLOCK,
            Material.BONE_BLOCK, Material.SLIME_BLOCK, Material.HONEYCOMB_BLOCK
    };
    private static final EntityType[] skywarsMobs = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.PIG, EntityType.COW,
            EntityType.SHEEP, EntityType.CHICKEN, EntityType.WOLF,
            EntityType.CAT, EntityType.FOX, EntityType.RABBIT,
            EntityType.HORSE, EntityType.DONKEY, EntityType.LLAMA
    };
    private final Set<String> tntCracking = new HashSet<>();

    // Other plugin components (initialized in onEnable)
    private Essentials essentials;
    private AuthMe authMe;
    private Claims claims;
    private AntiCheat antiCheat;
    private TreeMiner treeMiner;
    private RPTMarket rptMarket;
    private Mintconomy mintconomy;
    private CoreProtect coreProtect;
    private MagisterAC magisterAC;
    private LuckPerms luckPerms;
    private RaspberryPi raspberryPi;

    public AuthMe getAuthMe() { return authMe; }
    public Claims getClaims() { return claims; }
    public AntiCheat getAntiCheat() { return antiCheat; }
    public TreeMiner getTreeMiner() { return treeMiner; }
    public Essentials getEssentials() { return essentials; }
    public RPTMarket getRPTMarket() { return rptMarket; }
    public Mintconomy getMintconomy() { return mintconomy; }
    public CoreProtect getCoreProtect() { return coreProtect; }
    public MagisterAC getMagisterAC() { return magisterAC; }
    public LuckPerms getLuckPerms() { return luckPerms; }
    public RaspberryPi getRaspberryPi() { return raspberryPi; }

    @Override
    public void onEnable() {
        getLogger().info("StarStackmc v3.0 enabled - territories + auto-reset + minigames");
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "territories.dat");
        loadTerritories();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Initialize sub-modules
        this.authMe = new AuthMe(this);
        this.claims = new Claims(this);
        this.antiCheat = new AntiCheat(this);
        this.treeMiner = new TreeMiner(this);
        treeMiner.register();
        this.essentials = new Essentials(this);
        essentials.register();
        this.rptMarket = new RPTMarket(this);
        rptMarket.register();
        this.mintconomy = new Mintconomy(this);
        mintconomy.register();
        this.coreProtect = new CoreProtect(this);
        coreProtect.register();
        this.magisterAC = new MagisterAC(this);
        magisterAC.register();
        this.luckPerms = new LuckPerms(this);
        luckPerms.register();
        this.raspberryPi = new RaspberryPi(this);
        raspberryPi.register();

        // Bootstrap the embedded SkinsRestorer (classes shaded into this jar).
        SkinsRestorerBridge.start(this);

        for (String g : GAMES) {
            ensureGameWorld(g);
            gameQueues.put(g, new HashMap<>());
        }
    }

    @Override
    public void onDisable() {
        saveTerritories();
        if (essentials != null) essentials.save();
        if (rptMarket != null) rptMarket.save();
        if (mintconomy != null) mintconomy.save();
        if (coreProtect != null) coreProtect.save();
        if (magisterAC != null) magisterAC.save();
        if (luckPerms != null) luckPerms.save();
        SkinsRestorerBridge.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerGame.containsKey(p.getUniqueId())) {
                World w = Bukkit.getWorld("world");
                if (w != null) p.teleport(new Location(w, 1000.5, 135, 1000.5));
            }
        }
    }

    void ensureGameWorld(String name) {
        String worldName = name;
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.type(WorldType.FLAT);
            wc.generator(new VoidChunkGenerator());
            wc.generateStructures(false);
            w = Bukkit.createWorld(wc);
            if (w != null) {
                w.setGameRule(GameRule.KEEP_INVENTORY, true);
                w.setGameRule(GameRule.MOB_GRIEFING, false);
                w.setTime(6000L);
                w.setStorm(false);
                w.setThundering(false);
            }
        } else {
            // World already existed from an older run; clear weather and
            // re-anchor time so the arena is visible on first load.
            w.setStorm(false);
            w.setThundering(false);
            w.setTime(6000L);
        }
        GameWorld gw = new GameWorld();
        gw.name = name;
        gw.world = w;
        gw.spawnLoc = new Location(w, 0.5, 65, 0.5);
        gw.joinLoc = new Location(w, 0.5, 66, 0.5);
        gameWorlds.put(name, gw);
        getLogger().info("Game world ready: " + worldName);
        buildArena(w, name);
    }

    /**
     * Build the static arena layout for a mini-game world.
     * Called once per world, right after world creation. Synchronous on
     * the main thread; for larger arenas we batch via the scheduler so
     * we don't freeze the server.
     */
    void buildArena(World w, String game) {
        if (w == null) return;
        switch (game) {
            case "bedwars":   buildBedwarsArena(w); break;
            case "skywars":   buildSkywarsArena(w); break;
            case "pvp":       buildPvpArena(w); break;
            case "survival":  buildSurvivalArena(w); break;
            case "tnt":       buildTntArena(w); break;
            case "glass":     buildGlassArena(w); break;
        }
    }

    // ────────── bedwars ──────────
    // 4 team spawn islands (one per cardinal direction), each 7×7 wool platform
    // with a bed. Center has diamond/emerald blocks as mid-game loot.
    void buildBedwarsArena(World w) {
        int y = 60;
        Material[] teamWool = {
                Material.WHITE_WOOL, Material.ORANGE_WOOL,
                Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL
        };
        double[] angles = {0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};
        double radius = 22.0;
        for (int i = 0; i < 4; i++) {
            double cx = Math.cos(angles[i]) * radius;
            double cz = Math.sin(angles[i]) * radius;
            int bx = (int) Math.round(cx);
            int bz = (int) Math.round(cz);
            // 7×7 wool platform (no center, so a 5×5 with a ring border)
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                    w.getBlockAt(bx + dx, y, bz + dz).setType(teamWool[i]);
                    w.getBlockAt(bx + dx, y - 1, bz + dz).setType(Material.STONE);
                }
            }
            // Bed on the inner edge (pointing toward center)
            int bdx = (int) -Math.signum(cx);
            int bdz = (int) -Math.signum(cz);
            placeBed(w, bx + bdx, y + 1, bz + bdz);
        }
        // Center 5×5 platform with diamond blocks + emerald in middle
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(dx, y, dz).setType(Material.DIAMOND_BLOCK);
                w.getBlockAt(dx, y - 1, dz).setType(Material.STONE);
            }
        }
        w.getBlockAt(0, y + 1, 0).setType(Material.EMERALD_BLOCK);
    }

    void placeBed(World w, int x, int y, int z) {
        // Paper 1.21+ uses BlockData; use the simple 2-block head/foot pair.
        w.getBlockAt(x, y, z).setType(Material.RED_BED);
        // Bed has head/foot — set the second half too if the data permits
        // For simplicity, use a single RED_BED block which auto-completes head/foot in legacy data.
    }

    // ────────── skywars ──────────
    // 16 spawn islands (4 per ring × 4 rings) + 1 center island.
    // Each island is 3×3 with a chest in the middle. Center island has 4 chests.
    void buildSkywarsArena(World w) {
        int y = 80;
        // Spawn islands: 4 rings × 4 islands = 16 islands
        for (int ring = 0; ring < 4; ring++) {
            double r = 10.0 + ring * 8.0;
            for (int i = 0; i < 4; i++) {
                double angle = (2 * Math.PI / 4) * i + (ring * Math.PI / 8);
                int cx = (int) Math.round(Math.cos(angle) * r);
                int cz = (int) Math.round(Math.sin(angle) * r);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        w.getBlockAt(cx + dx, y, cz + dz).setType(Material.GRASS_BLOCK);
                        w.getBlockAt(cx + dx, y - 1, cz + dz).setType(Material.DIRT);
                    }
                }
                w.getBlockAt(cx, y + 1, cz).setType(Material.CHEST);
            }
        }
        // Center island (5×5) with diamond blocks + 4 chests on the corners
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(dx, y, dz).setType(Material.GRASS_BLOCK);
                w.getBlockAt(dx, y - 1, dz).setType(Material.DIRT);
            }
        }
        w.getBlockAt(2, y + 1, 2).setType(Material.CHEST);
        w.getBlockAt(-2, y + 1, 2).setType(Material.CHEST);
        w.getBlockAt(2, y + 1, -2).setType(Material.CHEST);
        w.getBlockAt(-2, y + 1, -2).setType(Material.CHEST);
        w.getBlockAt(0, y + 1, 0).setType(Material.DIAMOND_BLOCK);
    }

    // ────────── pvp ──────────
    // Circular stone-brick arena with a raised center platform.
    void buildPvpArena(World w) {
        int y = 60;
        int radius = 18;
        // Floor (radius 18) and walls (3 high)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= radius) {
                    w.getBlockAt(dx, y, dz).setType(Material.STONE_BRICKS);
                    w.getBlockAt(dx, y - 1, dz).setType(Material.STONE);
                }
                if (dist >= radius - 1 && dist <= radius) {
                    for (int wallY = y + 1; wallY <= y + 3; wallY++) {
                        w.getBlockAt(dx, wallY, dz).setType(Material.STONE_BRICKS);
                    }
                }
            }
        }
        // Raised center platform (3×3, +2 blocks)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(dx, y + 2, dz).setType(Material.GOLD_BLOCK);
            }
        }
        // 4 cardinal spawn platforms
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            int sx = (int) Math.round(Math.cos(angle) * (radius - 3));
            int sz = (int) Math.round(Math.sin(angle) * (radius - 3));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    w.getBlockAt(sx + dx, y + 1, sz + dz).setType(Material.IRON_BLOCK);
                }
            }
        }
    }

    // ────────── survival ──────────
    // 30×30 grass platform with 4 trees, 4 chests, and ore veins around the edges.
    void buildSurvivalArena(World w) {
        int y = 60;
        int r = 15;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                w.getBlockAt(dx, y, dz).setType(Material.GRASS_BLOCK);
                w.getBlockAt(dx, y - 1, dz).setType(Material.DIRT);
                w.getBlockAt(dx, y - 2, dz).setType(Material.STONE);
            }
        }
        // 4 trees at the corners (oak logs 4 high + leaf canopy)
        int[] treeX = {-r + 3, r - 3, -r + 3, r - 3};
        int[] treeZ = {-r + 3, r - 3, r - 3, -r + 3};
        for (int t = 0; t < 4; t++) {
            for (int dy = 1; dy <= 4; dy++) {
                w.getBlockAt(treeX[t], y + dy, treeZ[t]).setType(Material.OAK_LOG);
            }
            for (int dy = 3; dy <= 5; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy < 5) continue;
                        if (Math.abs(dx) > 2 || Math.abs(dz) > 2) continue;
                        w.getBlockAt(treeX[t] + dx, y + dy, treeZ[t] + dz).setType(Material.OAK_LEAVES);
                    }
                }
            }
        }
        // 4 chests in the center quadrants
        int[] chestX = {-7, 7, -7, 7};
        int[] chestZ = {-7, -7, 7, 7};
        for (int i = 0; i < 4; i++) {
            w.getBlockAt(chestX[i], y + 1, chestZ[i]).setType(Material.CHEST);
        }
        // Ore ring just inside the grass edge
        for (int dx = -r + 1; dx <= r - 1; dx++) {
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                if (Math.abs(dx) == r - 1 || Math.abs(dz) == r - 1) {
                    w.getBlockAt(dx, y - 2, dz).setType(Material.COAL_ORE);
                }
            }
        }
        // Iron vein corners
        int[][] ironCorners = {{-r + 1, -r + 1}, {r - 1, -r + 1}, {-r + 1, r - 1}, {r - 1, r - 1}};
        for (int[] c : ironCorners) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    w.getBlockAt(c[0] + dx, y - 2, c[1] + dz).setType(Material.IRON_ORE);
                }
            }
        }
    }

    // ────────── tnt-run ──────────
    // 8 stacked 30×30 sand platforms. The actual TNT placement and
    // random-layer-removal happens at game start (in startMultiGame);
    // here we just lay down the per-layer floors and TNT blocks so
    // the world looks like a TNT tower when idle.
    void buildTntArena(World w) {
        int baseY = 60;
        int r = 14;
        int layers = 8;
        int layerHeight = 2; // TNT height between sand layers
        for (int layer = 0; layer < layers; layer++) {
            int y = baseY + layer * (layerHeight + 1);
            // Sand floor
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    w.getBlockAt(dx, y, dz).setType(Material.SAND);
                }
            }
            // TNT layer above the sand
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    w.getBlockAt(dx, y + 1, dz).setType(Material.TNT);
                }
            }
        }
        // Top glass platform (safe space on top)
        int topY = baseY + layers * (layerHeight + 1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                w.getBlockAt(dx, topY, dz).setType(Material.GLASS);
                w.getBlockAt(dx, topY - 1, dz).setType(Material.SAND);
            }
        }
    }

    // ────────── glass (跳玻璃) ──────────
    // Stacked colored wool layers; players must keep jumping because
    // walked-on blocks fade out after a short delay. Each layer is
    // a different color for visual separation. Top layer is a safe
    // gold platform where the survivor wins.
    void buildGlassArena(World w) {
        int baseY = 60;
        int r = 14;
        int layers = 9;
        int layerHeight = 3;
        Material[] palette = {
                Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL,
                Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL, Material.PURPLE_WOOL,
                Material.MAGENTA_WOOL, Material.PINK_WOOL, Material.WHITE_WOOL
        };
        for (int layer = 0; layer < layers; layer++) {
            int y = baseY + layer * layerHeight;
            Material wool = palette[layer % palette.length];
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    w.getBlockAt(dx, y, dz).setType(wool);
                    // Underlay so blocks have something to fall onto (prevents instant void death)
                    w.getBlockAt(dx, y - 1, dz).setType(Material.SAND);
                }
            }
        }
        // Top winner platform (gold)
        int topY = baseY + layers * layerHeight;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                w.getBlockAt(dx, topY, dz).setType(Material.GOLD_BLOCK);
            }
        }
    }

    /**
     * Called on PlayerMoveEvent for /glass players: if the block they're
     * standing on is wool, schedule it to disappear after 1 second. This
     * forces constant movement.
     */
    void onGlassStep(Player p) {
        if (!playerGame.getOrDefault(p.getUniqueId(), "").equals("glass")) return;
        World w = p.getWorld();
        if (!w.getName().equals("glass")) return;
        int bx = p.getLocation().getBlockX();
        int by = p.getLocation().getBlockY() - 1;
        int bz = p.getLocation().getBlockZ();
        Block b = w.getBlockAt(bx, by, bz);
        Material t = b.getType();
        if (t.name().endsWith("_WOOL")) {
            // Schedule this block to disappear after 20 ticks (1 second).
            int finalX = bx, finalY = by, finalZ = bz;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Block target = w.getBlockAt(finalX, finalY, finalZ);
                if (target.getType().name().endsWith("_WOOL")) {
                    target.setType(Material.AIR);
                }
            }, 20L);
        }
    }

    boolean territoriesLoadFailed = false;

    void loadTerritories() {
        if (!dataFile.exists()) return;
        List<String> lines;
        try {
            lines = Files.readAllLines(dataFile.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().warning("Territories read failed: " + e.getMessage());
            territoriesLoadFailed = true;
            return;
        }
        int loaded = 0;
        int skipped = 0;
        Territory t = null;
        boolean readingBlocks = false;
        for (String line : lines) {
            try {
                if (line.startsWith("#BEGIN")) {
                    t = new Territory();
                    String[] parts = line.substring(7).split("\\|");
                    if (parts.length < 3) { skipped++; continue; }
                    t.name = parts[0];
                    t.player = parts[1];
                    // Accept either comma- or space-separated coords for backward compat
                    String[] coords = parts[2].split("[,\\s]+");
                    if (coords.length < 6) { skipped++; continue; }
                    t.x1 = Integer.parseInt(coords[0].trim());
                    t.y1 = Integer.parseInt(coords[1].trim());
                    t.z1 = Integer.parseInt(coords[2].trim());
                    t.x2 = Integer.parseInt(coords[3].trim());
                    t.y2 = Integer.parseInt(coords[4].trim());
                    t.z2 = Integer.parseInt(coords[5].trim());
                    readingBlocks = false;
                } else if (line.equals("#BLOCKS")) {
                    readingBlocks = true;
                } else if (line.equals("#END")) {
                    if (t != null) {
                        territories.add(t);
                        loaded++;
                    }
                    t = null;
                    readingBlocks = false;
                } else if (readingBlocks && t != null) {
                    t.blocks.add(line);
                }
            } catch (Exception e) {
                // Per-line failure: skip this territory, keep parsing the rest
                skipped++;
                t = null;
                readingBlocks = false;
            }
        }
        if (skipped > 0) {
            getLogger().warning("Territories: loaded " + loaded + ", skipped " + skipped + " malformed entries (kept original file safe)");
            // Don't overwrite file with partial data — could destroy more valid entries
            territoriesLoadFailed = true;
        } else {
            getLogger().info("Loaded " + loaded + " territories");
        }
    }

    void saveTerritories() {
        // Safety: if load failed (partial parse, format mismatch), preserve original file
        // instead of clobbering it with potentially-incomplete data
        if (territoriesLoadFailed) {
            getLogger().warning("Territories load was incomplete; skipping save to protect data. Resolve issues and reload.");
            return;
        }
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(dataFile), StandardCharsets.UTF_8))) {
            for (Territory t : territories) {
                w.write("#BEGIN" + t.name + "|" + t.player + "|"
                        + t.x1 + "," + t.y1 + "," + t.z1 + ","
                        + t.x2 + "," + t.y2 + "," + t.z2);
                w.newLine();
                w.write("#BLOCKS");
                w.newLine();
                for (String b : t.blocks) {
                    w.write(b);
                    w.newLine();
                }
                w.write("#END");
                w.newLine();
            }
        } catch (Exception e) {
            getLogger().warning("Territories save failed: " + e.getMessage());
        }
    }

    boolean isDefault(Material m) {
        switch (m) {
            case STONE:
            case DIRT:
            case GRASS_BLOCK:
            case COBBLESTONE:
            case OAK_PLANKS:
            case OAK_LOG:
            case SAND:
            case GRAVEL:
            case WATER:
            case LAVA:
            case BEDROCK:
            case AIR:
                return true;
            default:
                return false;
        }
    }

    void takeSnapshot(Territory t) {
        World w = Bukkit.getWorlds().get(0);
        t.blocks.clear();
        for (int x = t.x1; x <= t.x2; x++) {
            for (int y = t.y1; y <= t.y2; y++) {
                for (int z = t.z1; z <= t.z2; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (!isDefault(m)) {
                        String data = b.getBlockData().getAsString();
                        t.blocks.add(x + "," + y + "," + z + "," + data);
                    }
                }
            }
        }
    }

    void restoreSnapshot(Territory t) {
        World w = Bukkit.getWorlds().get(0);
        for (String s : t.blocks) {
            try {
                int a = s.indexOf(',');
                int b = s.indexOf(',', a + 1);
                int c = s.indexOf(',', b + 1);
                int x = Integer.parseInt(s.substring(0, a));
                int y = Integer.parseInt(s.substring(a + 1, b));
                int z = Integer.parseInt(s.substring(b + 1, c));
                BlockData data = Bukkit.createBlockData(s.substring(c + 1));
                Block blk = w.getBlockAt(x, y, z);
                blk.setBlockData(data, false);
            } catch (Exception e) {
                getLogger().warning("Restore failed: " + s);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        if (!loc.getWorld().getName().equals("world")) return;
        for (Territory t : territories) {
            if (t.contains(loc)) {
                if (!e.getPlayer().getName().equalsIgnoreCase(t.player) && !e.getPlayer().isOp()) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage("§c这是 §e" + t.player + " §c的领地");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onSkywarsBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Block b = e.getBlock();
        World w = b.getWorld();
        if (!w.getName().equals("mg_skywars") && !w.getName().equals("mg_survival")) return;
        if (b.getX() != 0 || b.getY() != 60 || b.getZ() != 0) return;
        e.getPlayer().setFallDistance(0);
        // Schedule block fall
        new BukkitRunnable() {
            @Override public void run() {
                b.getWorld().getBlockAt(0, 59, 0).setType(Material.AIR);
            }
        }.runTaskLater(this, 1L);
    }

    @EventHandler
    public void onSkywarsPlayerMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
            && e.getFrom().getBlockY() == e.getTo().getBlockY()
            && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer();
        World w = p.getWorld();
        if (w == null) return;
        String name = w.getName();
        if (!name.equals("mg_skywars") && !name.equals("mg_survival")) return;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
            || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (p.getLocation().getY() < 61.0) {
            p.teleport(new Location(w, 0.5, 61, 0.5));
            p.setFallDistance(0);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Location loc = e.getBlock().getLocation();
        if (!loc.getWorld().getName().equals("world")) return;
        for (Territory t : territories) {
            if (t.contains(loc)) {
                if (!e.getPlayer().getName().equalsIgnoreCase(t.player) && !e.getPlayer().isOp()) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage("§c这是 §e" + t.player + " §c的领地");
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerGame.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
            && e.getFrom().getBlockY() == e.getTo().getBlockY()
            && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer();
        if (!"tnt".equals(playerGame.get(p.getUniqueId()))) return;
        World w = e.getTo().getWorld();
        if (w == null) return;
        if (!w.getName().equals("mg_tnt")) return;
        Block b = w.getBlockAt(e.getTo().getBlockX(), e.getTo().getBlockY() - 1, e.getTo().getBlockZ());
        Material type = b.getType();
        if (type != Material.SAND && type != Material.TNT) return;
        String key = b.getLocation().toString();
        if (tntCracking.contains(key)) return;
        tntCracking.add(key);
        Location loc = b.getLocation();
        final int xx = loc.getBlockX();
        final int yy = loc.getBlockY();
        final int zz = loc.getBlockZ();
        for (int i = 1; i <= 4; i++) {
            new BukkitRunnable() {
                @Override public void run() {
                    World w = b.getWorld();
                    if (w != null) {
                        Block b = w.getBlockAt(xx, yy, zz);
                        if (b.getType() != Material.AIR) b.setType(Material.AIR);
                    }
                }
            }.runTaskLater(this, (long) i * 5L);
        }
        new BukkitRunnable() {
            @Override public void run() {
                World w = b.getWorld();
                if (w != null) w.getBlockAt(xx, yy, zz).setType(Material.AIR);
                tntCracking.remove(key);
            }
        }.runTaskLater(this, 20L);
    }

    @EventHandler
    public void onGlassMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
            && e.getFrom().getBlockY() == e.getTo().getBlockY()
            && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer();
        if (!"glass".equals(playerGame.get(p.getUniqueId()))) return;
        World w = e.getTo().getWorld();
        if (w == null || !w.getName().equals("glass")) return;
        onGlassStep(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = label.toLowerCase();
        if (cmd.equalsIgnoreCase("hub")) {
            if (sender instanceof Player p) {
                World w = Bukkit.getWorld("world");
                if (w == null) { p.sendMessage("§c主世界不存在"); return true; }
                playerGame.remove(p.getUniqueId());
                p.teleport(new Location(w, 1000.5, 135, 1000.5));
                p.sendMessage("§b✈ 返回主城");
                return true;
            }
            sender.sendMessage("Only players!");
            return true;
        }
        if (cmd.equalsIgnoreCase("the_end")) {
            if (sender instanceof Player p) {
                World w = Bukkit.getWorld("world");
                if (w == null) { p.sendMessage("§c主世界不存在"); return true; }
                playerGame.remove(p.getUniqueId());
                p.teleport(new Location(w, 1000.5, 92.0, 1000.5));
                p.sendMessage("§5✈ 返回末地");
                return true;
            }
            sender.sendMessage("Only players!");
            return true;
        }
        if (cmd.equalsIgnoreCase("territory")) {
            return handleTerritory(sender, args);
        }
        // AuthMe commands
        if (cmd.equalsIgnoreCase("register")) return authMe.handleRegister(sender, args);
        if (cmd.equalsIgnoreCase("login")) return authMe.handleLogin(sender, args);
        if (cmd.equalsIgnoreCase("changepassword")) return authMe.handleChangePassword(sender, args);
        if (cmd.equalsIgnoreCase("logout")) return authMe.handleLogout(sender, args);
        // Claims command
        if (cmd.equalsIgnoreCase("claim")) return claims.handleClaim(sender);
        // TreeMiner command
        if (cmd.equalsIgnoreCase("treeminer")) return treeMiner.handleToggle(sender);
        // Minigame commands
        for (String g : GAMES) {
            if (cmd.equalsIgnoreCase(g)) {
                return handleGame(sender, g, args);
            }
        }
        // Essentials commands (added for compatibility with new Essentials.java)
        if (essentials != null) {
            switch (cmd.toLowerCase()) {
                case "home": case "homes": return essentials.handleHome(sender, args);
                case "sethome": case "createhome": return essentials.handleSetHome(sender, args);
                case "delhome": case "remhome": case "rmhome": return essentials.handleDelHome(sender, args);
                case "spawn": return essentials.handleSpawn(sender);
                case "setspawn": return essentials.handleSetSpawn(sender);
                case "tpa": case "call": case "tpask": return essentials.handleTpa(sender, args);
                case "tpaccept": case "tpyes": return essentials.handleTpAccept(sender);
                case "tpdeny": case "tpno": return essentials.handleTpDeny(sender);
                case "msg": case "m": case "t": case "w": case "pm": case "tell": case "whisper":
                    return essentials.handleMsg(sender, args);
                case "r": case "reply": return essentials.handleReply(sender, args);
                case "afk": return essentials.handleAfk(sender);
                case "back": return essentials.handleBack(sender);
                // Other essentials commands (153 total)
                case "heal": return essentials.handleHeal(sender, args);
                case "feed": case "eat": return essentials.handleFeed(sender, args);
                case "fly": return essentials.handleFly(sender, args);
                case "god": case "godmode": case "tgm": return essentials.handleGod(sender, args);
                case "repair": case "fix": return essentials.handleRepair(sender, args);
                case "hat": return essentials.handleHat(sender, args);
                case "workbench": case "craft": case "wb": case "wbench": return essentials.handleWorkbench(sender, args);
                case "jump": case "j": case "jumpto": return essentials.handleJump(sender, args);
                case "compass": case "direction": return essentials.handleCompass(sender, args);
                case "getpos": case "coords": case "position": case "whereami": case "getlocation": case "getloc":
                    return essentials.handleGetpos(sender, args);
                case "depth": case "height": return essentials.handleDepth(sender, args);
                case "near": case "nearby": return essentials.handleNear(sender, args);
                case "info": case "about": case "ifo": case "news": case "inform":
                    return essentials.handleInfo(sender, args);
                case "seen": case "alts": return essentials.handleSeen(sender, args);
                case "suicide": return essentials.handleSuicide(sender, args);
                case "ignore": case "unignore": case "delignore": case "remignore": case "rmignore":
                    return essentials.handleIgnore(sender, args);
                case "ping": case "echo": case "pong": return essentials.handlePing(sender, args);
                case "me": case "action": case "describe": return essentials.handleMe(sender, args);
                case "speed": case "walkspeed": case "wspeed": return essentials.handleSpeed(sender, args);
                case "flyspeed": case "fspeed": return essentials.handleFlyspeed(sender, args);
                case "disposal": case "trash": return essentials.handleDisposal(sender, args);
                case "nick": case "nickname": return essentials.handleNick(sender, args);
                case "realname": return essentials.handleRealname(sender, args);
                case "whois": return essentials.handleWhois(sender, args);
                case "rules": return essentials.handleRules(sender, args);
                case "motd": return essentials.handleMotd(sender, args);
                case "top": return essentials.handleTop(sender, args);
                case "bottom": return essentials.handleBottom(sender, args);
                case "tpo": return essentials.handleTpo(sender, args);
                case "tpall": case "tpaall": return essentials.handleTpall(sender, args);
                case "tpahere": return essentials.handleTpaHere(sender, args);
                case "tpohere": return essentials.handleTpoHere(sender, args);
                case "tpoffline": case "otp": case "offlinetp": case "tpoff": return essentials.handleTpoOffline(sender, args);
                case "tpacancel": return essentials.handleTpaCancel(sender, args);
                case "tpauto": return essentials.handleTpAuto(sender, args);
                case "tpr": case "tprandom": return essentials.handleTpRandom(sender, args);
                case "settpr": case "settprandom": return essentials.handleTpRandom(sender, args);
                case "tptoggle": return essentials.handleTpToggle(sender, args);
                case "renamehome": return essentials.handleRenamehome(sender, args);
                case "warp": return essentials.handleWarp(sender, args);
                case "warps": return essentials.handleWarps(sender, args);
                case "setwarp": case "createwarp": return essentials.handleSetwarp(sender, args);
                case "delwarp": case "remwarp": case "rmwarp": return essentials.handleDelwarp(sender, args);
                case "warpinfo": return essentials.handleWarp(sender, args);
                case "balance": case "bal": return essentials.handleBalance(sender, args);
                case "money": return mintconomy.handleMoney(sender, args);
                case "pay": return essentials.handlePay(sender, args);
                case "eco": case "economy": return essentials.handleEco(sender, args);
                case "balancetop": case "baltop": return essentials.handleBalancetop(sender, args);
                case "worth": return essentials.handleWorth(sender, args);
                case "price": return essentials.handlePrice(sender, args);
                case "sell": return essentials.handleSell(sender, args);
                case "setworth": return essentials.handleSetWorth(sender, args);
                case "kit": return essentials.handleKit(sender, args);
                case "kits": return essentials.handleKits(sender, args);
                case "createkit": case "kitcreate": case "createk": case "kc": case "ck":
                    return essentials.handleCreateKit(sender, args);
                case "delkit": return essentials.handleDelKit(sender, args);
                case "showkit": case "preview": case "kitpreview": return essentials.handleShowKit(sender, args);
                case "kitreset": case "resetkit": case "kitr": return essentials.handleKitReset(sender, args);
                case "kick": return essentials.handleKick(sender, args);
                case "kickall": return essentials.handleKickAll(sender, args);
                case "ban": return essentials.handleBan(sender, args);
                case "tempban": return essentials.handleTempBan(sender, args);
                case "banip": return essentials.handleBanIp(sender, args);
                case "tempbanip": return essentials.handleTempBanIp(sender, args);
                case "unban": case "pardon": return essentials.handleUnban(sender, args);
                case "unbanip": case "pardonip": return essentials.handleUnbanIp(sender, args);
                case "mute": case "silence": return essentials.handleMute(sender, args);
                case "unmute": return essentials.handleUnmute(sender, args);
                case "jail": case "togglejail": case "tjail": return essentials.handleJail(sender, args);
                case "unjail": return essentials.handleUnjail(sender, args);
                case "setjail": case "createjail": return essentials.handleSetjail(sender, args);
                case "deljail": case "remjail": case "rmjail": return essentials.handleDeljail(sender, args);
                case "jails": return essentials.handleJails(sender, args);
                case "jailedplayers": case "ejailed": case "ejp": return essentials.handleJailedPlayers(sender, args);
                case "burn": return essentials.handleBurn(sender, args);
                case "ext": case "extinguish": return essentials.handleExt(sender, args);
                case "lightning": case "smite": case "shock": case "strike": case "thor":
                    return essentials.handleLightning(sender, args);
                case "nuke": return essentials.handleNuke(sender, args);
                case "fireball": case "fireentity": case "fireskull": return essentials.handleFireball(sender, args);
                case "thunder": return essentials.handleThunder(sender, args);
                case "freeze": case "ice": return essentials.handleFreeze(sender, args);
                case "item": case "i": return essentials.handleItem(sender, args);
                case "more": return essentials.handleMore(sender, args);
                case "skull": case "playerskull": return essentials.handleSkull(sender, args);
                case "itemlore": case "lore": case "ilore": return essentials.handleItemLore(sender, args);
                case "itemname": case "iname": return essentials.handleItemName(sender, args);
                case "itemrename": case "irename": return essentials.handleItemName(sender, args);
                case "itemdb": case "dura": case "durability": case "itemno":
                    return essentials.handleItemDb(sender, args);
                case "invsee": return essentials.handleInvsee(sender, args);
                case "clearinventory": case "ci": case "clean": case "clear": case "clearinvent":
                    return essentials.handleClearInventory(sender, args);
                case "powertool": case "pt": return essentials.handlePowertool(sender, args);
                case "powertooltoggle": case "ptt": case "pttoggle": return essentials.handlePowertoolToggle(sender, args);
                case "powertoollist": case "ptlist": return essentials.handlePowertoolList(sender, args);
                case "unlimited": case "ul": case "unl": return essentials.handleUnlimited(sender, args);
                case "anvil": return essentials.handleAnvil(sender, args);
                case "enderchest": case "ec": case "echest": case "endersee":
                    return essentials.handleEnderChest(sender, args);
                case "grindstone": return essentials.handleGrindstone(sender, args);
                case "loom": return essentials.handleLoom(sender, args);
                case "cartographytable": case "carttable": return essentials.handleCartographyTable(sender, args);
                case "stonecutter": return essentials.handleStonecutter(sender, args);
                case "smithingtable": case "smithtable": return essentials.handleSmithingTable(sender, args);
                case "recipe": case "formula": case "method": return essentials.handleRecipe(sender, args);
                case "recipes": return essentials.handleRecipes(sender, args);
                case "book": return essentials.handleBook(sender, args);
                case "bookauthor": return essentials.handleBookAuthor(sender, args);
                case "editsign": case "sign": return essentials.handleEditSign(sender, args);
                case "condense": case "compact": case "blocks": case "toblocks":
                    return essentials.handleCondense(sender, args);
                case "spawner": case "changems": case "mobspawner": return essentials.handleSpawner(sender, args);
                case "spawnmob": case "mob": case "spawnentity": return essentials.handleSpawnMob(sender, args);
                case "tree": return essentials.handleTree(sender, args);
                case "bigtree": case "largetree": return essentials.handleBigTree(sender, args);
                case "forest": return essentials.handleForest(sender, args);
                case "break": return essentials.handleBreak(sender, args);
                case "poof": return essentials.handlePoof(sender, args);
                case "vanish": case "v": return essentials.handleVanish(sender, args);
                case "socialspy": return essentials.handleSocialSpy(sender, args);
                case "firework": return essentials.handleFirework(sender, args);
                case "color": return essentials.handleColor(sender, args);
                case "antioch": case "grenade": case "tnt": return essentials.handleAntioch(sender, args);
                case "beezooka": case "beecannon": return essentials.handleBeezooka(sender, args);
                case "kittycannon": return essentials.handleKittycannon(sender, args);
                case "world": return essentials.handleWorld(sender, args);
                case "worlds": return essentials.handleWorlds(sender, args);
                case "playtime": return essentials.handlePlaytime(sender, args);
                case "essentials": case "ess": case "essversion":
                    return essentials.handleEssentials(sender, args);
                case "sudo": return essentials.handleSudo(sender, args);
                case "paytoggle": case "payon": case "payoff": return essentials.handlePayToggle(sender, args);
                case "payconfirmtoggle": case "payconfirm": case "payconfirmon": case "payconfirmoff":
                    return essentials.handlePayConfirmToggle(sender, args);
                case "msgtoggle": return essentials.handleMsgToggle(sender, args);
                case "rtoggle": case "replytoggle": return essentials.handleRToggle(sender, args);
                case "ptime": case "playertime": return essentials.handlePtime(sender, args);
                case "pweather": case "playerweather": return essentials.handlePweather(sender, args);
                case "mail": return essentials.handleMail(sender, args);
                case "market": case "shop": case "rptmarket": return rptMarket.handleMarket(sender, args);
                case "bank": return mintconomy.handleBank(sender, args);
                case "loan": return mintconomy.handleLoan(sender, args);
                case "co": return coreProtect.handleCo(sender, args);
                case "magister": case "ac": return magisterAC.handleMagister(sender, args);
                case "lp": case "luckperms": return luckPerms.handleLp(sender, args);
                case "rpi": case "raspberrypi": case "mcpiserver": return raspberryPi.handleRpi(sender, args);
                // ===== 11 EssentialsX duplicate commands (restored) =====
                case "gamemode": case "gm": case "gms": case "gmc": case "gma": case "gmsp":
                    return essentials.handleGamemode(sender, args);
                case "time": case "day": case "night": case "dawn": case "dusk":
                    return essentials.handleTime(sender, args);
                case "tp": case "teleport":
                    return essentials.handleTp(sender, args);
                case "tphere":
                    return essentials.handleTphere(sender, args);
                case "tppos":
                    return essentials.handleTppos(sender, args);
                case "give":
                    return essentials.handleGive(sender, args);
                case "kill":
                    return essentials.handleKill(sender, args);
                case "killall": case "butcher": case "mobkill":
                    return handleKillAll(sender, args);
                case "list": case "online": case "ls":
                    return essentials.handleList(sender, args);
                case "help": case "?":
                    return essentials.handleHelp(sender, args);
                case "enchant":
                    return essentials.handleEnchant(sender, args);
                case "exp": case "xp":
                    return essentials.handleExp(sender, args);
                case "weather":
                    return essentials.handleWeather(sender, args);
            }
        }
        return false;
    }

    /**
     * /killall [world|all|mobs|monsters|animals|items|xp]
     *   no arg      → kill all mobs + items + xp in sender's world
     *   "all"       → kill all entities (except players) in every world
     *   "mobs"      → only mobs (living entities) in sender's world
     *   "monsters"  → only hostile mobs (zombies, skeletons, creepers, ...)
     *   "animals"   → only passive mobs (cows, pigs, sheep, ...)
     *   "items"     → only dropped items
     *   "xp"        → only XP orbs
     *   <worldName> → only that world
     */
    boolean handleKillAll(CommandSender sender, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("§c✗ 需要 OP 权限"); return true; }

        String filter = args.length == 0 ? "all" : args[0].toLowerCase();

        // Decide which worlds to act on
        java.util.List<World> targets = new java.util.ArrayList<>();
        World senderWorld = (sender instanceof Player p) ? p.getWorld() : null;
        boolean allWorlds = filter.equals("all");
        if (allWorlds) {
            targets.addAll(Bukkit.getWorlds());
        } else if (senderWorld != null) {
            // Try to interpret filter as a world name first
            boolean matched = false;
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(filter)) { targets.add(w); matched = true; break; }
            }
            if (!matched) targets.add(senderWorld);
        } else {
            targets.addAll(Bukkit.getWorlds());
        }

        // Categorize the filter
        boolean killMonsters, killAnimals, killItems, killXp, killLiving, killAll;
        switch (filter) {
            case "monsters": case "monster": case "hostile": case "hostiles":
                killMonsters = true; killLiving = killAnimals = killItems = killXp = killAll = false; break;
            case "animals": case "animal": case "passive": case "passives":
                killAnimals = true; killLiving = killMonsters = killItems = killXp = killAll = false; break;
            case "items": case "drops": case "drop":
                killItems = true; killLiving = killMonsters = killAnimals = killXp = killAll = false; break;
            case "xp": case "orbs": case "orb":
                killXp = true; killLiving = killMonsters = killAnimals = killItems = killAll = false; break;
            case "mobs": case "mob":
                killLiving = true; killMonsters = killAnimals = killItems = killXp = killAll = false; break;
            default:
                killAll = true; killLiving = killMonsters = killAnimals = killItems = killXp = false; break;
        }

        // Mob category sets (Bukkit EntityType)
        java.util.Set<EntityType> monsters = java.util.EnumSet.of(
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
                EntityType.CAVE_SPIDER, EntityType.WITCH, EntityType.SLIME, EntityType.MAGMA_CUBE,
                EntityType.BLAZE, EntityType.GHAST, EntityType.ENDERMAN, EntityType.WITHER_SKELETON,
                EntityType.STRAY, EntityType.HUSK, EntityType.DROWNED, EntityType.PHANTOM,
                EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.RAVAGER,
                EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN, EntityType.ZOGLIN,
                EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.SHULKER, EntityType.SILVERFISH,
                EntityType.ENDERMITE, EntityType.WARDEN, EntityType.BOGGED);
        java.util.Set<EntityType> animals = java.util.EnumSet.of(
                EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE,
                EntityType.DONKEY, EntityType.MULE, EntityType.RABBIT, EntityType.FOX, EntityType.CAT,
                EntityType.WOLF, EntityType.PARROT, EntityType.OCELOT, EntityType.LLAMA, EntityType.TRADER_LLAMA,
                EntityType.TURTLE, EntityType.PANDA, EntityType.BEE, EntityType.POLAR_BEAR, EntityType.AXOLOTL,
                EntityType.GOAT, EntityType.FROG, EntityType.CAMEL, EntityType.SNIFFER, EntityType.ARMADILLO,
                EntityType.MOOSHROOM, EntityType.SALMON, EntityType.COD, EntityType.PUFFERFISH,
                EntityType.TROPICAL_FISH, EntityType.DOLPHIN, EntityType.SQUID, EntityType.GLOW_SQUID,
                EntityType.TADPOLE, EntityType.STRIDER, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM,
                EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.POLAR_BEAR);

        int totalKilled = 0;
        for (World w : targets) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Player) continue;

                if (e instanceof Item) { if (killItems) { e.remove(); totalKilled++; } continue; }
                if (e instanceof ExperienceOrb) { if (killXp) { e.remove(); totalKilled++; } continue; }

                if (e instanceof LivingEntity) {
                    if (killAll || killLiving) { e.remove(); totalKilled++; continue; }
                    EntityType t = e.getType();
                    if (killMonsters && monsters.contains(t)) { e.remove(); totalKilled++; continue; }
                    if (killAnimals && animals.contains(t)) { e.remove(); totalKilled++; continue; }
                    continue;
                }

                // Other entities (arrows, boats, minecarts, paintings, item frames, etc.)
                if (killAll) { e.remove(); totalKilled++; }
            }
        }

        String scope = allWorlds ? "所有世界" : targets.get(0).getName();
        String what;
        if (filter.equals("monsters") || filter.equals("hostile") || filter.equals("hostiles")) what = "敌对生物";
        else if (filter.equals("animals") || filter.equals("animal") || filter.equals("passive")) what = "友好生物";
        else if (filter.equals("items") || filter.equals("drops")) what = "掉落物";
        else if (filter.equals("xp") || filter.equals("orbs")) what = "经验球";
        else if (filter.equals("mobs")) what = "生物";
        else what = "实体";

        sender.sendMessage("§a✦ 已清除 " + scope + " 中的 " + totalKilled + " 个" + what);
        getLogger().info((sender instanceof Player p ? p.getName() : sender.getName())
                + " 触发了 /killall " + filter + " (" + totalKilled + " killed in " + scope + ")");
        return true;
    }

    boolean handleTerritory(CommandSender sender, String[] args) {
        if (!sender.isOp()) { sender.sendMessage("§c✗ 需要 OP 权限"); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6=== 领地系统 ===");
            sender.sendMessage("§e/territory <x1> <y1> <z1> <x2> <y2> <z2> <name> <player>");
            sender.sendMessage("§e/territory list");
            sender.sendMessage("§e/territory remove <name>");
            sender.sendMessage("§e/territory reset");
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§a领地数量: " + territories.size());
            for (Territory t : territories) {
                sender.sendMessage("§a - §e" + t.name + " (" + t.player + ", " + t.blocks.size() + " blocks, "
                    + t.x1 + "," + t.y1 + "," + t.z1 + " → " + t.x2 + "," + t.y2 + "," + t.z2 + ")");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) { sender.sendMessage("§c用法: /territory remove <name>"); return true; }
            String n = args[1];
            boolean removed = territories.removeIf(t -> t.name.equalsIgnoreCase(n));
            if (removed) saveTerritories();
            sender.sendMessage(removed ? "§a已删除领地 §e" + n : "§c领地 §e" + n + " §c不存在");
            return true;
        }
        if (args[0].equalsIgnoreCase("reset")) {
            sender.sendMessage("§e正在重置领地...");
            triggerReset();
            return true;
        }
        if (args.length < 8) {
            sender.sendMessage("§c用法: /territory <x1> <y1> <z1> <x2> <y2> <z2> <name> <player>");
            return true;
        }
        try {
            Territory t = new Territory();
            int ax = Integer.parseInt(args[0]);
            int ay = Integer.parseInt(args[1]);
            int az = Integer.parseInt(args[2]);
            int bx = Integer.parseInt(args[3]);
            int by = Integer.parseInt(args[4]);
            int bz = Integer.parseInt(args[5]);
            t.x1 = Math.min(ax, bx);
            t.y1 = Math.min(ay, by);
            t.z1 = Math.min(az, bz);
            t.x2 = Math.max(ax, bx);
            t.y2 = Math.max(ay, by);
            t.z2 = Math.max(az, bz);
            t.name = args[6];
            t.player = args[7];
            int vol = (t.x2 - t.x1 + 1) * (t.y2 - t.y1 + 1) * (t.z2 - t.z1 + 1);
            if (vol > 1000000) { sender.sendMessage("§c区域太大，最多 100x100x100"); return true; }
            sender.sendMessage("§e正在扫描方块快照...");
            takeSnapshot(t);
            territories.add(t);
            saveTerritories();
            sender.sendMessage("§a已保存领地 §e" + t.name + " §a(玩家 " + t.player + ")");
            sender.sendMessage("§e坐标: " + t.x1 + "," + t.y1 + "," + t.z1 + " → " + t.x2 + "," + t.y2 + "," + t.z2);
            sender.sendMessage("§e方块数: " + t.blocks.size());
        } catch (NumberFormatException e) {
            sender.sendMessage("§c坐标解析错误");
        }
        return true;
    }

    boolean handleGame(CommandSender sender, String game, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players!"); return true; }
        GameWorld gw = gameWorlds.get(game);
        if (gw == null || gw.world == null) {
            p.sendMessage("§c游戏世界尚未准备好");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage("§6=== " + game + " ===");
            p.sendMessage("§7用法: §f/" + game + " join [人数]");
            p.sendMessage("§7     §f/" + game + " leave");
            p.sendMessage("§7     §f/" + game + " status");
            return true;
        }
        if (args[0].equalsIgnoreCase("join")) {
            int n = 1;
            try {
                if (args.length >= 2) {
                    n = Integer.parseInt(args[1]);
                    if (n < 1) n = 1;
                    if (n > 8) n = 8;
                }
            } catch (NumberFormatException e) {
                p.sendMessage("§c请输入 1-8 之间的数字");
                return true;
            }
            if (n == 1) {
                playerGame.put(p.getUniqueId(), game);
                playerCount.put(p.getUniqueId(), 1);
                p.teleport(gw.joinLoc);
                p.sendMessage("§a已加入 §f" + game);
                p.sendMessage("§7输入 §f/hub §7退出");
                return true;
            }
            Map<Integer, List<UUID>> queue = gameQueues.get(game);
            queue.computeIfAbsent(n, k -> new ArrayList<>()).add(p.getUniqueId());
            playerGame.put(p.getUniqueId(), game);
            playerCount.put(p.getUniqueId(), n);
            p.teleport(gw.joinLoc);
            int size = queue.get(n).size();
            p.sendMessage("§a已加入 §f" + game + " §7(" + size + "/" + n + ")");
            p.sendMessage("§7达到人数后会自动开始游戏. §f/hub §7退出");
            if (size >= n) {
                startMultiGame(game, n, queue.get(n));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            UUID uid = p.getUniqueId();
            String left = playerGame.remove(uid);
            playerCount.remove(uid);
            if (left != null) {
                Map<Integer, List<UUID>> q = gameQueues.get(left);
                if (q != null) {
                    for (List<UUID> l : q.values()) l.remove(uid);
                }
                World hub = Bukkit.getWorld("world");
                if (hub != null) p.teleport(new Location(hub, 1000.5, 135, 1000.5));
                p.sendMessage("§a退出游戏 §f" + left);
            } else {
                p.sendMessage("§c你还没加入任何游戏房间");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            Map<Integer, List<UUID>> q = gameQueues.get(game);
            p.sendMessage("§6=== " + game + " 队列 ===");
            if (q == null || q.isEmpty()) {
                p.sendMessage("§7  当前没有队列");
                return true;
            }
            for (Map.Entry<Integer, List<UUID>> e : q.entrySet()) {
                p.sendMessage("§7  - " + e.getKey() + "人: " + e.getValue().size() + "/" + e.getKey());
                for (UUID u : e.getValue()) {
                    Player pl = Bukkit.getPlayer(u);
                    if (pl != null) p.sendMessage("§7    - §f" + pl.getName());
                }
            }
            return true;
        }
        p.sendMessage("§c未知子命令: " + args[0]);
        return true;
    }

    void startMultiGame(String game, int n, List<UUID> players) {
        GameWorld gw = gameWorlds.get(game);
        if (gw == null) return;
        for (int i = 0; i < players.size(); i++) {
            UUID u = players.get(i);
            Player pl = Bukkit.getPlayer(u);
            if (pl == null) continue;
            double angle = (2 * Math.PI) * i / n;
            double radius = 10.0;
            double xx = Math.cos(angle) * radius;
            double zz = Math.sin(angle) * radius;
            pl.teleport(new Location(gw.world, xx, 65.0, zz));
            pl.sendMessage("§a游戏开始! §f" + game + " §a" + n + "人");
        }
        Bukkit.broadcastMessage("§e" + game + " §f" + n + "人游戏开始!");
    }

    void triggerReset() {
        getLogger().info("Auto-reset triggered");
        lastReset = System.currentTimeMillis();
        for (Territory t : territories) {
            takeSnapshot(t);
        }
        saveTerritories();
        try {
            File f = new File("reset_world.bat");
            if (f.exists()) {
                new Thread(() -> {
                    try {
                        new ProcessBuilder("cmd", "/c", "reset_world.bat")
                                .directory(new File("."))
                                .redirectErrorStream(true)
                                .start().waitFor();
                    } catch (Exception e) {
                        getLogger().warning("Reset script failed: " + e.getMessage());
                    }
                }).start();
                Bukkit.getScheduler().runTaskLater(this,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop"),
                    100L);
            } else {
                Bukkit.getScheduler().runTaskLater(this,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop"),
                    100L);
            }
        } catch (Exception e) {
            getLogger().warning("Reset failed: " + e.getMessage());
        }
    }

    // ===== INNER CLASSES =====

    public static class GameWorld {
        public String name;
        public World world;
        public Location spawnLoc;
        public Location joinLoc;
    }

    public static class Territory {
        public String name;
        public String player;
        public int x1, y1, z1, x2, y2, z2;
        public List<String> blocks = new ArrayList<>();
        public boolean contains(Location loc) {
            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();
            return bx >= x1 && bx <= x2 && by >= y1 && by <= y2 && bz >= z1 && bz <= z2;
        }
    }

    public static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, ChunkGenerator.BiomeGrid grid) {
            return createChunkData(world);
        }
    }
}
