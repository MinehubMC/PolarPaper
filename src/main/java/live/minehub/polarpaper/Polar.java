package live.minehub.polarpaper;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftGameRule;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
        CompletableFuture<@Nullable World> future = new CompletableFuture<>();
        createWorld(new PolarStreamingGenerator(config, worldAccess), worldName, worldAccess).thenAcceptAsync(world -> {
            if (world == null) return;
            if (worldBytes != null && worldBytes.length > 0) {
                PolarStreamLoader.stream(worldBytes, world, worldAccess).thenRun(() -> future.complete(world));
                return;
            }
            future.complete(world);
        });
        return future.whenComplete((u, ex) -> {
            if (ex != null) ExceptionUtil.log(ex);
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
        CompletableFuture<@Nullable World> future = new CompletableFuture<>();
        createWorld(new PolarStreamingGenerator(config, worldAccess), worldName, worldAccess).thenAcceptAsync(world -> {
            if (world == null) return;
            ServerLevel level = ((CraftWorld) world).getHandle();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (PolarChunk chunk : polarWorld.chunks()) {
                NoUnloadLevelChunk levelChunk = chunk.createLevelChunk(level);

                CompletableFuture<Void> future2 = new CompletableFuture<>();
                Bukkit.getGlobalRegionScheduler().run(PolarPaper.getPlugin(), t -> {
                    PolarStreamLoader.insertChunk(level, levelChunk);
                    worldAccess.loadChunkData(world, levelChunk, chunk.userData());
                    future2.complete(null);
                });
                futures.add(future2);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                future.complete(world);
            });
        });
        return future;
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

        final ResourceKey<net.minecraft.world.level.Level> dimensionKey = PaperWorldLoader.dimensionKey(creator.key());
        WorldLoader.DataLoadContext context = craftServer.getServer().worldLoaderContext;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        net.minecraft.core.Registry<LevelStem> contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        final LevelStem configuredStem = craftServer.getServer().registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(actualDimension);
        if (configuredStem == null) {
            throw new IllegalStateException("Missing configured level stem " + actualDimension);
        }

        PaperWorldLoader.LoadedWorldData loadedWorldData = PaperWorldLoader.loadWorldData(
                craftServer.getServer(),
                dimensionKey,
                name
        );
        final PrimaryLevelData primaryLevelData = (PrimaryLevelData) craftServer.getServer().getWorldData();

        WorldOptions worldOptions = new WorldOptions(creator.seed(), creator.generateStructures(), creator.bonusChest());

        // fix for log "No key layers in MapLike[{}]"
        JsonObject defaultGenSettings = new JsonObject();
        defaultGenSettings.add("layers", new JsonArray());
        defaultGenSettings.add("biome", new JsonPrimitive("minecraft:plains"));
        DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(creator.generatorSettings().isEmpty() ? defaultGenSettings : GsonHelper.parse(creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));
        WorldDimensions worldDimensions = properties.create(context.datapackWorldgen());

        WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
        if (complete.dimensions().getValue(actualDimension) == null) {
            throw new IllegalStateException("Missing generated level stem " + actualDimension + " for world " + name);
        }

        net.minecraft.world.Difficulty minecraftDifficulty;
        try {
            minecraftDifficulty = net.minecraft.world.Difficulty.valueOf(difficulty.name());
        } catch (IllegalArgumentException e) { // This error should never happen
            PolarPaper.logger().warning("Difficulty " + difficulty.name() + " not found, defaulting to NORMAL");
            minecraftDifficulty = net.minecraft.world.Difficulty.NORMAL;
        }

        WorldGenSettings worldGenSettings = new WorldGenSettings(worldOptions, worldDimensions);
        registryAccess = complete.dimensionsRegistryAccess();
        loadedWorldData.levelOverrides().setHardcore(creator.hardcore());
        loadedWorldData.levelOverrides().setDifficulty(minecraftDifficulty);
        loadedWorldData = new PaperWorldLoader.LoadedWorldData(
                loadedWorldData.bukkitName(),
                loadedWorldData.uuid(),
                loadedWorldData.pdc(),
                loadedWorldData.levelOverrides()
        );

        contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);

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


        long biomeZoomSeed = BiomeManager.obfuscateSeed(worldGenSettings.options().seed());
        LevelStem customStem = worldGenSettings.dimensions().get(actualDimension).orElse(null);
        if (customStem == null) {
            customStem = contextLevelStemRegistry.getValue(actualDimension);
        }
        if (customStem == null) {
            throw new IllegalStateException("Missing level stem for world " + name + " using key " + actualDimension);
        }

        final SavedDataStorage savedDataStorage = new SavedDataStorage(craftServer.getServer().storageSource.getDimensionPath(dimensionKey).resolve(LevelResource.DATA.id()), craftServer.getServer().getFixerUpper(), craftServer.getServer().registryAccess());
        savedDataStorage.set(WorldGenSettings.TYPE, new WorldGenSettings(worldGenSettings.options(), worldGenSettings.dimensions()));
        savedDataStorage.set(GameRuleMap.TYPE, nmsGameRules.rules);
        List<CustomSpawner> list = ImmutableList.of(
                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(savedDataStorage)
        );

        ServerLevel serverLevel = new ServerLevel(
                craftServer.getServer(),
                craftServer.getServer().executor,
                craftServer.getServer().storageSource,
                worldGenSettings,
                dimensionKey,
                customStem,
                primaryLevelData.isDebugWorld(),
                biomeZoomSeed,
                creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(),
                true,
                actualDimension,
                creator.environment(),
                chunkGenerator,
                biomeProvider,
                savedDataStorage,
                loadedWorldData
        );

//        if (!(craftServer.getWorlds().containsKey(name.toLowerCase(Locale.ROOT)))) {
//            return null;
//        }

        serverLevel.clockManager().setTotalTicks(serverLevel.dimensionType().defaultClock().get(), time);

        Runnable initRunnable = () -> {
            craftServer.getServer().addLevel(serverLevel); // Paper - Put world into worldlist before initing the world; move up
            craftServer.getServer().initWorld(serverLevel);
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
