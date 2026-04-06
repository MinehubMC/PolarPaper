package live.minehub.polarpaper;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import io.papermc.paper.world.PaperWorldLoader;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.generator.PolarStreamingGenerator;
import live.minehub.polarpaper.source.FilePolarSource;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Main;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftGameRule;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.generator.CraftWorldInfo;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class Polar {

    private static final Map<String, ScheduledTask> AUTOSAVE_TASK_MAP = new HashMap<>();

    private Polar() {

    }

    /**
     * Load a world from the plugins/polarpaper/worlds folder
     *
     * @param worldName The name of the world to load
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> loadWorldFromFile(@NotNull String worldName) {
        return loadWorld(FilePolarSource.defaultFolder(worldName), worldName, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    /**
     * Load a polar world using the source defined in the config
     *
     * @param worldName The name of the world to load
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see FilePolarSource#defaultFolder(String)
     */
    public static CompletableFuture<@Nullable World> loadWorld(@NotNull PolarSource source, @NotNull String worldName) {
        return loadWorld(source, worldName, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    /**
     * Load and create a polar world
     *
     * @param source The source to load the polar world from
     * @param worldName The name of the world to create
     * @param worldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see FilePolarSource#defaultFolder(String)
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     */
    public static CompletableFuture<@Nullable World> loadWorld(@NotNull PolarSource source, @NotNull String worldName, @NotNull PolarWorldAccess worldAccess) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults

        CompletableFuture<@Nullable World> future = new CompletableFuture<>();

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), task -> {
            try {
                byte[] bytes = source.readBytes();

                Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
                    createWorld(bytes, worldName, config, worldAccess)
                            .thenAccept(future::complete)
                            .exceptionally(e -> {
                                ExceptionUtil.log(e);
                                return null;
                            });
                });
            } catch (Exception e) {
                PolarPaper.logger().warning("Exception while loading world: " + worldName);
                ExceptionUtil.log(e);
                future.complete(null);
            }
        });

        return future;
    }

    /**
     * Creates a polar world with config read from config.yml and with the default PolarWorldAccess
     *
     * @param worldName The name for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(byte[] worldBytes, @NotNull String worldName) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        return createWorld(worldBytes, worldName, config, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    /**
     * Creates a polar world with config read from config.yml and with the default PolarWorldAccess
     *
     * @param worldName The name for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(PolarWorld polarWorld, @NotNull String worldName) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        return createWorld(polarWorld, worldName, config, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    /**
     * Creates a polar world with config read from config.yml
     *
     * @param worldName The name for the polar world
     * @param worldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     */
    public static CompletableFuture<@Nullable World> createWorld(byte[] worldBytes, @NotNull String worldName, @NotNull PolarWorldAccess worldAccess) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        return createWorld(worldBytes, worldName, config, worldAccess);
    }

    /**
     * Creates a polar world with config read from config.yml
     *
     * @param worldName The name for the polar world
     * @param worldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     */
    public static CompletableFuture<@Nullable World> createWorld(PolarWorld polarWorld, @NotNull String worldName, @NotNull PolarWorldAccess worldAccess) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        return createWorld(polarWorld, worldName, config, worldAccess);
    }

    /**
     * Creates a polar world with the default PolarWorldAccess
     *
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(byte[] worldBytes, @NotNull String worldName, @NotNull Config config) {
        return createWorld(worldBytes, worldName, config, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    /**
     * Creates a polar world with the default PolarWorldAccess
     *
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(PolarWorld polarWorld, @NotNull String worldName, @NotNull Config config) {
        return createWorld(polarWorld, worldName, config, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    /**
     * Creates a polar world
     *
     * @param worldBytes The byte array of the polar world. Null to load a blank world
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(byte[] worldBytes, @NotNull String worldName, @NotNull Config config, @NotNull PolarWorldAccess worldAccess) {
        return createWorld(new PolarStreamingGenerator(config, worldAccess), worldName, worldAccess).thenApplyAsync(world -> {
            if (world == null) return null;
            if (worldBytes != null && worldBytes.length > 0) {
                PolarStreamLoader.stream(worldBytes, world, worldAccess);
            }
            return world;
        });
    }

    /**
     * Creates a polar world
     *
     * @param worldName The name for the polar world
     * @param config Custom config for the polar world
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     */
    public static CompletableFuture<@Nullable World> createWorld(PolarWorld polarWorld, @NotNull String worldName, @NotNull Config config, @NotNull PolarWorldAccess worldAccess) {
        return createWorld(new PolarStreamingGenerator(config, worldAccess), worldName, worldAccess).thenApplyAsync(world -> {
            if (world == null) return null;
            ServerLevel level = ((CraftWorld) world).getHandle();
            for (PolarChunk chunk : polarWorld.chunks()) {
                NoUnloadLevelChunk levelChunk = chunk.createLevelChunk(level);
                Bukkit.getGlobalRegionScheduler().run(PolarPaper.getPlugin(), t -> {
                    worldAccess.loadChunkData(world, levelChunk, polarWorld.userData());
                });

                PolarStreamLoader.insertChunk(level, levelChunk);
            }
            return world;
        });
    }

    /**
     * Creates a polar world
     *
     * @param generator Generator for the world
     * @param worldName The name for the polar world
     * @param worldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @return CompletableFuture with the created bukkit world (completes immediately if not async)
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     * @see PolarStreamingGenerator
     */
    public static CompletableFuture<@Nullable World> createWorld(@NotNull PolarGenerator generator, @NotNull String worldName, @NotNull PolarWorldAccess worldAccess) {
        if (Bukkit.getWorld(worldName) != null) {
            PolarPaper.logger().warning("A world with the name '" + worldName + "' already exists, skipping.");
            return CompletableFuture.completedFuture(null);
        }

        Config config = generator.getConfig();

        WorldCreator worldCreator = WorldCreator.name(worldName)
                .type(config.worldType())
                .environment(config.environment())
                .generator(generator);

        CompletableFuture<@Nullable World> future = new CompletableFuture<>();

        Runnable worldCreateRunnable = () -> {
            World newWorld = createPolarLevel(worldCreator, config.spawn(), config.difficulty(), config.gamerules(), config.time());

            if (newWorld == null) {
                PolarPaper.logger().warning("An error occurred loading polar world '" + worldName + "', skipping.");
                future.complete(null);
                return;
            }

            // Since autosave is disabled in the PolarServerLevel anyway, setAutoSave is now essentially setting whether
            // chunks should be allowed to unload and be removed from memory
            newWorld.setAutoSave(false);

            startAutoSaveTask(newWorld, config);
            future.complete(newWorld);
        };

        if (config.async()) {
            Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), (t) -> worldCreateRunnable.run());
        } else {
            worldCreateRunnable.run();
        }

        return future;
    }

    public static void stopAutoSaveTask(String worldName) {
        ScheduledTask prevTask = AUTOSAVE_TASK_MAP.get(worldName);
        if (prevTask != null) prevTask.cancel();
    }

    public static void startAutoSaveTask(World world, Config config) {
        stopAutoSaveTask(world.getName());

        if (config.autoSaveIntervalTicks() == -1) return;

        ScheduledTask autosaveTask = Bukkit.getAsyncScheduler().runAtFixedRate(PolarPaper.getPlugin(), (t) -> {
            long before = System.nanoTime();
            String savingMsg = String.format("Autosaving '%s'...", world.getName());
            PolarPaper.logger().info(savingMsg);
            if (config.announceAutosave()) for (Player plr : Bukkit.getOnlinePlayers()) {
                if (!plr.hasPermission("polar.notifications")) continue;
                plr.sendMessage(Component.text(savingMsg, NamedTextColor.AQUA));
            }

            Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
                updateConfig(world, world.getName()); // config should only be updated synchronously
            });
            try {
                saveWorldToFile(world);
            } catch (Exception e) {
                String errorMsg = String.format("Failed to save '%s', please check logs for error", world.getName());
                PolarPaper.logger().severe(errorMsg);
                for (Player plr : Bukkit.getOnlinePlayers()) {
                    if (!plr.hasPermission("polar.notifications")) continue;
                    plr.sendMessage(Component.text(errorMsg, NamedTextColor.RED));
                }
                return;
            }

            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            String savedMsg = String.format("Saved '%s' in %sms", world.getName(), ms);
            PolarPaper.logger().info(savedMsg);
            if (config.announceAutosave()) for (Player plr : Bukkit.getOnlinePlayers()) {
                if (!plr.hasPermission("polar.notifications")) continue;
                plr.sendMessage(Component.text(savedMsg, NamedTextColor.AQUA));
            }
        }, config.autoSaveIntervalTicks() * 50L, config.autoSaveIntervalTicks() * 50L, TimeUnit.MILLISECONDS);

        AUTOSAVE_TASK_MAP.put(world.getName(), autosaveTask);
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
        Config newConfig = Config.updateConfigWithWorld(Config.readFromConfig(fileConfig, worldName, defaultConfig), world); // If world not in config, use defaults

        Config.writeToConfig(fileConfig, worldName, newConfig);

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
                PolarPaper.logger().warning("Invalid gamerule: " + key.asMinimalString());
                continue;
            }
            setGameRule(world, rule, gamerule.getValue());
        }

        Polar.startAutoSaveTask(world, config);
    }

    public static void saveWorldToFile(World world) {
        saveWorld(world, FilePolarSource.defaultFolder(world.getName()));
    }

    /**
     * Updates and saves a polar world using the given source
     * Can be called asynchronously
     *
     * @param world The bukkit world (needs to be a polar world)
     * @param polarSource The source to use to save the polar world (e.g. FilePolarSource)
     */
    @SuppressWarnings("unused")
    public static void saveWorld(World world, PolarSource polarSource) {
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) return;
        Collection<PolarChunk> extraChunks = generator.getPolarWorld() == null ? List.of() : generator.getPolarWorld().chunks();
        saveWorld(world, extraChunks, polarSource, generator.getWorldAccess(), BlockSelector.ALL, generator.getConfig());
    }

    /**
     * Updates and saves a polar world using the given source
     * Can be called asynchronously
     *
     * @param world The bukkit world to retrieve new chunks from
     * @param extraChunks Extra chunks to include in the saved file
     * @param polarSource The source to use to save the polar world (e.g. FilePolarSource)
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param blockSelector Used to filter which blocks should be updated (essentially a crop)
     * @param config Custom config for the polar world
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     * @see BlockSelector#ALL
     */
    public static void saveWorld(World world, Collection<PolarChunk> extraChunks, PolarSource polarSource, PolarWorldAccess polarWorldAccess, BlockSelector blockSelector, Config config) {
        PolarWorld newPolarWorld = PolarWorld.convert(world, polarWorldAccess, blockSelector, config, extraChunks);
        byte[] worldBytes = PolarWriter.write(newPolarWorld);
        polarSource.saveBytes(worldBytes);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static @Nullable World createPolarLevel(WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();

        boolean async = !craftServer.isPrimaryThread();

        // Check if already existing
        if (craftServer.getWorld(creator.name()) != null) {
            return null;
        }

        Preconditions.checkState(craftServer.getServer().getAllLevels().iterator().hasNext(), "Cannot create additional worlds on STARTUP");
        //Preconditions.checkState(!this.console.isIteratingOverLevels, "Cannot create a world while worlds are being ticked"); // Paper - Cat - Temp disable. We'll see how this goes.

        String name = creator.name();
        ChunkGenerator chunkGenerator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        File folder = new File(craftServer.getWorldContainer(), name);
        World world = craftServer.getWorld(name);

        // Paper start
        World worldByKey = craftServer.getWorld(creator.key());
        if (world != null || worldByKey != null) {
            if (world == worldByKey) {
                return world;
            }
            throw new IllegalArgumentException("Cannot create a world with key " + creator.key() + " and name " + name + " one (or both) already match a world that exists");
        }
        // Paper end

        if (folder.exists()) {
            Preconditions.checkArgument(folder.isDirectory(), "File (%s) exists and isn't a folder", name);
        }

        if (chunkGenerator == null) {
            chunkGenerator = craftServer.getGenerator(name);
        }

        if (biomeProvider == null) {
            biomeProvider = craftServer.getBiomeProvider(name);
        }

        ResourceKey<LevelStem> actualDimension = switch (creator.environment()) {
            case NORMAL -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        };

        LevelStorageSource.LevelStorageAccess levelStorageAccess;
        try {
            Path pluginFolder = PolarPaper.getPlugin().getDataPath();
            Path tempFolder = pluginFolder.resolve("temp");

            levelStorageAccess = LevelStorageSource.createDefault(tempFolder).validateAndCreateAccess(name, actualDimension);
        } catch (IOException | ContentValidationException ex) {
            throw new RuntimeException(ex);
        }

        boolean hardcore = creator.hardcore();

        PrimaryLevelData primaryLevelData;
        WorldLoader.DataLoadContext context = craftServer.getServer().worldLoaderContext;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        Registry<LevelStem> contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        Dynamic<?> dataTag = PaperWorldLoader.getLevelData(levelStorageAccess).dataTag();
        if (dataTag != null) {
            LevelDataAndDimensions levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dataTag, context.dataConfiguration(), contextLevelStemRegistry, context.datapackWorldgen()
            );
            primaryLevelData = (PrimaryLevelData) levelDataAndDimensions.worldData();
            registryAccess = levelDataAndDimensions.dimensions().dimensionsRegistryAccess();
        } else {
            LevelSettings levelSettings;
            WorldOptions worldOptions = new WorldOptions(creator.seed(), creator.generateStructures(), creator.bonusChest());
            WorldDimensions worldDimensions;

            net.minecraft.world.Difficulty minecraftDifficulty;

            // Can be used the id but method byId is deprecated
            try {
                minecraftDifficulty = net.minecraft.world.Difficulty.valueOf(difficulty.name());
            } catch (IllegalArgumentException e) { // This error should never happen
                PolarPaper.logger().warning("Difficulty " + difficulty.name() + " not found, defaulting to NORMAL");
                minecraftDifficulty = net.minecraft.world.Difficulty.NORMAL;
            }

            net.minecraft.world.level.gamerules.GameRules nmsGameRules = new net.minecraft.world.level.gamerules.GameRules(context.dataConfiguration().enabledFeatures());

            for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
                NamespacedKey key = NamespacedKey.fromString(entry.getKey());
                if (key == null) {
                    if (Config.DEFAULT_GAMERULES.containsKey(entry.getKey())) continue; // is a custom gamerule, ignore
                    PolarPaper.logger().warning("Invalid gamerule: " + entry.getKey());
                    continue;
                }
                GameRule<?> rule = org.bukkit.Registry.GAME_RULE.get(key);
                if (rule == null) {
                    PolarPaper.logger().warning("Invalid gamerule: " + key.asMinimalString());
                    continue;
                }
                net.minecraft.world.level.gamerules.GameRule<Object> nmsRule = ((CraftGameRule<Object>)rule).getHandle();

                nmsGameRules.set(nmsRule, entry.getValue(), null);
            }

            // fix for log "No key layers in MapLike[{}]"
            JsonObject defaultGenSettings = new JsonObject();
            defaultGenSettings.add("layers", new JsonArray());
            defaultGenSettings.add("biome", new JsonPrimitive("minecraft:plains"));
            DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(creator.generatorSettings().isEmpty() ? defaultGenSettings : GsonHelper.parse(creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));
            levelSettings = new LevelSettings(
                    name,
                    GameType.byId(craftServer.getDefaultGameMode().getValue()),
                    hardcore, minecraftDifficulty,
                    false,
                    nmsGameRules,
                    context.dataConfiguration()
            );
            worldDimensions = properties.create(context.datapackWorldgen());

            WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
            Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());

            primaryLevelData = new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle);
            registryAccess = complete.dimensionsRegistryAccess();
        }

        contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        primaryLevelData.customDimensions = contextLevelStemRegistry;
        primaryLevelData.checkName(name);
        primaryLevelData.setModdedInfo(craftServer.getServer().getServerModName(), craftServer.getServer().getModdedStatus().shouldReportAsModified());

        if (craftServer.getServer().options.has("forceUpgrade")) {
            Main.forceUpgrade(levelStorageAccess, primaryLevelData, DataFixers.getDataFixer(), craftServer.getServer().options.has("eraseCache"), () -> true, registryAccess, craftServer.getServer().options.has("recreateRegionFiles"));
        }

        long i = BiomeManager.obfuscateSeed(primaryLevelData.worldGenOptions().seed());
        List<CustomSpawner> list = ImmutableList.of(
//                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(primaryLevelData)
        );
        LevelStem customStem = contextLevelStemRegistry.getValue(actualDimension);

        WorldInfo worldInfo = new CraftWorldInfo(primaryLevelData, levelStorageAccess, creator.environment(), customStem.type().value(), customStem.generator(), craftServer.getHandle().getServer().registryAccess()); // Paper - Expose vanilla BiomeProvider from WorldInfo
        if (biomeProvider == null && chunkGenerator != null) {
            biomeProvider = chunkGenerator.getDefaultBiomeProvider(worldInfo);
        }

        ResourceKey<Level> dimensionKey;
        String levelName = craftServer.getServer().getProperties().levelName;
        if (name.equals(levelName + "_nether")) {
            dimensionKey = Level.NETHER;
        } else if (name.equals(levelName + "_the_end")) {
            dimensionKey = Level.END;
        } else {
            dimensionKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(creator.key().namespace(), creator.key().value()));
        }

        ServerLevel serverLevel = new PolarServerLevel(
                craftServer.getServer(),
                craftServer.getServer().executor,
                levelStorageAccess,
                primaryLevelData,
                dimensionKey,
                customStem,
                primaryLevelData.isDebugWorld(),
                i,
                creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(),
                true,
                craftServer.getServer().overworld().getRandomSequences(),
                creator.environment(),
                chunkGenerator, biomeProvider
        );

//        if (!(craftServer.getWorlds().containsKey(name.toLowerCase(Locale.ROOT)))) {
//            return null;
//        }

        serverLevel.setDayTime(time);

        Runnable initRunnable = () -> {
            craftServer.getServer().addLevel(serverLevel); // Paper - Put world into worldlist before initing the world; move up
            craftServer.getServer().initWorld(serverLevel, primaryLevelData, primaryLevelData.worldGenOptions());
            // Paper - Put world into worldlist before initing the world; move up

            craftServer.getServer().prepareLevel(serverLevel);

            serverLevel.serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), new BlockPos(spawnPos.getBlockX(), spawnPos.getBlockY(), spawnPos.getBlockZ()), spawnPos.getYaw(), spawnPos.getPitch()));

            craftServer.getServer().updateEffectiveRespawnData();
        };
        if (async) {
            Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), initRunnable);
        } else {
            initRunnable.run();
        }

        return serverLevel.getWorld();
    }

}
