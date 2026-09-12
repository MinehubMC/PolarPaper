package live.minehub.polarpaper.core.config;

import dev.hallock.zstd.Zstd;
import live.minehub.polarpaper.core.world.PolarConstants;
import live.minehub.polarpaper.core.world.PolarWorld;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @see Config#getDefaultConfig(FileConfiguration) 
 * @see Builder#defaults()
 * @see Config#toBuilder() 
 * @param autoSaveIntervalTicks the time between each autosave in ticks (20 ticks = 1 second), -1 to disable autosaving
 * @param announceAutosave whether to send autosave messages regardless of "polar.notifications" permission
 * @param time the daytime of the world
 * @param saveOnStop whether to save on shutdown or when using /polar unload
 * @param loadOnStartup whether to load the world when the plugin is enabled
 * @param spawn the spawn location
 * @param difficulty the difficulty
 * @param async whether to create the world asynchronously. Can cause issues with other plugins
 * @param saveLight whether chunks are saved with light data.
 * Reduces CPU usage when loading the world but increases world size significantly
 * @param worldType Prefer WorldType.FLAT if possible as it skips unnecessary vanilla biome generation
 * @param environment
 * @param gamerules map of gamerules - custom rules: liquidPhysics, blockPhysics, blockGravity, blockFade
 */
public record Config(
        int autoSaveIntervalTicks,
        boolean announceAutosave,
        long time,
        boolean saveOnStop,
        boolean loadOnStartup,
        @NotNull Location spawn,
        @NotNull Difficulty difficulty,
        boolean async,
        boolean saveLight,
        PolarWorld.CompressionType compression,
        int compressionLevel,
        @NotNull WorldType worldType,
        @NotNull World.Environment environment,
        @NotNull Map<String, Object> gamerules
) {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    private static final Map<String, Object> DEFAULT_GAMERULES = new HashMap<>() {{
        put("spawn_mobs", false);
        put("fire_spread_radius_around_player", 0);
        put("random_tick_speed", 0);
        put("mob_griefing", false);
        put("spread_vines", false);
        put("tnt_explodes", false);
        put("blockFade", false); // custom gamerule
        put("blockPhysics", true); // custom gamerule
        put("blockGravity", true); // custom gamerule
        put("liquidPhysics", true); // custom gamerule

        // paper default gamerules, put here to remove clutter when saving other worlds
        put("max_command_sequence_length", 65536);
        put("max_block_modifications", 32768);
        put("max_command_forks", 65536);
    }};

    private static final List<Property<?>> PROPERTIES = List.of(
            new Property<>("autosaveIntervalTicks", -1, Config::autoSaveIntervalTicks, Builder::autoSaveIntervalTicks, "-1 to disable"),
            new Property<>("announceAutosave", true, Config::announceAutosave, Builder::announceAutosave),
            new Property<Number>("time", 1000L, Config::time, (b, n) -> b.time(n.longValue())),
            new Property<>("saveOnStop", false, Config::saveOnStop, Builder::saveOnStop),
            new Property<>("loadOnStartup", true, Config::loadOnStartup, Builder::loadOnStartup),
            new LocationProperty("spawn", new Location(null, 0, 64, 0), Config::spawn, Builder::spawn),
            new EnumProperty<>(Difficulty.class, "difficulty", Difficulty.NORMAL, Config::difficulty, Builder::difficulty),
            new Property<>("async", false, Config::async, Builder::async, "Experimental, may cause issues with other plugins"),
            new Property<>("saveLight", true, Config::saveLight, Builder::saveLight, "Whether chunks are saved with light data. Reduces load time and CPU usage while loading but increases world size"),
            new EnumProperty<>(PolarWorld.CompressionType.class, "compression", PolarConstants.DEFAULT_COMPRESSION, Config::compression, Builder::compression),
            new Property<>("compressionLevel", PolarConstants.DEFAULT_COMPRESSION_LEVEL, Config::compressionLevel, Builder::compressionLevel, "ZSTD compression level, higher means smaller file size but longer save times. Max %s, default %s".formatted(Zstd.zstd().maxCompressionLevel(), PolarConstants.DEFAULT_COMPRESSION_LEVEL)),
            new EnumProperty<>(WorldType.class, "worldType", WorldType.FLAT, Config::worldType, Builder::worldType),
            new EnumProperty<>(World.Environment.class, "environment", World.Environment.NORMAL, Config::environment, Builder::environment),
            new GamerulesProperty("gamerules", DEFAULT_GAMERULES, Config::gamerules, (b, m) -> {
                for (Map.Entry<String, Object> entry : m.entrySet()) { // add instead of replacing gamerules
                    b.gamerule(entry.getKey(), entry.getValue());
                }
                return b;
            }, "Custom rules: liquidPhysics, blockPhysics, blockGravity, blockFade")
    );

    public static Map<String, Object> getDefaultGamerules() {
        return DEFAULT_GAMERULES;
    }

    public static boolean isInConfig(FileConfiguration config, @NotNull String worldName) {
        return config.isSet("worlds." + worldName);
    }

    public static Config getDefaultConfig(FileConfiguration config) {
        return readPrefix(config, "default.", Builder.defaults());
    }

    public @NotNull String spawnString() {
        return LocationProperty.locationToString(spawn());
    }

    public static @NotNull Config readFromConfig(FileConfiguration config, World world) {
        Builder builder = getDefaultConfig(config).toBuilder().fromWorld(world);
        return readFromConfig(config, world.getKey().getKey(), builder);
    }

    public static @NotNull Config readFromConfig(FileConfiguration config, String worldName) {
        return readFromConfig(config, worldName, getDefaultConfig(config).toBuilder());
    }

    public static @NotNull Config readFromConfig(FileConfiguration config, String worldName, Builder builder) {
        return readPrefix(config, String.format("worlds.%s.", worldName), builder);
    }

    private static @NotNull Config readPrefix(FileConfiguration config, String prefix, Builder builder) {
        for (Property<?> property : PROPERTIES) {
            property.apply(builder, config, prefix);
        }
        return builder.build();
    }

    public static void writeToConfig(Path configPath, FileConfiguration fileConfig, String worldName, Config config) {
        Config defaultConfig = getDefaultConfig(fileConfig);

        String prefix = String.format("worlds.%s.", worldName);

        for (Property<?> property : PROPERTIES) {
            property.write(fileConfig, prefix, config, defaultConfig);
        }

        try {
            fileConfig.save(configPath.toFile());
        } catch (IOException e) {
            LOGGER.error("Failed to save world to config file", e);
        }
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @SuppressWarnings("unused")
    public static final class Builder {
        private int autoSaveIntervalTicks;
        private boolean announceAutosave;
        private long time;
        private boolean saveOnStop;
        private boolean loadOnStartup;
        private @NotNull Location spawn;
        private @NotNull Difficulty difficulty;
        private boolean async;
        private boolean saveLight;
        private PolarWorld.CompressionType compression;
        private int compressionLevel;
        private @NotNull WorldType worldType;
        private @NotNull World.Environment environment;
        private @NotNull Map<String, Object> gamerules = new HashMap<>();

        private Builder() {

        }

        private Builder(Config record) {
            this.autoSaveIntervalTicks = record.autoSaveIntervalTicks;
            this.announceAutosave = record.announceAutosave;
            this.time = record.time;
            this.saveOnStop = record.saveOnStop;
            this.loadOnStartup = record.loadOnStartup;
            this.spawn = record.spawn;
            this.difficulty = record.difficulty;
            this.async = record.async;
            this.saveLight = record.saveLight;
            this.compression = record.compression;
            this.compressionLevel = record.compressionLevel;
            this.worldType = record.worldType;
            this.environment = record.environment;
            this.gamerules = record.gamerules;
        }

        public Builder fromWorld(World world) {
            this.time(world.getTime())
                    .spawn(world.getSpawnLocation())
                    .difficulty(world.getDifficulty())
                    .environment(world.getEnvironment());

            for (String name : world.getGameRules()) {
                GameRule<?> gamerule = Registry.GAME_RULE.get(Key.key("minecraft", name));
                if (gamerule == null) {
                    LOGGER.warn("Invalid gamerule: {}", name);
                    continue;
                }

                Object gameRuleValue = world.getGameRuleValue(gamerule);
                Object gameRuleDefault = world.getGameRuleDefault(gamerule); // 1.21.11 depends on this deprecated method
                if (gameRuleValue != gameRuleDefault) {
                    gamerule(name, gameRuleValue);
                } else {
                    removeGamerule(name);
                }
            }

            return this;
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

        public Builder announceAutosave(boolean announceAutosave) {
            this.announceAutosave = announceAutosave;
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

        /**
         * Whether chunks are saved with light data.
         * Reduces CPU usage when loading the world but increases world size significantly
         */
        public Builder saveLight(boolean saveLight) {
            this.saveLight = saveLight;
            return this;
        }

        public Builder compression(PolarWorld.CompressionType compression) {
            this.compression = compression;
            return this;
        }

        /**
         * Set the ZSTD compression level, higher means smaller file size but longer save times.
         * @see Zstd#maxCompressionLevel()
         * @see Zstd#minCompressionLevel()
         * @see Zstd#defaultCompressionLevel()
         */
        public Builder compressionLevel(int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        /**
         * Prefer WorldType.FLAT if possible as it skips unnecessary vanilla biome generation
         */
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
            return new Config(this.autoSaveIntervalTicks, this.announceAutosave, this.time, this.saveOnStop, this.loadOnStartup,
                    this.spawn, this.difficulty, this.async, this.saveLight, this.compression, this.compressionLevel,
                    this.worldType, this.environment, this.gamerules);
        }

        public static Builder defaults() {
            Builder builder = new Builder();
            for (Property<?> property : PROPERTIES) {
                property.applyDefault(builder);
            }
            return builder;
        }
    }

    private static class Property<T> {
        private final @NotNull String path;
        private final @NotNull T defaultValue;
        private final @NotNull Function<Config, T> getter;
        private final @NotNull BiFunction<Builder, T, Builder> writer;
        private final @NotNull List<String> comments;
        public Property(@NotNull String path,
                        @NotNull T defaultValue,
                        @NotNull Function<Config, T> getter,
                        @NotNull BiFunction<Builder, T, Builder> writer,
                        @NotNull List<String> comments) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.getter = getter;
            this.writer = writer;
            this.comments = comments;
        }

        public Property(@NotNull String path,
                        @NotNull T defaultValue,
                        @NotNull Function<Config, T> getter,
                        @NotNull BiFunction<Builder, T, Builder> writer) {
            this(path, defaultValue, getter, writer, List.of());
        }

        public Property(@NotNull String path,
                        @NotNull T defaultValue,
                        @NotNull Function<Config, T> getter,
                        @NotNull BiFunction<Builder, T, Builder> writer,
                        @NotNull String comment) {
            this(path, defaultValue, getter, writer, List.of(comment));
        }

        public T get(Config config) {
            return getter.apply(config);
        }

        public void applyDefault(Builder builder) {
            writer.apply(builder, defaultValue);
        }

        public void apply(Builder builder, FileConfiguration config, String prefix) {
            T value = read(config, prefix);
            if (value == null) return;
            writer.apply(builder, value);
        }

        public T read(FileConfiguration config, String prefix) {
            String fullPath = prefix + path;
            return (T) config.get(fullPath);
        }

        public void write(FileConfiguration fileConfig, String prefix, Config config, Config defaultConfig) {
            T value = get(config);
            T defaultValue = get(defaultConfig);
            String fullPath = prefix + path;
            // only save if the config differs from the default
            if (value.equals(defaultValue)) fileConfig.set(fullPath, null);
            else fileConfig.set(fullPath, value);
            if (!comments.isEmpty()) fileConfig.setInlineComments(fullPath, comments);
        }

        public @NonNull String getPath() {
            return path;
        }

        public T getDefault() {
            return defaultValue;
        }

        public @NonNull List<String> getComments() {
            return comments;
        }
    }

    private static class EnumProperty<T extends Enum<T>> extends Property<T> {
        private final Class<T> enumClass;

        public EnumProperty(Class<T> enumClass, String path, T defaultValue, Function<Config, T> getter, BiFunction<Builder, T, Builder> writer, List<String> comments) {
            this.enumClass = enumClass;
            super(path, defaultValue, getter, writer, comments);
        }

        public EnumProperty(Class<T> enumClass, String path, T defaultValue, Function<Config, T> getter, BiFunction<Builder, T, Builder> writer) {
            this(enumClass, path, defaultValue, getter, writer, List.of());
        }

        public EnumProperty(Class<T> enumClass, String path, T defaultValue, Function<Config, T> getter, BiFunction<Builder, T, Builder> writer, String comment) {
            this(enumClass, path, defaultValue, getter, writer, List.of(comment));
        }

        @Override
        public T read(FileConfiguration config, String prefix) {
            String fullPath = prefix + getPath();

            String string = config.getString(fullPath);
            if (string == null) return null;
            try {
                return T.valueOf(enumClass, string);
            } catch (IllegalArgumentException _) {
                LOGGER.warn("Failed to read enum value: {}", string);
                return getDefault();
            }
        }

        @Override
        public void write(FileConfiguration fileConfig, String prefix, Config config, Config defaultConfig) {
            T value = get(config);
            T defaultValue = get(defaultConfig);
            String fullPath = prefix + getPath();
            // only save if the config differs from the default
            if (value.equals(defaultValue)) fileConfig.set(fullPath, null);
            else fileConfig.set(fullPath, value.name());

            // generate a string based on all the enum values
            StringBuilder commentBuilder = new StringBuilder();
            commentBuilder.append("One of: ");
            T[] enums = enumClass.getEnumConstants();
            boolean first = true;
            for (T anEnum : enums) {
                if (!first) {
                    commentBuilder.append(", ");
                } else first = false;
                commentBuilder.append(anEnum.name());
            }

            fileConfig.setInlineComments(fullPath, List.of(commentBuilder.toString()));
        }
    }

    private static class LocationProperty extends Property<Location> {
        public LocationProperty(String path, Location defaultValue, Function<Config, Location> getter, BiFunction<Builder, Location, Builder> writer, List<String> comments) {
            super(path, defaultValue, getter, writer, comments);
        }

        public LocationProperty(String path, Location defaultValue, Function<Config, Location> getter, BiFunction<Builder, Location, Builder> writer) {
            this(path, defaultValue, getter, writer, List.of());
        }

        public LocationProperty(String path, Location defaultValue, Function<Config, Location> getter, BiFunction<Builder, Location, Builder> writer, String comment) {
            this(path, defaultValue, getter, writer, List.of(comment));
        }

        @Override
        public Location read(FileConfiguration config, String prefix) {
            String fullPath = prefix + getPath();
            return stringToLocation(config.getString(fullPath));
        }

        @Override
        public void write(FileConfiguration fileConfig, String prefix, Config config, Config defaultConfig) {
            Location value = get(config);
            Location defaultValue = get(defaultConfig);
            String fullPath = prefix + getPath();
            // only save if the config differs from the default
            if (value.equals(defaultValue)) fileConfig.set(fullPath, null);
            else {
                fileConfig.set(fullPath, locationToString(value));
            }
            if (!getComments().isEmpty()) fileConfig.setInlineComments(fullPath, getComments());
        }

        protected static String locationToString(Location spawn) {
            return String.format("%s, %s, %s, %s, %s",
                    spawn.x(),
                    spawn.y(),
                    spawn.z(),
                    spawn.getYaw(),
                    spawn.getPitch());
        }

        private Location stringToLocation(String string) {
            if (string == null) return getDefault();

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
                    LOGGER.warn("Failed to parse spawn pos: {}", string);
                    return getDefault();
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse spawn pos: {}", string);
                return getDefault();
            }
        }
    }

    private static class GamerulesProperty extends Property<Map<String, Object>> {
        public GamerulesProperty(String path, Map<String, Object> defaultValue, Function<Config, Map<String, Object>> getter, BiFunction<Builder, Map<String, Object>, Builder> writer, List<String> comments) {
            super(path, defaultValue, getter, writer, comments);
        }

        public GamerulesProperty(String path, Map<String, Object> defaultValue, Function<Config, Map<String, Object>> getter, BiFunction<Builder, Map<String, Object>, Builder> writer) {
            this(path, defaultValue, getter, writer, List.of());
        }

        public GamerulesProperty(String path, Map<String, Object> defaultValue, Function<Config, Map<String, Object>> getter, BiFunction<Builder, Map<String, Object>, Builder> writer, String comment) {
            this(path, defaultValue, getter, writer, List.of(comment));
        }

        @Override
        public Map<String, Object> read(FileConfiguration config, String prefix) {
            String fullPath = prefix + getPath();
            return convertYmlGamerules((List<Map<?, ?>>) config.get(fullPath));
        }

        @Override
        public void write(FileConfiguration fileConfig, String prefix, Config config, Config defaultConfig) {
            Map<String, Object> value = get(config);
            Map<String, Object> defaultValue = get(defaultConfig);
            String fullPath = prefix + getPath();

            // only save if the config differs from the default
            List<Map<String, ?>> gamerulesToSave = gamerulesList(value);
            gamerulesToSave.removeAll(gamerulesList(defaultValue));
            if (gamerulesToSave.isEmpty()) gamerulesToSave = null;

            fileConfig.set(fullPath, gamerulesToSave);
            if (!getComments().isEmpty()) fileConfig.setInlineComments(fullPath, getComments());
        }

        private @NotNull Map<String, Object> convertYmlGamerules(List<Map<?, ?>> ymlGamerules) {
            if (ymlGamerules == null) return Map.of();
            Map<String, Object> gamerules = new HashMap<>();

            for (Map<?, ?> ymlGamerule : ymlGamerules) {
                for (Map.Entry<?, ?> entry : ymlGamerule.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) continue;

                    gamerules.put(key, entry.getValue());
                }
            }
            return gamerules;
        }

        private @NotNull List<Map<String, ?>> gamerulesList(Map<String, Object> gamerules) {
            List<Map<String, ?>> gamerulesList = new ArrayList<>(gamerules.size());
            for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
                gamerulesList.add(Map.of(entry.getKey(), entry.getValue()));
            }
            return gamerulesList;
        }
    }
}
