package live.minehub.polarpaper;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import live.minehub.polarpaper.commands.CommandManager;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.source.FilePolarSource;
import live.minehub.polarpaper.core.util.FoliaUtil;
import live.minehub.polarpaper.core.util.ShutdownExecutor;
import live.minehub.polarpaper.util.WorldKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class PolarPaper extends JavaPlugin {

    // A save should never hang now that its tasks are actually run, but a stop that never finishes is worse than
    // a world that failed to save, so give up eventually. Folia allows itself the same 60s to halt its schedulers.
    private static final long SAVE_ON_STOP_TIMEOUT_SECONDS = 60;

    @Override
    public void onEnable() {
        FoliaUtil.isFolia(); // Fix classloader error on stop

        // Paper commands
        LifecycleEventManager<@NotNull Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            new CommandManager().register(commands);
        });

        registerEvents();

        Path pluginFolder = getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");

        worldsFolder.toFile().mkdirs();

        saveDefaultConfig();

        try (var files = Files.walk(worldsFolder, 4, FileVisitOption.FOLLOW_LINKS)) {
            files.forEach(path -> {
                if (Files.isDirectory(path) || !path.getFileName().toString().endsWith(".polar")) {
                    return;
                }

                String worldName = WorldKey.getWorldName(path);

                Config config = Config.readFromConfig(getConfig(), worldName);

                if (!config.loadOnStartup()) return;

                getLogger().info("Loading polar world: " + worldName);

                Polar.createWorld(new FilePolarSource(path), worldName);
            });
        } catch (IOException e) {
            getLogger().warning("Failed to load world on startup");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            getLogger().warning(exceptionAsString);
        }
    }

    @Override
    public void onDisable() {
        // Saving schedules tasks that no scheduler is going to run anymore at this point, so they get run on this
        // thread instead. Without this the save either blocks forever or quietly drops whatever it was waiting on.
        ShutdownExecutor.start();
        try {
            for (World world : getServer().getWorlds()) {
                PolarGenerator generator = PolarGenerator.fromWorld(world);
                if (generator == null) continue;

                Path worldFolderPath = world.getWorldFolder().toPath();
                if (Files.exists(worldFolderPath)) {
                    getLogger().info("Clearing temp files for " + world.getKey().getKey());
                    try (Stream<Path> paths = Files.walk(worldFolderPath)) {
                        paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                    } catch (IOException e) {
                        getLogger().warning("Failed to delete temp files for " + world.getKey().getKey());
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        String exceptionAsString = sw.toString();
                        getLogger().warning(exceptionAsString);
                    }
                }

                if (!generator.getConfig().saveOnStop()) {
                    getLogger().info(String.format("Not saving '%s' as it has save on stop disabled", world.getKey().getKey()));
                    continue;
                }
                if (Polar.isLoading(world.getKey())) {
                    getLogger().info(String.format("Not saving '%s' as it was not fully loaded", world.getKey().getKey()));
                    continue;
                }

                saveOnStop(world);
            }
        } finally {
            ShutdownExecutor.stop();
        }
    }

    private void saveOnStop(World world) {
        String worldName = world.getKey().getKey();
        getLogger().info("Saving '" + worldName + "'...");

        long before = System.nanoTime();
        Polar.updateConfig(world, worldName);

        CompletableFuture<Void> saveFuture = Polar.saveWorld(world);
        if (!ShutdownExecutor.awaitCompletion(saveFuture, SAVE_ON_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            getLogger().severe(String.format("Gave up saving '%s' after %s seconds", worldName, SAVE_ON_STOP_TIMEOUT_SECONDS));
            return;
        }

        try {
            saveFuture.join();
        } catch (CompletionException | CancellationException e) {
            getLogger().log(Level.SEVERE, String.format("Failed to save '%s'", worldName), e);
            return;
        }

        int ms = (int) ((System.nanoTime() - before) / 1_000_000);
        getLogger().info(String.format("Saved '%s' in %sms", worldName, ms));
    }

    public static PolarPaper getPlugin() {
        return getPlugin(PolarPaper.class);
    }

    public static Path getConfigPath() {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        return pluginFolder.resolve("config.yml");
    }

    public static void registerEvents() {
        PolarPaper.getPlugin().getServer().getPluginManager().registerEvents(new PolarListener(), PolarPaper.getPlugin());
    }

}