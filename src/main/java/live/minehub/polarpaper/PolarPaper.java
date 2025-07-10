package live.minehub.polarpaper;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import live.minehub.polarpaper.commands.PolarCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static PolarPaper getPlugin() {
        return PolarPaper.getPlugin(PolarPaper.class);
    }

    public static void registerEvents() {
        PolarPaper.getPlugin().getServer().getPluginManager().registerEvents(new PolarListener(), PolarPaper.getPlugin());
    }


}