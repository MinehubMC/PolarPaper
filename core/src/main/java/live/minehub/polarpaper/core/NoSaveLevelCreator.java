package live.minehub.polarpaper.core;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface NoSaveLevelCreator {
    Logger LOGGER = LoggerFactory.getLogger(NoSaveLevelCreator.class);

    CompletableFuture<@Nullable World> createLevel(Plugin plugin, WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time);

}
