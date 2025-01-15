package live.minehub.polarpaper;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import live.minehub.polarpaper.commands.PolarCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public final class PolarPaper extends JavaPlugin {

    @Override
    public void onEnable() {
        // Paper commands
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            PolarCommand.register(commands);
        });

        getServer().getPluginManager().registerEvents(new PolarListener(), this);

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

                initWorld(worldName, getConfig());

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

    public static void initWorld(String worldName, FileConfiguration config) {
        if (Polar.isInConfig(worldName)) return;

        Config.writeToConfig(config, worldName, Config.DEFAULT);
    }

    /**
     * Initializes a world with the default config provided through the method.
     * Used when converting a world to save all its properties.
     */
    public static void initWorld(String worldName, FileConfiguration config, Config defaultConfig) {
        if (Polar.isInConfig(worldName)) return;

        Config.writeToConfig(config, worldName, defaultConfig);
    }

    @Override
    public void onDisable() {
        // Save worlds that are configured to autosave
        // TODO: Not working
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        getLogger().info("Autosaving worlds...");
//        for (World world : Bukkit.getWorlds()) {
//            PolarWorld pw = PolarWorld.fromWorld(world);
//            if (pw == null) continue;
//            PolarGenerator pg = PolarGenerator.fromWorld(world);
//            if (pg == null) continue;
//            Config config = Config.readFromConfig(getConfig(), world.getName());
//            if (config == null) config = Config.DEFAULT;
//
//            if (!config.autoSave()) continue;
//
//            getLogger().info("Autosaving polar world: " + world.getName());
//
//            CompletableFuture<Void> future = new CompletableFuture<>();
//            futures.add(future);
//
//            Polar.saveWorldConfigSource(world, pw, pg, () -> future.complete(null), () -> future.complete(null));
//        }
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//        getLogger().info("Autosaved worlds!");
    }

    public static PolarPaper getPlugin() {
        return PolarPaper.getPlugin(PolarPaper.class);
    }


}