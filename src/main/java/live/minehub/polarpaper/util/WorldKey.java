package live.minehub.polarpaper.util;

import live.minehub.polarpaper.PolarPaper;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class WorldKey {

    public static String getWorldName(Path path) {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        return worldsFolder.toAbsolutePath().relativize(path.toAbsolutePath()).toString().replaceAll(".polar$", "")
                .replace(" ", "_")
                .replace("/", "_")
                .toLowerCase();
    }

    /**
     * Gets a bukkit world by first trying Polar's namespace, then Minecraft's namespace
     * @param worldName The name of the world
     * @return null if no world found
     */
    public static @Nullable World getWorld(String worldName) {
        worldName = worldName.toLowerCase().replace(" ", "_");

        // try polar namespace
        NamespacedKey worldKey = NamespacedKey.fromString(worldName, PolarPaper.getPlugin());
        if (worldKey == null) return null;
        World world = Bukkit.getWorld(worldKey);
        if (world != null) return world;

        // try minecraft namespace
        worldKey = NamespacedKey.fromString(worldName);
        if (worldKey == null) return null;
        world = Bukkit.getWorld(worldKey);
        return world;
    }

}
