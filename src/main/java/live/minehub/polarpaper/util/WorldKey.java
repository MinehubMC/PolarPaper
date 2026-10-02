package live.minehub.polarpaper.util;

import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.resources.Identifier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class WorldKey {

    public static String getWorldName(Path path) {
        Path worldsFolder = PolarPaper.getWorldsPath();
        return Format.stripExtension(worldsFolder.toAbsolutePath().relativize(path.toAbsolutePath()).toString())
                .replace(" ", "_")
                .toLowerCase();
    }

    /**
     * Gets a bukkit world by first trying Polar's namespace, then Minecraft's namespace
     * @param worldName The name of the world
     * @return null if no world found
     */
    public static @Nullable World getWorld(String worldName) {
        worldName = Format.stripExtension(worldName)
                .replace(" ", "_")
                .toLowerCase();

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

    public static @Nullable World getWorld(Identifier id) {
        NamespacedKey worldKey;
        World world;

        if (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            // try polar namespace
            worldKey = NamespacedKey.fromString(id.getPath(), PolarPaper.getPlugin());
            if (worldKey == null) return null;
            world = Bukkit.getWorld(worldKey);
            if (world != null) return world;
        }

        worldKey = new NamespacedKey(id.getNamespace(), id.getPath());
        world = Bukkit.getWorld(worldKey);
        return world;
    }

    public static boolean isOutsideWorldsFolder(Path path) {
        Path worldsFolder = PolarPaper.getWorldsPath();
        return !path.normalize().startsWith(worldsFolder);
    }

    public static Path validatePath(CommandSender sender, String userPath) {
        Path worldsFolder = PolarPaper.getWorldsPath();
        Path path;
        try {
            path = worldsFolder.resolve(userPath);
        } catch (InvalidPathException _) {
            sender.sendMessage(Component.text("Invalid path", NamedTextColor.RED));
            return null;
        }

        if (isOutsideWorldsFolder(path)) {
            sender.sendMessage(Component.text("Outside of worlds folder", NamedTextColor.RED));
            return null;
        }

        return path;
    }

}
