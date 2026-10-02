package live.minehub.polarpaper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.ChunkUtils;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.generator.PolarStreamLoader;
import live.minehub.polarpaper.core.generator.PolarWorldLoader;
import live.minehub.polarpaper.core.source.BytesPolarSource;
import live.minehub.polarpaper.core.source.FilePolarSource;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.world.*;
import live.minehub.polarpaper.nms.VersionUtil;
import live.minehub.polarpaper.util.EntitiesWorldAccess;
import live.minehub.polarpaper.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("unused")
public class Polar {

    private static final Logger LOGGER = LoggerFactory.getLogger(Polar.class);

    private static final Set<NamespacedKey> LOADING_WORLDS = new CopyOnWriteArraySet<>();
    private static final Map<NamespacedKey, ScheduledTask> AUTOSAVE_TASK_MAP = new ConcurrentHashMap<>();

    private Polar() {

    }

    public static FilePolarSource getDefaultFolderSource(String worldName) {
        Path worldsFolder = PolarPaper.getWorldsPath();
        Path path = worldsFolder.resolve(worldName + ".polar");
        return new FilePolarSource(path);
    }

    public static boolean isLoading(NamespacedKey worldKey) {
        return LOADING_WORLDS.contains(worldKey);
    }

    public static void setLoading(NamespacedKey worldKey, boolean loading) {
        if (loading) {
            LOADING_WORLDS.add(worldKey);
        } else {
            LOADING_WORLDS.remove(worldKey);
        }
    }

    /**
     * Load a polar world using the source defined in the config
     *
     * @param worldName The name of the world to load
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see Polar#getDefaultFolderSource(String)
     */
    public static CompletableFuture<@Nullable World> createWorld(@Nullable PolarSource source, @NotNull String worldName) {
        return createWorld(source, worldName, VersionUtil.getPolarFeaturesWorldAccess());
    }

    /**
     * Load a polar world with config read from config.yml and with the default PolarWorldAccess
     *
     * @param worldName The name for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(PolarWorld polarWorld, @NotNull String worldName) {
        return createWorld(polarWorld, worldName, VersionUtil.getPolarFeaturesWorldAccess());
    }

    /**
     * Load a polar world with config read from config.yml
     *
     * @param polarSource The source to load the polar world from
     * @param worldName The name for the polar world
     * @param worldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see Polar#getDefaultFolderSource(String)
     * @see BytesPolarSource
     * @see EntitiesWorldAccess
     */
    public static CompletableFuture<@Nullable World> createWorld(@Nullable PolarSource polarSource, @NotNull String worldName, @NotNull PolarWorldAccess worldAccess) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        return createWorld(polarSource, worldName, config, worldAccess, PolarDataConverter.DEFAULT);
    }

    /**
     * Creates a polar world with config read from config.yml
     *
     * @param worldName The name for the polar world
     * @param worldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see EntitiesWorldAccess
     */
    public static CompletableFuture<@Nullable World> createWorld(@NotNull PolarWorld polarWorld, @NotNull String worldName, @NotNull PolarWorldAccess worldAccess) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        return createWorld(polarWorld, worldName, config, worldAccess, PolarDataConverter.DEFAULT);
    }

    /**
     * Creates a polar world with the default PolarWorldAccess
     *
     * @param polarSource The source to load the polar world from
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see Polar#getDefaultFolderSource(String)
     * @see BytesPolarSource
     */
    public static CompletableFuture<@Nullable World> createWorld(@Nullable PolarSource polarSource, @NotNull String worldName, @NotNull Config config) {
        return createWorld(polarSource, worldName, config, VersionUtil.getPolarFeaturesWorldAccess(), PolarDataConverter.DEFAULT);
    }

    /**
     * Creates a polar world with the default PolarWorldAccess
     *
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(@NotNull PolarWorld polarWorld, @NotNull String worldName, @NotNull Config config) {
        return createWorld(polarWorld, worldName, config, VersionUtil.getPolarFeaturesWorldAccess(), PolarDataConverter.DEFAULT);
    }

    /**
     * Creates a polar world
     *
     * @param source The source to load the polar world from
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see Polar#getDefaultFolderSource(String)
     * @see BytesPolarSource
     */
    public static CompletableFuture<@Nullable World> createWorld(@Nullable PolarSource source, @NotNull String worldName, @NotNull Config config, @NotNull PolarWorldAccess worldAccess, @NotNull PolarDataConverter dataConverter) {
        EntitySerializer entitySerializer = VersionUtil.getEntitySerializer();
        Format detectedFormat = source == null ? Format.POLAR_FORMAT : Format.detectFormat(source);
        if (detectedFormat == null) {
            String message = "Failed to detect the file type for world '%s'".formatted(worldName);
            LOGGER.warn(message);
            return CompletableFuture.failedFuture(new RuntimeException(message));
        }
        PolarGenerator loader = detectedFormat.createGenerator(config, source, worldAccess, dataConverter, entitySerializer);
        return createWorld(loader, worldName);
    }

    /**
     * Creates a polar world
     *
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(@NotNull PolarWorld polarWorld, @NotNull String worldName, @NotNull Config config, @NotNull PolarWorldAccess worldAccess, @NotNull PolarDataConverter dataConverter) {
        PolarGenerator loader = new PolarWorldLoader(config, polarWorld, worldAccess);
        return createWorld(loader, worldName);
    }

    /**
     * Creates a polar world
     *
     * @param generator Generator for the world
     * @param worldName The name for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see EntitiesWorldAccess
     * @see PolarStreamLoader
     */
    public static CompletableFuture<@Nullable World> createWorld(@NotNull PolarGenerator generator, @NotNull String worldName) {
        worldName = worldName.toLowerCase().replace(" ", "_");

        NamespacedKey worldKey = NamespacedKey.fromString(worldName, PolarPaper.getPlugin());
        if (worldKey == null) {
            LOGGER.warn("Invalid world name '{}'", worldName);
            return CompletableFuture.completedFuture(null);
        }

        if (Bukkit.getWorld(worldKey) != null) {
            LOGGER.warn("A world with the name '{}' already exists, skipping.", worldName);
            return CompletableFuture.completedFuture(null);
        }

        Config config = generator.getConfig();

        WorldCreator worldCreator = WorldCreator.ofKey(worldKey)
                .type(config.worldType())
                .environment(config.environment())
                .generator(generator);

        long beforeTime = System.nanoTime();

        @NotNull String finalWorldName = worldName;
        return VersionUtil.createNoSaveLevel(worldCreator, config.spawn(), config.difficulty(), config.gamerules(), config.time())
                .thenCompose(world -> {
                    if (world == null) return CompletableFuture.completedFuture(null);

                    // Since saving is disabled in the level anyway, setAutoSave is now essentially setting whether
                    // chunks should be allowed to unload and be removed from memory
                    world.setAutoSave(false);

                    ServerLevel level = ((CraftWorld) world).getHandle();
                    return ChunkUtils.insertEmptyChunks(generator.getWorldAccess().getPlugin(), level).thenApply(_ -> world);
                }).thenComposeAsync(world -> {
                    if (world == null) return null;

                    return generator.load(world).thenApply(_ -> world);
                }).thenApply(world -> {
                    if (world == null) return null;
                    LOGGER.info("Loaded {} in {}ms", finalWorldName, (System.nanoTime() - beforeTime) / 1_000_000);
                    setLoading(world.getKey(), false);
                    startAutoSaveTask(world, config);
                    return world;
                }).exceptionally(e -> {
                    LOGGER.error("Failed to load world {}", finalWorldName, e);
                    return null;
                });
    }

    public static void stopAutoSaveTask(NamespacedKey worldKey) {
        ScheduledTask prevTask = AUTOSAVE_TASK_MAP.get(worldKey);
        if (prevTask != null) {
            prevTask.cancel();
            AUTOSAVE_TASK_MAP.remove(worldKey);
        }
    }

    public static void startAutoSaveTask(World world, Config config) {
        startAutoSaveTask(world, config.autoSaveIntervalTicks(), config.announceAutosave());
    }

    public static void startAutoSaveTask(World world, int autosaveIntervalTicks, boolean announceAutosave) {
        stopAutoSaveTask(world.getKey());

        if (autosaveIntervalTicks == -1) return;

        ScheduledTask autosaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(PolarPaper.getPlugin(), t -> {
            long before = System.nanoTime();
            String savingMsg = String.format("Autosaving '%s'...", world.getKey().getKey());
            LOGGER.info(savingMsg);
            if (announceAutosave) for (Player plr : Bukkit.getOnlinePlayers()) {
                if (!plr.hasPermission("polar.notifications")) continue;
                plr.sendMessage(Component.text(savingMsg, NamedTextColor.AQUA));
            }

            updateConfig(world, world.getKey().getKey()); // config should only be updated synchronously
            saveWorld(world)
                    .whenComplete((_, e) -> {
                        if (e != null) {
                            String errorMsg = String.format("Failed to save '%s', please check logs for error", world.getKey().getKey());
                            LOGGER.error(errorMsg, e);
                            for (Player plr : Bukkit.getOnlinePlayers()) {
                                if (!plr.hasPermission("polar.notifications")) continue;
                                plr.sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                            }
                            return;
                        }

                        int ms = (int) ((System.nanoTime() - before) / 1_000_000);
                        String savedMsg = String.format("Saved '%s' in %sms", world.getKey().getKey(), ms);
                        LOGGER.info(savedMsg);
                        if (announceAutosave) for (Player plr : Bukkit.getOnlinePlayers()) {
                            if (!plr.hasPermission("polar.notifications")) continue;
                            plr.sendMessage(Component.text(savedMsg, NamedTextColor.AQUA));
                        }
                    });
        }, autosaveIntervalTicks, autosaveIntervalTicks);

        AUTOSAVE_TASK_MAP.put(world.getKey(), autosaveTask);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setGameRule(World world, GameRule<?> rule, Object value) {
        world.setGameRule((GameRule<T>) rule, (T)value);
    }

    /**
     * Writes this world's properties to config (e.g. gamerules)
     * Should only be called synchronously
     */
    public static Config updateConfig(World world, String worldName) {
        PolarPaper.getPlugin().reloadConfig();
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config defaultConfig = Config.getDefaultConfig(fileConfig);
        Config newConfig = Config.readFromConfig(fileConfig, worldName, defaultConfig.toBuilder()).toBuilder().fromWorld(world).build(); // If world not in config, use defaults

        Config.writeToConfig(PolarPaper.getConfigPath(), fileConfig, worldName, newConfig);

        return newConfig;
    }

    /**
     * Reads the config for the world and updates the world's properties (e.g. gamerules)
     */
    public static void reloadConfig(World world) {
        PolarPaper.getPlugin().reloadConfig();

        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) return;

        Config config = Config.readFromConfig(PolarPaper.getPlugin().getConfig(), world);

        generator.setConfig(config);

        world.setDifficulty(org.bukkit.Difficulty.valueOf(config.difficulty().name()));

        for (Map.Entry<String, Object> gamerule : config.gamerules().entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(gamerule.getKey());
            if (key == null) continue;
            GameRule<?> rule = org.bukkit.Registry.GAME_RULE.get(key);
            if (rule == null) {
                LOGGER.warn("Invalid gamerule: {}", key.asMinimalString());
                continue;
            }
            setGameRule(world, rule, gamerule.getValue());
        }

        Polar.startAutoSaveTask(world, config);
    }

    /**
     * Saves a polar world asynchronously using the source used to load it
     * <br>
     * Will not save if a source was not used to load the world
     *
     * @param world The bukkit world (needs to be a polar world)
     * @see PolarGenerator#getSource()
     */
    public static CompletableFuture<Void> saveWorld(World world) {
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) return CompletableFuture.completedFuture(null);
        PolarSource source = generator.getSource();
        if (source == null) return CompletableFuture.completedFuture(null);
        return saveWorld(world, source);
    }

    /**
     * Saves a polar world asynchronously using the given source
     *
     * @param world The bukkit world (needs to be a polar world)
     * @param polarSource The source to use to save the polar world
     * @see Polar#getDefaultFolderSource(String)
     * @see BytesPolarSource
     */
    @SuppressWarnings("unused")
    public static CompletableFuture<Void> saveWorld(World world, PolarSource polarSource) {
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) return CompletableFuture.completedFuture(null);
        Collection<PolarChunk> extraChunks = generator.getPolarWorld() == null ? List.of() : generator.getPolarWorld().chunks();
        return saveWorld(world, extraChunks, polarSource, generator.getWorldAccess(), BlockSelector.ALL, generator.getConfig());
    }

    /**
     * Updates and saves a polar world asynchronously using the given source
     * <br>
     * The future is completed exceptionally if saving failed
     *
     * @param world The bukkit world to retrieve new chunks from
     * @param extraChunks Extra chunks to include in the saved file
     * @param polarSource The source to use to save the polar world
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param blockSelector Used to filter which blocks should be updated (essentially a crop)
     * @param config Whether to save the world with light data
     * @see EntitiesWorldAccess
     * @see BlockSelector#ALL
     */
    public static CompletableFuture<Void> saveWorld(World world, Collection<PolarChunk> extraChunks, PolarSource polarSource, PolarWorldAccess polarWorldAccess, BlockSelector blockSelector, Config config) {
        if (Polar.isLoading(world.getKey())) return CompletableFuture.failedFuture(new IllegalStateException(world.getKey() + " is still loading"));

        PolarGenerator generator = PolarGenerator.fromWorld(world);
        byte[] prevWorldUserData = generator == null ? new byte[0] : generator.getUserData();

        CompletableFuture<PolarWorld> future;
        try {
            future = PolarWorld.convert(world, polarWorldAccess, blockSelector, config.saveLight(), extraChunks, false);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        return future.thenAcceptAsync(newPolarWorld -> {
            newPolarWorld.userData(prevWorldUserData);
            PolarWriter.write(polarSource, newPolarWorld, config);
        });
    }

}
