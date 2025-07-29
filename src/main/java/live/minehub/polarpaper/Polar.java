package live.minehub.polarpaper;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.util.CoordConversion;
import net.kyori.adventure.util.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Polar {

    private static final Logger LOGGER = Logger.getLogger(Polar.class.getName());

    private Polar() {

    }

    public static boolean isInConfig(@NotNull String worldName) {
        return PolarPaper.getPlugin().getConfig().isSet("worlds." + worldName);
    }

    /**
     * Creates a polar world with config read from config.yml
     *
     * @param world     The polar world
     * @param worldName The name for the polar world
     */
    public static void loadWorld(@NotNull PolarWorld world, @NotNull String worldName) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        if (config == null) {
            LOGGER.warning("Polar world '" + worldName + "' has an invalid config");
            return;
        }
        loadWorld(world, worldName, config);
    }

    /**
     * Creates a polar world
     *
     * @param world     The polar world
     * @param worldName The name for the polar world
     * @param config    Custom config for the polar world
     */
    public static void loadWorld(@NotNull PolarWorld world, @NotNull String worldName, @NotNull Config config) {
        if (Bukkit.getWorld(worldName) != null) {
            LOGGER.warning("A world with the name '" + worldName + "' already exists, skipping.");
            return;
        }

        PolarGenerator polar = new PolarGenerator(world, config);
        PolarBiomeProvider polarBiomeProvider = new PolarBiomeProvider(world);

        WorldCreator worldCreator = WorldCreator.name(worldName)
                .type(config.worldType())
                .environment(config.environment())
                .generator(polar)
                .biomeProvider(polarBiomeProvider)
                .keepSpawnLoaded(TriState.FALSE);

        World newWorld = Polar.loadWorld(worldCreator, config.spawn());
        if (newWorld == null) {
            LOGGER.warning("An error occurred loading polar world '" + worldName + "', skipping.");
            return;
        }

        newWorld.setDifficulty(config.difficulty());
        newWorld.setPVP(config.pvp());
        newWorld.setSpawnFlags(config.allowMonsters(), config.allowAnimals());
        newWorld.setAutoSave(config.autoSave());

        for (Map<String, ?> gamerule : config.gamerules()) {
            for (Map.Entry<String, ?> entry : gamerule.entrySet()) {
                GameRule<?> rule = GameRule.getByName(entry.getKey());
                if (rule == null) return;
                setGameRule(newWorld, rule, entry.getValue());
            }
        }
    }

    private static <T> void setGameRule(World world, GameRule<?> rule, Object value) {
        world.setGameRule((GameRule<T>) rule, (T)value);
    }

    public static Config updateConfig(World world, String worldName) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        if (config == null) return Config.DEFAULT;

        // Add gamerules from world into config
        List<Map<String, ?>> gameruleList = new ArrayList<>();
        for (String name : world.getGameRules()) {
            GameRule<?> gamerule = GameRule.getByName(name);
            if (gamerule == null) continue;

            Object gameRuleValue = world.getGameRuleValue(gamerule);
            if (gameRuleValue == null) continue;
            Object gameRuleDefault = world.getGameRuleDefault(gamerule);
            if (gameRuleValue != gameRuleDefault) {
                gameruleList.add(Map.of(name, gameRuleValue));
            }
        }

        // Update gamerules
        Config newConfig = new Config(
                config.source(),
                config.autoSave(),
                config.saveOnStop(),
                config.loadOnStartup(),
                config.spawn(),
                world.getDifficulty(),
                world.getAllowMonsters(),
                world.getAllowAnimals(),
                config.allowWorldExpansion(),
                world.getPVP(),
                config.worldType(),
                config.environment(),
                gameruleList
        );
        Config.writeToConfig(fileConfig, worldName, newConfig);

        return newConfig;
    }

    /**
     * Load a polar world using the source defined in the config
     *
     * @param worldName The name of the world to load
     * @return Whether loading the world was successful
     */
    public static CompletableFuture<Boolean> loadWorldConfigSource(String worldName) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        if (config == null) {
            LOGGER.warning("Polar world '" + worldName + "' has an invalid config, skipping.");
            return CompletableFuture.completedFuture(false);
        }

        PolarSource source = PolarSource.fromConfig(worldName, config);

        if (source == null) {
            LOGGER.warning("Source " + config.source() + " not recognised");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Bukkit.getAsyncScheduler().runNow(PolarPaper.getPlugin(), task -> {
            try {
                byte[] bytes = source.readBytes();
                PolarWorld polarWorld = PolarReader.read(bytes);

                Bukkit.getScheduler().runTask(PolarPaper.getPlugin(), () -> {
                    loadWorld(polarWorld, worldName, config);
                    future.complete(true);
                });
            } catch (Exception e) {
                LOGGER.warning("Failed to read polar world from file");
                LOGGER.log(Level.INFO, e.getMessage(), e);
                future.complete(false);
            }
        });

        return future;
    }

    public static CompletableFuture<Boolean> saveWorldConfigSource(World world) {
        PolarWorld polarWorld = PolarWorld.fromWorld(world);
        if (polarWorld == null) return CompletableFuture.completedFuture(false);
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) return CompletableFuture.completedFuture(false);
        return saveWorldConfigSource(world, polarWorld, generator.getWorldAccess(), ChunkSelector.all(), 0, 0);
    }

    /**
     * Save a polar world using the source defined in the config
     *
     * @param world The bukkit world
     * @param polarWorld The polar world
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param chunkSelector Used to filter which chunks should save
     * @param offsetX Offset in chunks added to the new chunk
     * @param offsetZ Offset in chunks added to the new chunk
     */
    public static CompletableFuture<Boolean> saveWorldConfigSource(World world, PolarWorld polarWorld, PolarWorldAccess polarWorldAccess, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        Config newConfig = updateConfig(world, world.getName());

        PolarSource source = PolarSource.fromConfig(world.getName(), newConfig);

        if (source == null) {
            LOGGER.warning("Source " + newConfig.source() + " not recognised");
            return CompletableFuture.completedFuture(false);
        }

        return saveWorld(world, polarWorld, polarWorldAccess, source, chunkSelector, offsetX, offsetZ);
    }

    /**
     * Updates and saves a polar world using the given source
     *
     * @param world The bukkit World
     * @param polarWorld The polar world
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param polarSource The source to use to save the polar world (e.g. FilePolarSource)
     * @param chunkSelector Used to filter which chunks should save
     * @param offsetX Offset in chunks added to the new chunk
     * @param offsetZ Offset in chunks added to the new chunk
     * @return Whether it was successful
     */
    public static CompletableFuture<Boolean> saveWorld(World world, PolarWorld polarWorld, PolarWorldAccess polarWorldAccess, PolarSource polarSource, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        return updateWorld(world, polarWorld, polarWorldAccess, chunkSelector, offsetX, offsetZ).thenApply((a) -> {
            byte[] worldBytes = PolarWriter.write(polarWorld);
            polarSource.saveBytes(worldBytes);
            return true;
        }).exceptionally(e -> {
            LOGGER.log(Level.INFO, e.getMessage(), e);
            return false;
        });
    }

    /**
     * Updates and saves a polar world using the given source
     *
     * @param world The bukkit World
     * @param polarSource The source to use to save the polar world (e.g. FilePolarSource)
     * @return Whether it was successful
     */
    public static CompletableFuture<Boolean> saveWorld(World world, PolarSource polarSource) {
        PolarWorld polarWorld = PolarWorld.fromWorld(world);
        if (polarWorld == null) return CompletableFuture.completedFuture(false);
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        if (generator == null) return CompletableFuture.completedFuture(false);
        return saveWorld(world, polarWorld, generator.getWorldAccess(), polarSource, ChunkSelector.all(), 0, 0);
    }

    /**
     * Updates and saves a polar world synchronously using the given source
     * Prefer using saveWorld unless it really needs to be synchronous as this will freeze the server
     *
     * @param world The bukkit World
     * @param polarWorld The polar world
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param polarSource The source to use to save the polar world (e.g. FilePolarSource)
     * @param chunkSelector Used to filter which chunks should save
     * @param offsetX Offset in chunks added to the new chunk
     * @param offsetZ Offset in chunks added to the new chunk
     */
    public static void saveWorldSync(World world, PolarWorld polarWorld, PolarWorldAccess polarWorldAccess, PolarSource polarSource, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        updateWorldSync(world, polarWorld, polarWorldAccess, chunkSelector, offsetX, offsetZ);
        byte[] worldBytes = PolarWriter.write(polarWorld);
        polarSource.saveBytes(worldBytes);
    }

    /**
     * Updates the chunks in a PolarWorld by running updateChunkData on all chunks
     *
     * @param world The bukkit World
     * @param polarWorld The polar world
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param chunkSelector Used to filter which chunks should update
     * @param offsetX Offset in chunks added to the new chunk
     * @param offsetZ Offset in chunks added to the new chunk
     * @return A CompletableFuture that completes once the world has finished updating
     * @see Polar#saveWorld(World, PolarSource)
     */
    public static CompletableFuture<Void> updateWorld(World world, PolarWorld polarWorld, PolarWorldAccess polarWorldAccess, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        List<PolarChunk> chunks = new ArrayList<>(polarWorld.chunks());
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        for (PolarChunk chunk : chunks) {
            if (!chunkSelector.test(chunk.x(), chunk.z())) {
                polarWorld.removeChunkAt(chunk.x(), chunk.z());
                continue;
            }

            var future = world.getChunkAtAsync(chunk.x() + offsetX, chunk.z() + offsetZ)
                    .thenAcceptAsync(c -> {
                        ChunkAccess craftChunk = ((CraftChunk) c).getHandle(ChunkStatus.FULL);
                        boolean unsaved = craftChunk.isUnsaved();
                        if (!unsaved) return; // chunk didn't need updating

                        updateChunkData(polarWorld, polarWorldAccess, c, chunk.x(), chunk.z()).join();

                        craftChunk.tryMarkSaved();
                    })
                    .exceptionally(e -> {
                        LOGGER.warning(e.toString());
                        return null;
                    });

            futures.add(future);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public static void updateWorldSync(World world, PolarWorld polarWorld, PolarWorldAccess polarWorldAccess, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        List<PolarChunk> chunks = new ArrayList<>(polarWorld.chunks());
        for (PolarChunk chunk : chunks) {
            if (!chunkSelector.test(chunk.x(), chunk.z())) {
                polarWorld.removeChunkAt(chunk.x(), chunk.z());
                continue;
            }

            Chunk c = world.getChunkAt(chunk.x() + offsetX, chunk.z() + offsetZ);
            ChunkAccess craftChunk = ((CraftChunk) c).getHandle(ChunkStatus.FULL);
            boolean unsaved = craftChunk.isUnsaved();
            if (!unsaved) return; // chunk didn't need updating

            updateChunkData(polarWorld, polarWorldAccess, c, chunk.x(), chunk.z()).join();

            craftChunk.tryMarkSaved();
        }
    }

    public static @Nullable World loadWorld(WorldCreator creator) {
        return loadWorld(creator, new Location(null, 0, 64, 0));
    }

    public static @Nullable World loadWorld(WorldCreator creator, Location spawnLocation) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();

        // Check if already existing
        if (craftServer.getWorld(creator.name()) != null) {
            return null;
        }

        String name = creator.name();
        ChunkGenerator generator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();

        ResourceKey<LevelStem> actualDimension = switch (creator.environment()) {
            case NORMAL -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        };

        LevelStorageSource.LevelStorageAccess worldSession;
        try {
            worldSession = LevelStorageSource.createDefault(craftServer.getWorldContainer().toPath()).validateAndCreateAccess(name, actualDimension);
        } catch (IOException | ContentValidationException ex) {
            throw new RuntimeException(ex);
        }

        Dynamic<?> dynamic;
        if (worldSession.hasWorldData()) {
            net.minecraft.world.level.storage.LevelSummary worldinfo;

            try {
                dynamic = worldSession.getDataTag();
                worldinfo = worldSession.getSummary(dynamic);
            } catch (NbtException | ReportedNbtException | IOException ioexception) {
                LevelStorageSource.LevelDirectory convertable_b = worldSession.getLevelDirectory();

                MinecraftServer.LOGGER.warn("Failed to load world data from {}", convertable_b.dataFile(), ioexception);
                MinecraftServer.LOGGER.info("Attempting to use fallback");

                try {
                    dynamic = worldSession.getDataTagFallback();
                    worldinfo = worldSession.getSummary(dynamic);
                } catch (NbtException | ReportedNbtException | IOException ioexception1) {
                    MinecraftServer.LOGGER.error("Failed to load world data from {}", convertable_b.oldDataFile(), ioexception1);
                    MinecraftServer.LOGGER.error("Failed to load world data from {} and {}. World files may be corrupted. Shutting down.", convertable_b.dataFile(), convertable_b.oldDataFile());
                    return null;
                }

                worldSession.restoreLevelDataFromOld();
            }

            if (worldinfo.requiresManualConversion()) {
                MinecraftServer.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                return null;
            }

            if (!worldinfo.isCompatible()) {
                MinecraftServer.LOGGER.info("This world was created by an incompatible version.");
                return null;
            }
        } else {
            dynamic = null;
        }

        boolean hardcore = creator.hardcore();

        PrimaryLevelData worlddata;
        WorldLoader.DataLoadContext worldloader_a = craftServer.getServer().worldLoader;
        RegistryAccess.Frozen iregistrycustom_dimension = worldloader_a.datapackDimensions();
        net.minecraft.core.Registry<LevelStem> iregistry = iregistrycustom_dimension.lookupOrThrow(Registries.LEVEL_STEM);
        if (dynamic != null) {
            LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(dynamic, worldloader_a.dataConfiguration(), iregistry, worldloader_a.datapackWorldgen());

            worlddata = (PrimaryLevelData) leveldataanddimensions.worldData();
            iregistrycustom_dimension = leveldataanddimensions.dimensions().dimensionsRegistryAccess();
        } else {
            LevelSettings worldsettings;
            WorldOptions worldoptions = new WorldOptions(creator.seed(), creator.generateStructures(), false);
            WorldDimensions worlddimensions;

            DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(GsonHelper.parse((creator.generatorSettings().isEmpty()) ? "{}" : creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));

            worldsettings = new LevelSettings(name, GameType.byId(craftServer.getDefaultGameMode().getValue()), hardcore, Difficulty.EASY, false, new GameRules(worldloader_a.dataConfiguration().enabledFeatures()), worldloader_a.dataConfiguration());
            worlddimensions = properties.create(worldloader_a.datapackWorldgen());

            WorldDimensions.Complete worlddimensions_b = worlddimensions.bake(iregistry);
            Lifecycle lifecycle = worlddimensions_b.lifecycle().add(worldloader_a.datapackWorldgen().allRegistriesLifecycle());

            worlddata = new PrimaryLevelData(worldsettings, worldoptions, worlddimensions_b.specialWorldProperty(), lifecycle);
            iregistrycustom_dimension = worlddimensions_b.dimensionsRegistryAccess();
        }
        iregistry = iregistrycustom_dimension.lookupOrThrow(Registries.LEVEL_STEM);
        worlddata.customDimensions = iregistry;
        worlddata.checkName(name);

        long j = BiomeManager.obfuscateSeed(worlddata.worldGenOptions().seed()); // Paper - use world seed
        List<CustomSpawner> list = ImmutableList.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(worlddata));
        LevelStem worlddimension = iregistry.getValue(actualDimension);

        ResourceKey<net.minecraft.world.level.Level> worldKey;
        String levelName = craftServer.getServer().getProperties().levelName;
        if (name.equals(levelName + "_nether")) {
            worldKey = net.minecraft.world.level.Level.NETHER;
        } else if (name.equals(levelName + "_the_end")) {
            worldKey = net.minecraft.world.level.Level.END;
        } else {
            worldKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(creator.key().namespace(), creator.key().value()));
        }

        // If set to not keep spawn in memory (changed from default) then adjust rule accordingly
        if (creator.keepSpawnLoaded() == net.kyori.adventure.util.TriState.FALSE) { // Paper
            worlddata.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
        }
        PolarServerLevel internal = new PolarServerLevel(craftServer.getServer(), craftServer.getServer().executor, worldSession, worlddata, worldKey, worlddimension, craftServer.getServer().progressListenerFactory.create(worlddata.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS)),
                worlddata.isDebugWorld(), j, creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(), true, craftServer.getServer().overworld().getRandomSequences(), creator.environment(), generator, biomeProvider);


        craftServer.getServer().addLevel(internal); // Paper - Put world into worldlist before initing the world; move up
        craftServer.getServer().initWorld(internal, worlddata, worlddata, worlddata.worldGenOptions());

//        internal.setSpawnSettings(true);
        // Paper - Put world into worldlist before initing the world; move up

//        craftServer.getServer().prepareLevels(internal.getChunkSource().chunkMap.progressListener, internal);
        // Paper - rewrite chunk system

        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(internal.getWorld()));

        return internal.getWorld();
    }

    public static CompletableFuture<Void> updateChunkData(PolarWorld polarWorld, PolarWorldAccess worldAccess, Chunk chunk, int newChunkX, int newChunkZ) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        if (craftChunk == null) {
            polarWorld.removeChunkAt(newChunkX, newChunkZ);
            return CompletableFuture.completedFuture(null);
        }
        ChunkSnapshot snapshot = craftChunk.getChunkSnapshot(true, true, false, false);
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        LevelChunk chunkAccess = (LevelChunk) craftChunk.getHandle(ChunkStatus.FULL);
        HashSet<Map.Entry<BlockPos, BlockEntity>> blockEntities = new HashSet<>(chunkAccess.blockEntities.entrySet());
        Entity[] entities = Arrays.copyOf(craftChunk.getEntities(), craftChunk.getEntities().length);

        int worldHeight = maxHeight - minHeight + 1; // I hate paper
        int sectionCount = worldHeight / 16;

        boolean onlyPlayers = true;
        for (Entity entity : entities) {
            if (entity.getType() != EntityType.PLAYER) {
                onlyPlayers = false;
                break;
            }
        }

        if (onlyPlayers) { // if contains no entities or the entities are all players
            boolean allEmpty = true;
            for (int i = 0; i < sectionCount; i++) {
                if (!snapshot.isSectionEmpty(i)) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) {
                polarWorld.updateChunkAt(newChunkX, newChunkZ, new PolarChunk(newChunkX, newChunkZ, sectionCount));
                return CompletableFuture.completedFuture(null);
            }
        }


        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();

        return CompletableFuture.runAsync(() -> {
            PolarChunk polarChunk = createPolarChunk(worldAccess, snapshot, newChunkX, newChunkZ, minHeight, maxHeight, blockEntities, entities, registryAccess);
            polarWorld.updateChunkAt(newChunkX, newChunkZ, polarChunk);
        });
    }

    public static PolarChunk createPolarChunk(PolarWorldAccess worldAccess, ChunkSnapshot snapshot, int newChunkX, int newChunkZ, int minHeight, int maxHeight, Set<Map.Entry<BlockPos, BlockEntity>> blockEntities, Entity[] entities, RegistryAccess.Frozen registryAccess) {
        List<PolarChunk.BlockEntity> polarBlockEntities = new ArrayList<>();

        int worldHeight = maxHeight - minHeight + 1; // I hate paper
        int sectionCount = worldHeight / 16;

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            int sectionY = minHeight + i * 16;

            // Blocks
            int[] blockData = new int[4096];
            List<BlockData> blockPalette = new ArrayList<>();
            List<String> blockPaletteStrings = new ArrayList<>();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int sectionLocalY = sectionY + y;

                    for (int z = 0; z < 16; z++) {
                        int blockIndex = x + y * 16 * 16 + z * 16;

                        BlockData data = snapshot.getBlockData(x, sectionLocalY, z);
                        int paletteId = blockPalette.indexOf(data);
                        if (paletteId == -1) {
                            paletteId = blockPalette.size();
                            blockPalette.add(data);
                            blockPaletteStrings.add(data.getAsString(true));
                        }
                        blockData[blockIndex] = paletteId;
                    }
                }
            }
            if (blockPalette.size() == 1) {
                blockData = null;
            }

            // Biomes
            int[] biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];
            List<String> biomePalette = new ArrayList<>();
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        Biome biome = snapshot.getBiome(x * 4, sectionY + y * 4, z * 4);
                        String biomeString = biome.key().toString();

                        int paletteId = biomePalette.indexOf(biomeString);
                        if (paletteId == -1) {
                            paletteId = biomePalette.size();
                            biomePalette.add(biomeString);
                        }
                        biomeData[x + z * 4 + y * 4 * 4] = paletteId;
                    }
                }
            }

            sections[i] = new PolarSection(
                    blockPaletteStrings.toArray(new String[0]), blockData,
                    biomePalette.toArray(new String[0]), biomeData,
                    PolarSection.LightContent.MISSING, null, // TODO: Provide block light
                    PolarSection.LightContent.MISSING, null
            );
        }

        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities) {
            BlockPos blockPos = entry.getKey();
            BlockEntity blockEntity = entry.getValue();

            if (blockPos == null || blockEntity == null) continue;

            CompoundTag compoundTag = blockEntity.saveWithFullMetadata(registryAccess);

            Optional<String> id = compoundTag.getString("id");
            if (id.isEmpty()) {
                LOGGER.warning("No ID in block entity data at: " + blockPos);
                LOGGER.warning("Compound tag: " + compoundTag);
                continue;
            }

            int index = CoordConversion.chunkBlockIndex(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            polarBlockEntities.add(new PolarChunk.BlockEntity(index, id.get(), compoundTag));
        }

        int[][] heightMaps = new int[PolarChunk.MAX_HEIGHTMAPS][0];
        worldAccess.saveHeightmaps(snapshot, heightMaps);

        ByteArrayDataOutput userDataOutput = ByteStreams.newDataOutput();
        worldAccess.saveChunkData(snapshot, blockEntities, entities, userDataOutput);
        byte[] userData = userDataOutput.toByteArray();

        return new PolarChunk(
                newChunkX,
                newChunkZ,
                sections,
                polarBlockEntities,
                heightMaps,
                userData
        );
    }



}
