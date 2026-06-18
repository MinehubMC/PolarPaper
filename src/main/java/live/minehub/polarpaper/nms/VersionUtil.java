package live.minehub.polarpaper.nms;

import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.util.Versioning;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VersionUtil {

    public static CompletableFuture<@Nullable World> createNoSaveLevel(WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        Polar.setLoading(creator.key(), true);
        Plugin plugin = PolarPaper.getPlugin();
        String version = Versioning.getCurrentApiVersion();
        return switch (version) {
            default -> new live.minehub.polarpaper.paper_latest.NoSaveLevelCreatorImpl().createLevel(plugin, creator, spawnPos, difficulty, gamerules, time);
        };
    }

}
