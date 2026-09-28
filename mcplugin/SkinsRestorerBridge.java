import net.skinsrestorer.bukkit.SRBukkitAdapter;
import net.skinsrestorer.bukkit.SRBukkitInit;
import net.skinsrestorer.bukkit.logger.BukkitConsoleImpl;
import net.skinsrestorer.bukkit.update.UpdateDownloaderGithub;
import net.skinsrestorer.bukkit.utils.PluginJarProvider;
import net.skinsrestorer.shared.log.JavaLoggerImpl;
import net.skinsrestorer.shared.plugin.SRBootstrapper;
import net.skinsrestorer.shared.plugin.SRServerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge that bootstraps the embedded SkinsRestorer plugin (its classes are
 * shaded into StarStackmc.jar) by calling SRBootstrapper.startPlugin directly.
 *
 * Why not use SRBukkitBootstrap?
 *   SRBukkitBootstrap extends JavaPlugin, and JavaPlugin's constructor calls
 *   PluginClassLoader.initialize(plugin). Since StarStackmc.jar's classloader
 *   already has StarStack registered as its plugin, the second JavaPlugin
 *   instantiation throws "Plugin already initialized!". We bypass that by
 *   calling SRBootstrapper.startPlugin directly, supplying StarStack itself
 *   as the JavaPlugin context (it is a JavaPlugin, just not registered to
 *   SkinsRestorer).
 *
 * The internal class is named StarStack (not StarStackmc) to keep binary
 * compatibility with the 11 pre-compiled sub-modules in StarStack-final11.jar
 * — they have constructors expecting a StarStack parameter type.
 */
public final class SkinsRestorerBridge {
    private static final AtomicReference<Runnable> SHUTDOWN = new AtomicReference<>();

    private SkinsRestorerBridge() {}

    public static void start(StarStack starStack) {
        Server server = Bukkit.getServer();
        Path pluginFile = getFile(starStack);

        // SkinsRestorer's data folder (lives inside StarStackmc's data folder).
        Path dataFolder = starStack.getDataFolder().toPath().resolve("SkinsRestorer");
        File df = dataFolder.toFile();
        if (!df.exists() && !df.mkdirs()) {
            starStack.getLogger().warning("[SkinsRestorer] could not create data folder: " + dataFolder);
        }

        JavaLoggerImpl logger = new JavaLoggerImpl(
                new BukkitConsoleImpl(server.getConsoleSender()),
                server.getLogger());

        // The PlatformClass records supply SkinsRestorer's Injector with the
        // platform objects it would normally get from a JavaPlugin instance.
        PluginJarProvider pluginJarProvider = new PluginJarProvider() {
            @Override public Path get() { return pluginFile; }
        };
        net.skinsrestorer.shared.update.DownloaderClassProvider downloaderClassProvider =
                new net.skinsrestorer.shared.update.DownloaderClassProvider() {
                    @Override public Class<? extends net.skinsrestorer.shared.update.UpdateDownloader> get() {
                        return UpdateDownloaderGithub.class;
                    }
                };

        List<SRBootstrapper.PlatformClass<?>> platformRegister = List.of(
                new SRBootstrapper.PlatformClass<>(JavaPlugin.class, starStack),
                new SRBootstrapper.PlatformClass<>(Server.class, server),
                new SRBootstrapper.PlatformClass<>(PluginJarProvider.class, pluginJarProvider),
                new SRBootstrapper.PlatformClass<>(net.skinsrestorer.shared.update.DownloaderClassProvider.class, downloaderClassProvider)
        );

        try {
            SRBootstrapper.startPlugin(
                    SHUTDOWN::set,
                    platformRegister,
                    logger,
                    true,
                    SRBukkitAdapter.class,
                    SRServerPlugin.class,
                    dataFolder,
                    SRBukkitInit.class);
            starStack.getLogger().info("[SkinsRestorer] Embedded SkinsRestorer started (data: " + dataFolder + ")");
        } catch (Throwable t) {
            starStack.getLogger().warning("[SkinsRestorer] Bridge start failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static void shutdown() {
        Runnable r = SHUTDOWN.getAndSet(null);
        if (r != null) {
            try { r.run(); } catch (Throwable ignored) { }
        }
    }

    /** JavaPlugin.getFile() is protected — reach it via reflection. */
    private static Path getFile(StarStack starStack) {
        try {
            // getFile is declared on JavaPlugin (inherited), not on StarStack
            Method m = JavaPlugin.class.getDeclaredMethod("getFile");
            m.setAccessible(true);
            return ((File) m.invoke(starStack)).toPath();
        } catch (Throwable t) {
            throw new RuntimeException("Could not read StarStack.getFile()", t);
        }
    }
}