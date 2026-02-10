package live.minehub.polarpaper;

import live.minehub.polarpaper.util.CompressionType;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @see Config#getDefaultConfig(FileConfiguration)
 * @see Config#BLANK_DEFAULT
 * @see Config#toBuilder()
 * @param autoSaveIntervalTicks the time between each autosave in ticks (20 ticks = 1 second), -1 to disable autosaving
 * @param time the daytime of the world
 * @param saveOnStop whether to save on shutdown or when using /polar unload
 * @param loadOnStartup whether to load the world when the plugin is enabled
 * @param spawn the spawn location
 * @param difficulty the difficulty
 * @param async whether to create the world asynchronously. Can cause issues with other plugins
 * @param removeChunks whether chunks are removed from the PolarWorld once fully generated to save memory.
 * Should be disabled if reusing the PolarWorld object between multiple worlds
 * @param worldType
 * @param environment
 * @param gamerules map of gamerules - custom rules: liquidPhysics, blockPhysics, blockGravity, coralDeath
 */
public record Config(
        int autoSaveIntervalTicks,
        long time,
        boolean saveOnStop,
        boolean loadOnStartup,
        @NotNull Location spawn,
        @NotNull Difficulty difficulty,
        boolean async,
        CompressionType compression,
        @NotNull WorldType worldType,
        @NotNull World.Environment environment,
        @NotNull Map<String, Object> gamerules
) {

    public static final Map<String, Object> DEFAULT_GAMERULES = new HashMap<>() {{
        put("spawn_mobs", false);
        put("fire_spread_radius_around_player", 0);
        put("random_tick_speed", 0);
        put("mob_griefing", false);
        put("spread_vines", false);
        put("tnt_explodes", false);
        put("coralDeath", false); // custom gamerule
        put("blockPhysics", true); // custom gamerule
        put("blockGravity", true); // custom gamerule
        put("liquidPhysics", true); // custom gamerule

        // paper default gamerules, put here to remove clutter when saving other worlds
        put("max_command_sequence_length", 65536);
        put("max_block_modifications", 32768);
        put("max_command_forks", 65536);
    }};

    public static final Config BLANK_DEFAULT = new Config(
            -1,
            1000L,
            false,
            true,
            new Location(null, 0, 64, 0),
            Difficulty.NORMAL,
            false,
            CompressionType.ZSTD,
            WorldType.NORMAL,
            World.Environment.NORMAL,
            DEFAULT_GAMERULES
    );

    public static boolean isInConfig(@NotNull String worldName) {
        return PolarPaper.getPlugin().getConfig().isSet("worlds." + worldName);
    }

    public static Config getDefaultConfig(FileConfiguration config) {
        return readPrefix(config, "default.", BLANK_DEFAULT);
    }

    public static @NotNull Config updateConfigWithWorld(Config config, World world) {
        Config.Builder configBuilder = config.toBuilder()
                .time(world.getTime())
                .spawn(world.getSpawnLocation())
                .difficulty(world.getDifficulty())
                .environment(world.getEnvironment());

        for (String name : world.getGameRules()) {
            GameRule<?> gamerule = Registry.GAME_RULE.get(Key.key("minecraft", name));
            if (gamerule == null) {
                PolarPaper.logger().warning("Invalid gamerule: " + name);
                continue;
            }

            Object gameRuleValue = world.getGameRuleValue(gamerule);
            Object gameRuleDefault = world.getGameRuleDefault(gamerule);
            if (gameRuleValue != gameRuleDefault) {
                configBuilder.gamerule(name, gameRuleValue);
            } else {
                configBuilder.removeGamerule(name);
            }
        }

        return configBuilder.build();
    }

    public @NotNull String spawnString() {
        return locationToString(spawn());
    }

    public static @NotNull Config readFromConfig(FileConfiguration config, World world) {
        return updateConfigWithWorld(readFromConfig(config, world.getName(), getDefaultConfig(config)), world);
    }

    public static @NotNull Config readFromConfig(FileConfiguration config, String worldName) {
        return readFromConfig(config, worldName, getDefaultConfig(config));
    }

    public static @NotNull Config readFromConfig(FileConfiguration config, String worldName, Config defaultConfig) {
        return readPrefix(config, String.format("worlds.%s.", worldName), defaultConfig);
    }

    private static @NotNull Config readPrefix(FileConfiguration config, String prefix, Config defaultConfig) {
        try {
            int autoSaveIntervalTicks = config.getInt(prefix + "autosaveIntervalTicks", defaultConfig.autoSaveIntervalTicks);
            long time = config.getLong(prefix + "time", defaultConfig.time);
            boolean saveOnStop = config.getBoolean(prefix + "saveOnStop", defaultConfig.saveOnStop);
            boolean loadOnStartup = config.getBoolean(prefix + "loadOnStartup", defaultConfig.loadOnStartup);
            String spawn = config.getString(prefix + "spawn", locationToString(defaultConfig.spawn));
            Difficulty difficulty = Difficulty.valueOf(config.getString(prefix + "difficulty", defaultConfig.difficulty.name()));
            boolean async = config.getBoolean(prefix + "async", defaultConfig.async);
            CompressionType compression = CompressionType.valueOf(config.getString(prefix + "compression", defaultConfig.compression.name()));
            WorldType worldType = WorldType.valueOf(config.getString(prefix + "worldType", defaultConfig.worldType.name()));
            World.Environment environment = World.Environment.valueOf(config.getString(prefix + "environment", defaultConfig.environment.name()));

            List<Map<?, ?>> gamerules = config.getMapList(prefix + "gamerules");

            Map<String, Object> gamerulesMap = new HashMap<>();
            Map<String, Object> convertedGamerules = Config.convertYmlGamerules(gamerules);

            gamerulesMap.putAll(defaultConfig.gamerules());
            gamerulesMap.putAll(convertedGamerules);

            return new Config(
                    autoSaveIntervalTicks,
                    time,
                    saveOnStop,
                    loadOnStartup,
                    stringToLocation(spawn),
                    difficulty,
                    async,
                    compression,
                    worldType,
                    environment,
                    gamerulesMap
            );
        } catch (IllegalArgumentException e) {
            PolarPaper.logger().warning("Failed to read config, using defaults");
            ExceptionUtil.log(e);
            return defaultConfig;
        }
    }

    private static void writeProperty(FileConfiguration fileConfig, String path, Object value, Object def) {
        if (value.equals(def)) fileConfig.set(path, null);
        else fileConfig.set(path, value);
    }

    public static void writeToConfig(FileConfiguration fileConfig, String worldName, Config config) {
        Config defaultConfig = getDefaultConfig(fileConfig);

        String prefix = String.format("worlds.%s.", worldName);

        // only save if the config differs from the default
        writeProperty(fileConfig, prefix + "time", config.time, defaultConfig.time);
        writeProperty(fileConfig, prefix + "autosaveIntervalTicks", config.autoSaveIntervalTicks, defaultConfig.autoSaveIntervalTicks);
        fileConfig.setInlineComments(prefix + "autosaveIntervalTicks", List.of("-1 to disable"));
        writeProperty(fileConfig, prefix + "saveOnStop", config.saveOnStop, defaultConfig.saveOnStop);
        writeProperty(fileConfig, prefix + "loadOnStartup", config.loadOnStartup, defaultConfig.loadOnStartup);
        writeProperty(fileConfig, prefix + "spawn", locationToString(config.spawn), locationToString(defaultConfig.spawn));
        writeProperty(fileConfig, prefix + "difficulty", config.difficulty.name(), defaultConfig.difficulty.name());
        writeProperty(fileConfig, prefix + "async", config.async, defaultConfig.async);
        fileConfig.setInlineComments(prefix + "async", List.of("Very experimental"));
        writeProperty(fileConfig, prefix + "compression", config.compression, defaultConfig.compression);
        fileConfig.setInlineComments(prefix + "compression", List.of("One of: ZSTD, NONE"));
        writeProperty(fileConfig, prefix + "worldType", config.worldType.name(), defaultConfig.worldType.name());
        fileConfig.setInlineComments(prefix + "worldType", List.of("One of: NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES"));
        writeProperty(fileConfig, prefix + "environment", config.environment.name(), defaultConfig.environment.name());
        fileConfig.setInlineComments(prefix + "environment", List.of("One of: NORMAL, NETHER, THE_END, CUSTOM"));

        var gamerulesToSave = config.gamerulesList();
        gamerulesToSave.removeAll(defaultConfig.gamerulesList());
        if (gamerulesToSave.isEmpty()) gamerulesToSave = null;
        fileConfig.set(prefix + "gamerules", gamerulesToSave);

        fileConfig.setInlineComments(prefix + "gamerules", List.of("Custom rules: liquidPhysics, blockPhysics, blockGravity, coralDeath"));

        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path configFile = pluginFolder.resolve("config.yml");
        try {
            fileConfig.save(configFile.toFile());
        } catch (IOException e) {
            PolarPaper.logger().warning("Failed to save world to config file");
            ExceptionUtil.log(e);
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
                PolarPaper.logger().warning("Failed to parse spawn pos: " + string);
                return BLANK_DEFAULT.spawn;
            }
        } catch (Exception e) {
            PolarPaper.logger().warning("Failed to parse spawn pos: " + string);
            return BLANK_DEFAULT.spawn;
        }
    }

    public @NotNull List<Map<String, ?>> gamerulesList() {
        List<Map<String, ?>> gamerules = new ArrayList<>();
        for (Map.Entry<String, Object> entry : gamerules().entrySet()) {
            gamerules.add(Map.of(entry.getKey(), entry.getValue()));
        }
        return gamerules;
    }

    public static @NotNull Map<String, Object> convertYmlGamerules(List<Map<?, ?>> ymlGamerules) {
        Map<String, Object> gamerules = new HashMap<>();
        for (Map<?, ?> ymlGamerule : ymlGamerules) {
            for (Map.Entry<?, ?> entry : ymlGamerule.entrySet()) {
                if (!(entry.getKey() instanceof String key)) continue;

                gamerules.put(key, entry.getValue());
            }
        }
        return gamerules;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @SuppressWarnings("unused")
    public static final class Builder {
        private int autoSaveIntervalTicks;
        private long time;
        private boolean saveOnStop;
        private boolean loadOnStartup;
        private @NotNull Location spawn;
        private @NotNull Difficulty difficulty;
        private boolean async;
        private CompressionType compression;
        private @NotNull WorldType worldType;
        private @NotNull World.Environment environment;
        private @NotNull Map<String, Object> gamerules;

        private Builder(Config record) {
            this.autoSaveIntervalTicks = record.autoSaveIntervalTicks;
            this.time = record.time;
            this.saveOnStop = record.saveOnStop;
            this.loadOnStartup = record.loadOnStartup;
            this.spawn = record.spawn;
            this.difficulty = record.difficulty;
            this.async = record.async;
            this.compression = record.compression;
            this.worldType = record.worldType;
            this.environment = record.environment;
            this.gamerules = record.gamerules;
        }

        /**
         * The time between each autosave in ticks (20 ticks = 1 second)
         * <p>
         * -1 to disable autosaving
         */
        public Builder autoSaveIntervalTicks(int autoSaveIntervalTicks) {
            this.autoSaveIntervalTicks = autoSaveIntervalTicks;
            return this;
        }

        public Builder time(long time) {
            this.time = time;
            return this;
        }

        /**
         * Whether to save on shutdown or when using /polar unload
         */
        public Builder saveOnStop(boolean saveOnStop) {
            this.saveOnStop = saveOnStop;
            return this;
        }

        /**
         * Whether to load the world when the plugin is enabled
         */
        public Builder loadOnStartup(boolean loadOnStartup) {
            this.loadOnStartup = loadOnStartup;
            return this;
        }

        public Builder spawn(@NotNull Location spawn) {
            this.spawn = Objects.requireNonNull(spawn, "Null spawn");
            return this;
        }

        public Builder difficulty(@NotNull Difficulty difficulty) {
            this.difficulty = Objects.requireNonNull(difficulty, "Null difficulty");
            return this;
        }

        /**
         * Whether to create the world asynchronously.
         * Can cause issues with other plugins
         */
        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder compression(CompressionType compression) {
            this.compression = compression;
            return this;
        }

        public Builder worldType(@NotNull WorldType worldType) {
            this.worldType = Objects.requireNonNull(worldType, "Null worldType");
            return this;
        }

        public Builder environment(@NotNull World.Environment environment) {
            this.environment = Objects.requireNonNull(environment, "Null environment");
            return this;
        }

        public Builder gamerules(@NotNull Map<String, Object> gamerules) {
            this.gamerules = gamerules;
            return this;
        }

        public Builder gamerule(@NotNull String gameruleKey, @Nullable Object gameruleValue) {
            this.gamerules.put(gameruleKey, gameruleValue);
            return this;
        }

        public Builder removeGamerule(@NotNull String gameruleKey) {
            this.gamerules.remove(gameruleKey);
            return this;
        }

        public Config build() {
            return new Config(this.autoSaveIntervalTicks, this.time, this.saveOnStop, this.loadOnStartup,
                    this.spawn, this.difficulty, this.async, this.compression, this.worldType,
                    this.environment, this.gamerules);
        }
    }
}
