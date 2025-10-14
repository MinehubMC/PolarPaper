package live.minehub.polarpaper;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import live.minehub.polarpaper.commands.PolarCommand;
import live.minehub.polarpaper.source.FilePolarSource;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class PolarPaper extends JavaPlugin {

    @Override
    public void onEnable() {
        // Paper commands
        LifecycleEventManager<@NotNull Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            PolarCommand.register(commands);
        });

        registerEvents();

        Path pluginFolder = Path.of(getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");

        worldsFolder.toFile().mkdirs();

        saveDefaultConfig();

        try (var files = Files.list(worldsFolder)) {
            files.forEach(path -> {
                if (!path.getFileName().toString().endsWith(".polar")) {
                    return;
                }

                String worldName = path.getFileName().toString().split("\\.polar")[0];

                // add to config if not already there
                if (!Polar.isInConfig(worldName)) {
                    Config.writeToConfig(getConfig(), worldName, Config.DEFAULT);
                }

                Config config = Config.readFromConfig(getConfig(), worldName);
                if (config == null) {
                    getLogger().warning("Polar world '" + worldName + "' has an invalid config, skipping.");
                    return;
                }

                if (!config.loadOnStartup()) return;

                getLogger().info("Loading polar world: " + worldName);

                Polar.loadWorldConfigSource(worldName);
            });
        } catch (IOException e) {
            getLogger().warning("Failed to load world on startup");
            getLogger().warning(e.toString());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Clearing temp directory");
        Path pluginFolder = Path.of(getDataFolder().getAbsolutePath());
        Path tempFolder = pluginFolder.resolve("temp");
        if (Files.exists(tempFolder)) {
            try (Stream<Path> paths = Files.walk(tempFolder)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                getLogger().warning("Failed to delete temp directory");
                getLogger().log(Level.INFO, e.getMessage(), e);
            }
        } else {
            getLogger().log(Level.INFO, "Temp directory does not exist. Skipping...");
        }

        for (World world : getServer().getWorlds()) {
            PolarWorld polarWorld = PolarWorld.fromWorld(world);
            PolarGenerator generator = PolarGenerator.fromWorld(world);
            if (polarWorld == null || generator == null) continue;

            if (!generator.getConfig().saveOnStop()) {
                PolarPaper.logger().info(String.format("Not saving '%s' as it has save on stop disabled", world.getName()));
                return;
            }

            getLogger().info("Saving '" + world.getName() + "'...");

            long before = System.nanoTime();
            Path worldsFolder = pluginFolder.resolve("worlds");
            Path path = worldsFolder.resolve(world.getName() + ".polar");
            Polar.saveWorldSync(world, polarWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, new FilePolarSource(path), ChunkSelector.all(), 0, 0);
            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            PolarPaper.logger().info(String.format("Saved '%s' in %sms", world.getName(), ms));
        }
    }

    public static PolarPaper getPlugin() {
        return PolarPaper.getPlugin(PolarPaper.class);
    }
    public static Logger logger() {
        return getPlugin().getLogger();
    }

    public static void registerEvents() {
        PolarPaper.getPlugin().getServer().getPluginManager().registerEvents(new PolarListener(), PolarPaper.getPlugin());
    }

}