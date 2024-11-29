package live.minehub.polarpaper;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

public record Config(
    String source,
    boolean autoSave,
    boolean loadOnStartup,
    String spawn,
    Difficulty difficulty,
    boolean allowMonsters,
    boolean allowAnimals,
    boolean pvp,
    WorldType worldType,
    World.Environment environment
) {

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());

    public static final Config DEFAULT = new Config(
            "file",
            false,
            true,
            "0, 64, 0",
            Difficulty.NORMAL,
            false,
            false,
            true,
            WorldType.FLAT,
            World.Environment.NORMAL
    );

    public Location getSpawnPos() {
        String[] split = spawn.split(",");
        try {
            if (split.length == 3) { // x y z
                String x = split[0];
                String y = split[1];
                String z = split[2];
                return new Location(null, Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z));
            } else if (split.length == 5) { // x y z yaw pitch
                String x = split[0];
                String y = split[1];
                String z = split[2];
                String yaw = split[3];
                String pitch = split[4];
                return new Location(null, Double.parseDouble(x), Double.parseDouble(y), Double.parseDouble(z), Float.parseFloat(yaw), Float.parseFloat(pitch));
            } else {
                LOGGER.warning("Failed to parse spawn pos: " + spawn);
                return new Location(null, 0, 64, 0);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to parse spawn pos: " + spawn);
            return new Location(null, 0, 64, 0);
        }
    }

    public @Nullable Config setSpawnPos(Location location) {
        return setSpawnPos(String.format("%s, %s, %s, %s, %s", location.x(), location.y(), location.z(), location.getYaw(), location.getPitch()));
    }
    public @Nullable Config setSpawnPosRounded(Location location) {
        return setSpawnPos(String.format("%s, %s, %s, %s, %s", location.blockX(), location.blockY(), location.blockZ(), Math.round(location.getYaw()), Math.round(location.getPitch())));
    }

    public @Nullable Config setSpawnPos(String string) {
        String[] split = string.split(",");
        if (split.length != 3 && split.length != 5) {
            LOGGER.warning("Failed to parse spawn pos: " + spawn);
            return null;
        }

        try {
            Double.parseDouble(split[0]);
            Double.parseDouble(split[1]);
            Double.parseDouble(split[2]);
            if (split.length == 5) {
                Float.parseFloat(split[3]);
                Float.parseFloat(split[4]);
            }

            return new Config(this.source, this.autoSave, this.loadOnStartup, string, this.difficulty, this.allowMonsters, this.allowAnimals, this.pvp, this.worldType, this.environment);
        } catch (Exception e) {
            LOGGER.warning("Failed to parse spawn pos: " + spawn);
            return null;
        }
    }

    public static @Nullable Config readFromConfig(FileConfiguration config, String worldName) {
        String prefix = String.format("worlds.%s.", worldName);

        try {
            String source = config.getString(prefix + "source", DEFAULT.source);
            boolean autoSave = config.getBoolean(prefix + "autosave", DEFAULT.autoSave);
            boolean loadOnStartup = config.getBoolean(prefix + "loadOnStartup", DEFAULT.loadOnStartup);
            String spawn = config.getString(prefix + "spawn", DEFAULT.spawn);
            Difficulty difficulty = Difficulty.valueOf(config.getString(prefix + "difficulty", DEFAULT.difficulty.name()));
            boolean allowMonsters = config.getBoolean(prefix + "allowMonsters", DEFAULT.allowMonsters);
            boolean allowAnimals = config.getBoolean(prefix + "allowAnimals", DEFAULT.allowAnimals);
            boolean pvp = config.getBoolean(prefix + "pvp", DEFAULT.pvp);
            WorldType worldType = WorldType.valueOf(config.getString(prefix + "worldType", DEFAULT.worldType.name()));
            World.Environment environment = World.Environment.valueOf(config.getString(prefix + "environment", DEFAULT.environment.name()));

            return new Config(
                    source,
                    autoSave,
                    loadOnStartup,
                    spawn,
                    difficulty,
                    allowMonsters,
                    allowAnimals,
                    pvp,
                    worldType,
                    environment
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void writeToConfig(FileConfiguration fileConfig, String worldName, Config config) {
        String prefix = String.format("worlds.%s.", worldName);

        fileConfig.set(prefix + "source", config.source);
        fileConfig.set(prefix + "autosave", config.autoSave);
        fileConfig.set(prefix + "loadOnStartup", config.loadOnStartup);
        fileConfig.set(prefix + "spawn", config.spawn);
        fileConfig.set(prefix + "difficulty", config.difficulty.name());
        fileConfig.set(prefix + "allowMonsters", config.allowMonsters);
        fileConfig.set(prefix + "allowAnimals", config.allowAnimals);
        fileConfig.set(prefix + "pvp", config.pvp);
        fileConfig.set(prefix + "worldType", config.worldType.name());
        fileConfig.setInlineComments(prefix + "worldType", List.of("One of: NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES"));
        fileConfig.set(prefix + "environment", config.environment.name());
        fileConfig.setInlineComments(prefix + "environment", List.of("One of: NORMAL, NETHER, THE_END, CUSTOM"));

        Path pluginFolder = Path.of(PaperPolar.getPlugin().getDataFolder().getAbsolutePath());
        Path configFile = pluginFolder.resolve("config.yml");
        try {
            fileConfig.save(configFile.toFile());
        } catch (IOException e) {
            LOGGER.warning("Failed to save world to config file");
            LOGGER.warning(e.toString());
        }
    }

}
