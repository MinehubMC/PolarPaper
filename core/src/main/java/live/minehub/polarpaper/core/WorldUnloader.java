package live.minehub.polarpaper.core;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public interface WorldUnloader {
    Logger LOGGER = LoggerFactory.getLogger(WorldUnloader.class);

    CompletableFuture<Boolean> unloadWorld(Plugin plugin, World world);
}
