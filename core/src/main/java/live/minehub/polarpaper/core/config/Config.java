package live.minehub.polarpaper.core.config;

import dev.hallock.zstd.Zstd;
import live.minehub.polarpaper.core.world.PolarConstants;
import live.minehub.polarpaper.core.world.PolarWorld;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        Location spawn,
        Difficulty difficulty,
        boolean async,
        boolean saveLight,
        PolarWorld.CompressionType compression,
        int compressionLevel,
        WorldType worldType,
        World.Environment environment,
        Map<String, Object> gamerules
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
            Property.of("autosaveIntervalTicks", -1, Config::autoSaveIntervalTicks, Builder::autoSaveIntervalTicks, "-1 to disable"),
            Property.of("announceAutosave", true, Config::announceAutosave, Builder::announceAutosave),
            new Property<>("time", 1000L, Config::time, Builder::time, Codec.LONG),
            Property.of("saveOnStop", false, Config::saveOnStop, Builder::saveOnStop),
            Property.of("loadOnStartup", true, Config::loadOnStartup, Builder::loadOnStartup),
            new Property<>("spawn", new Location(null, 0, 64, 0), Config::spawn, Builder::spawn, Codec.LOCATION),
            Property.ofEnum("difficulty", Difficulty.NORMAL, Config::difficulty, Builder::difficulty),
            Property.of("async", false, Config::async, Builder::async, "Experimental, may cause issues with other plugins"),
            Property.of("saveLight", true, Config::saveLight, Builder::saveLight, "Whether chunks are saved with light data. Reduces load time and CPU usage while loading but increases world size"),
            Property.ofEnum("compression", PolarConstants.DEFAULT_COMPRESSION, Config::compression, Builder::compression),
            Property.of("compressionLevel", PolarConstants.DEFAULT_COMPRESSION_LEVEL, Config::compressionLevel, Builder::compressionLevel, "ZSTD compression level, higher means smaller file size but longer save times. Max %s, default %s".formatted(Zstd.zstd().maxCompressionLevel(), PolarConstants.DEFAULT_COMPRESSION_LEVEL)),
            Property.ofEnum("worldType", WorldType.FLAT, Config::worldType, Builder::worldType),
            Property.ofEnum("environment", World.Environment.NORMAL, Config::environment, Builder::environment),
            new Property<>("gamerules", DEFAULT_GAMERULES, Config::gamerules, (b, m) -> {
                for (Map.Entry<String, Object> entry : m.entrySet()) { // add instead of replacing gamerules
                    b.gamerule(entry.getKey(), entry.getValue());
                }
            }, Codec.GAMERULES, "Custom rules: liquidPhysics, blockPhysics, blockGravity, blockFade")
    );

    public static Map<String, Object> getDefaultGamerules() {
        return DEFAULT_GAMERULES;
    }

    public static boolean isInConfig(FileConfiguration config, String worldName) {
        return config.isSet("worlds." + worldName);
    }

    public static Config getDefaultConfig(FileConfiguration config) {
        return readPrefix(config, "default.", Builder.defaults());
    }

    public String spawnString() {
        return locationToString(spawn());
    }

    public static Config readFromConfig(FileConfiguration config, World world) {
        Builder builder = getDefaultConfig(config).toBuilder().fromWorld(world);
        return readFromConfig(config, world.getKey().getKey(), builder);
    }

    public static Config readFromConfig(FileConfiguration config, String worldName) {
        return readFromConfig(config, worldName, getDefaultConfig(config).toBuilder());
    }

    public static Config readFromConfig(FileConfiguration config, String worldName, Builder builder) {
        return readPrefix(config, String.format("worlds.%s.", worldName), builder);
    }

    private static Config readPrefix(FileConfiguration config, String prefix, Builder builder) {
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
        private @Nullable Location spawn;
        private @Nullable Difficulty difficulty;
        private boolean async;
        private boolean saveLight;
        private @Nullable PolarWorld.CompressionType compression;
        private int compressionLevel;
        private @Nullable WorldType worldType;
        private @Nullable World.Environment environment;
        private Map<String, Object> gamerules = new HashMap<>();

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

        public Builder spawn(Location spawn) {
            this.spawn = Objects.requireNonNull(spawn, "Null spawn");
            return this;
        }

        public Builder difficulty(Difficulty difficulty) {
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
        public Builder worldType(WorldType worldType) {
            this.worldType = Objects.requireNonNull(worldType, "Null worldType");
            return this;
        }

        public Builder environment(World.Environment environment) {
            this.environment = Objects.requireNonNull(environment, "Null environment");
            return this;
        }

        public Builder gamerules(Map<String, Object> gamerules) {
            this.gamerules = gamerules;
            return this;
        }

        public Builder gamerule(String gameruleKey, @Nullable Object gameruleValue) {
            this.gamerules.put(gameruleKey, gameruleValue);
            return this;
        }

        public Builder removeGamerule(String gameruleKey) {
            this.gamerules.remove(gameruleKey);
            return this;
        }

        public Config build() {
            return new Config(this.autoSaveIntervalTicks, this.announceAutosave, this.time, this.saveOnStop, this.loadOnStartup,
                    Objects.requireNonNull(this.spawn, "spawn"), Objects.requireNonNull(this.difficulty, "difficulty"), this.async, this.saveLight, Objects.requireNonNull(this.compression, "compression"), this.compressionLevel,
                    Objects.requireNonNull(this.worldType, "worldType"), Objects.requireNonNull(this.environment, "environment"), this.gamerules);
        }

        public static Builder defaults() {
            Builder builder = new Builder();
            for (Property<?> property : PROPERTIES) {
                property.applyDefault(builder);
            }
            return builder;
        }
    }

    /**
     * A config value: where it lives in the yml, its default, how to get it from a {@link Config} and set it on a
     * {@link Builder}, and the {@link Codec} that converts it to and from the yml
     */
    private record Property<T>(String path, T defaultValue, Function<Config, T> getter,
                               BiConsumer<Builder, T> setter, Codec<T> codec, List<String> comments) {

        Property(String path, T defaultValue, Function<Config, T> getter, BiConsumer<Builder, T> setter, Codec<T> codec, String... comments) {
            this(path, defaultValue, getter, setter, codec, List.of(comments));
        }

        /**
         * A value stored as is in the yml, e.g. a boolean or an int
         */
        static <T> Property<T> of(String path, T defaultValue, Function<Config, T> getter, BiConsumer<Builder, T> setter, String... comments) {
            return new Property<>(path, defaultValue, getter, setter, Codec.value(), comments);
        }

        /**
         * An enum stored by name, with a comment listing the possible values
         */
        static <T extends Enum<T>> Property<T> ofEnum(String path, T defaultValue, Function<Config, T> getter, BiConsumer<Builder, T> setter) {
            return new Property<>(path, defaultValue, getter, setter, Codec.ofEnum(defaultValue.getDeclaringClass()));
        }

        void applyDefault(Builder builder) {
            setter.accept(builder, defaultValue);
        }

        void apply(Builder builder, FileConfiguration config, String prefix) {
            T value = codec.read(config.get(prefix + path), defaultValue);
            if (value != null) setter.accept(builder, value);
        }

        void write(FileConfiguration fileConfig, String prefix, Config config, Config defaultConfig) {
            String fullPath = prefix + path;
            // only save if the config differs from the default
            fileConfig.set(fullPath, codec.write(getter.apply(config), getter.apply(defaultConfig)));

            List<String> inlineComments = comments.isEmpty() ? codec.comments() : comments;
            if (!inlineComments.isEmpty()) fileConfig.setInlineComments(fullPath, inlineComments);
        }
    }

    private interface Codec<T> {
        Codec<Long> LONG = of((raw, _) -> {
            if (raw == null) return null;
            if (raw instanceof Number number) return number.longValue();
            LOGGER.warn("Failed to read number value: {}", raw);
            return null;
        }, value -> value);
        Codec<Location> LOCATION = of((raw, defaultValue) -> stringToLocation(raw == null ? null : raw.toString(), defaultValue), Config::locationToString);
        Codec<Map<String, Object>> GAMERULES = new Codec<>() {
            @SuppressWarnings("unchecked")
            @Override
            public Map<String, Object> read(@Nullable Object raw, Map<String, Object> defaultValue) {
                return convertYmlGamerules((List<Map<?, ?>>) raw);
            }

            @Override
            public @Nullable Object write(Map<String, Object> value, Map<String, Object> defaultValue) {
                List<Map<String, ?>> gamerulesToSave = gamerulesList(value);
                gamerulesToSave.removeAll(gamerulesList(defaultValue));
                return gamerulesToSave.isEmpty() ? null : gamerulesToSave;
            }
        };

        /**
         * @param raw the value in the yml, null if missing
         * @return the value to set on the builder, null to leave the builder unchanged
         */
        @Nullable T read(@Nullable Object raw, T defaultValue);

        /**
         * @return the value to put in the yml, null to remove it
         */
        @Nullable Object write(T value, T defaultValue);

        default List<String> comments() {
            return List.of();
        }

        /**
         * A codec that only writes values that differ from the default
         */
        static <T> Codec<T> of(BiFunction<@Nullable Object, T, @Nullable T> read, Function<T, Object> serialize, List<String> comments) {
            return new Codec<>() {
                @Override
                public @Nullable T read(@Nullable Object raw, T defaultValue) {
                    return read.apply(raw, defaultValue);
                }

                @Override
                public @Nullable Object write(T value, T defaultValue) {
                    return value.equals(defaultValue) ? null : serialize.apply(value);
                }

                @Override
                public List<String> comments() {
                    return comments;
                }
            };
        }

        static <T> Codec<T> of(BiFunction<@Nullable Object, T, @Nullable T> read, Function<T, Object> serialize) {
            return of(read, serialize, List.of());
        }

        @SuppressWarnings("unchecked")
        static <T> Codec<T> value() {
            return of((raw, _) -> (T) raw, value -> value);
        }

        static <T extends Enum<T>> Codec<T> ofEnum(Class<T> enumClass) {
            List<String> comments = List.of("One of: " + Arrays.stream(enumClass.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")));
            return of((raw, defaultValue) -> {
                if (raw == null) return null;
                try {
                    return Enum.valueOf(enumClass, raw.toString());
                } catch (IllegalArgumentException _) {
                    LOGGER.warn("Failed to read enum value: {}", raw);
                    return defaultValue;
                }
            }, Enum::name, comments);
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

    private static Location stringToLocation(@Nullable String string, Location defaultValue) {
        if (string == null) return defaultValue;

        String[] split = string.split(",");
        try {
            if (split.length == 3) { // x y z
                return new Location(null, Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
            } else if (split.length == 5) { // x y z yaw pitch
                return new Location(null, Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]),
                        Float.parseFloat(split[3]), Float.parseFloat(split[4]));
            }
        } catch (NumberFormatException _) {
            // fall through to the warning below
        }
        LOGGER.warn("Failed to parse spawn pos: {}", string);
        return defaultValue;
    }

    private static Map<String, Object> convertYmlGamerules(@Nullable List<Map<?, ?>> ymlGamerules) {
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

    private static List<Map<String, ?>> gamerulesList(Map<String, Object> gamerules) {
        List<Map<String, ?>> gamerulesList = new ArrayList<>(gamerules.size());
        for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
            gamerulesList.add(Map.of(entry.getKey(), entry.getValue()));
        }
        return gamerulesList;
    }
}
