package live.minehub.polarpaper;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import live.minehub.polarpaper.commands.CommandManager;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.source.FilePolarSource;
import live.minehub.polarpaper.util.ExceptionUtil;
import live.minehub.polarpaper.util.WorldKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class PolarPaper extends JavaPlugin {

    @Override
    public void onEnable() {
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

                logger().info("Loading polar world: " + worldName);

                Polar.createWorld(new FilePolarSource(path), worldName);
            });
        } catch (IOException e) {
            logger().warning("Failed to load world on startup");
            ExceptionUtil.log(e);
        }
    }

    @Override
    public void onDisable() {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();
        Path tempFolder = craftServer.getServer().storageSource.levelDirectory.path().resolve("dimensions").resolve(namespace());
        if (Files.exists(tempFolder)) {
            logger().info("Clearing temp directory");
            try (Stream<Path> paths = Files.walk(tempFolder)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            } catch (IOException e) {
                logger().warning("Failed to delete temp directory");
                ExceptionUtil.log(e);
            }
        }

        for (World world : getServer().getWorlds()) {
            PolarGenerator generator = PolarGenerator.fromWorld(world);
            if (generator == null) continue;

            if (!generator.getConfig().saveOnStop()) {
                logger().info(String.format("Not saving '%s' as it has save on stop disabled", world.getKey().getKey()));
                continue;
            }
            if (Polar.isLoading(world)) {
                logger().info(String.format("Not saving '%s' as it was not fully loaded", world.getKey().getKey()));
                continue;
            }

            logger().info("Saving '" + world.getKey().getKey() + "'...");

            long before = System.nanoTime();
            Polar.updateConfig(world, world.getKey().getKey());
            Polar.saveWorld(world);
            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            logger().info(String.format("Saved '%s' in %sms", world.getKey().getKey(), ms));
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