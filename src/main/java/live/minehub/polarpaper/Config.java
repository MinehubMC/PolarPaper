package live.minehub.polarpaper;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public record Config(
        @NotNull String source,
        boolean autoSave,
        boolean saveOnStop,
        boolean loadOnStartup,
        @NotNull Location spawn,
        @NotNull Difficulty difficulty,
        boolean allowMonsters,
        boolean allowAnimals,
        boolean allowWorldExpansion,
        boolean pvp,
        @NotNull WorldType worldType,
        @NotNull World.Environment environment,
        @NotNull List<Map<String, ?>> gamerules
) {

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());

    public static final Config DEFAULT = new Config(
            "file",
            false,
            false,
            true,
            new Location(null, 0, 64, 0),
            Difficulty.NORMAL,
            true,
            true,
            true,
            true,
            WorldType.NORMAL,
            World.Environment.NORMAL,
            List.of(
                    Map.of("doMobSpawning", false),
                    Map.of("doFireTick", false),
                    Map.of("randomTickSpeed", 0),
                    Map.of("mobGriefing", false),
                    Map.of("doVinesSpread", false)
            )
    );

    public @NotNull String spawnString() {
        return locationToString(spawn());
    }

    public @NotNull Config withSpawnPos(Location location) {
        return new Config(this.source, this.autoSave, this.saveOnStop, this.loadOnStartup, location, this.difficulty, this.allowMonsters, this.allowAnimals, this.allowWorldExpansion, this.pvp, this.worldType, this.environment, this.gamerules);
    }

    public static @Nullable Config readFromConfig(FileConfiguration config, String worldName) {
        String prefix = String.format("worlds.%s.", worldName);

        try {
            String source = config.getString(prefix + "source", DEFAULT.source);
            boolean autoSave = config.getBoolean(prefix + "autosave", DEFAULT.autoSave);
            boolean saveOnStop = config.getBoolean(prefix + "saveOnStop", DEFAULT.saveOnStop);
            boolean loadOnStartup = config.getBoolean(prefix + "loadOnStartup", DEFAULT.loadOnStartup);
            String spawn = config.getString(prefix + "spawn", locationToString(DEFAULT.spawn));
            Difficulty difficulty = Difficulty.valueOf(config.getString(prefix + "difficulty", DEFAULT.difficulty.name()));
            boolean allowMonsters = config.getBoolean(prefix + "allowMonsters", DEFAULT.allowMonsters);
            boolean allowAnimals = config.getBoolean(prefix + "allowAnimals", DEFAULT.allowAnimals);
            boolean allowWorldExpansion = config.getBoolean(prefix + "allowWorldExpansion", DEFAULT.allowWorldExpansion);
            boolean pvp = config.getBoolean(prefix + "pvp", DEFAULT.pvp);
            WorldType worldType = WorldType.valueOf(config.getString(prefix + "worldType", DEFAULT.worldType.name()));
            World.Environment environment = World.Environment.valueOf(config.getString(prefix + "environment", DEFAULT.environment.name()));


            List<Map<?, ?>> gamerules = config.getMapList(prefix + "gamerules");
            List<Map<String, ?>> gamerulesList = new ArrayList<>();
            for (Map<?, ?> gamerule : gamerules) {
                for (Map.Entry<?, ?> entry : gamerule.entrySet()) {
                    gamerulesList.add(Map.of((String)entry.getKey(), entry.getValue()));
                }
            }
            if (gamerules.isEmpty()) gamerulesList.addAll(DEFAULT.gamerules);


            return new Config(
                    source,
                    autoSave,
                    saveOnStop,
                    loadOnStartup,
                    stringToLocation(spawn),
                    difficulty,
                    allowMonsters,
                    allowAnimals,
                    allowWorldExpansion,
                    pvp,
                    worldType,
                    environment,
                    gamerulesList
            );
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Failed to read config");
            LOGGER.log(Level.INFO, e.getMessage(), e);
            return null;
        }
    }

    public static void writeToConfig(FileConfiguration fileConfig, String worldName, Config config) {
        String prefix = String.format("worlds.%s.", worldName);

        fileConfig.set(prefix + "source", config.source);
        fileConfig.set(prefix + "autosave", config.autoSave);
        fileConfig.set(prefix + "saveOnStop", config.saveOnStop);
        fileConfig.set(prefix + "loadOnStartup", config.loadOnStartup);
        fileConfig.set(prefix + "spawn", locationToString(config.spawn));
        fileConfig.set(prefix + "difficulty", config.difficulty.name());
        fileConfig.set(prefix + "allowMonsters", config.allowMonsters);
        fileConfig.set(prefix + "allowAnimals", config.allowAnimals);
        fileConfig.set(prefix + "allowWorldExpansion", config.allowWorldExpansion);
        fileConfig.setInlineComments(prefix + "allowWorldExpansion", List.of("Whether the world can grow and load more chunks"));
        fileConfig.set(prefix + "pvp", config.pvp);
        fileConfig.set(prefix + "worldType", config.worldType.name());
        fileConfig.setInlineComments(prefix + "worldType", List.of("One of: NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES"));
        fileConfig.set(prefix + "environment", config.environment.name());
        fileConfig.setInlineComments(prefix + "environment", List.of("One of: NORMAL, NETHER, THE_END, CUSTOM"));
        fileConfig.set(prefix + "gamerules", config.gamerules);

        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path configFile = pluginFolder.resolve("config.yml");
        try {
            fileConfig.save(configFile.toFile());
        } catch (IOException e) {
            LOGGER.warning("Failed to save world to config file");
            LOGGER.warning(e.toString());
        }
    }

    private static String locationToString(Location spawn) {
        return String.format("%s, %s, %s, %s, %s",
                spawn.x(),
                spawn.y(),
                spawn.z(),
                spawn.getYaw(),
                spawn.getPitch());
    }

    private static Location stringToLocation(String string) {
        String[] split = string.split(",");
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
                LOGGER.warning("Failed to parse spawn pos: " + string);
                return DEFAULT.spawn;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to parse spawn pos: " + string);
            return DEFAULT.spawn;
        }
    }

}
