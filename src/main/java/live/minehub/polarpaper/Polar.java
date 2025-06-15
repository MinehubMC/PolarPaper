package live.minehub.polarpaper;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import live.minehub.polarpaper.util.CoordConversion;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
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
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class Polar {

    private static final Logger LOGGER = Logger.getLogger(Polar.class.getName());

    private Polar() {

    }

    public static boolean isInConfig(@NotNull String worldName) {
        return PolarPaper.getPlugin().getConfig().isSet("worlds." + worldName);
    }

    /**
     * Load a polar world with config read from config.yml
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
     * Load a polar world
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

        World newWorld = Polar.createPolarWorld(worldCreator, config.getSpawnPos());
        if (newWorld == null) {
            LOGGER.warning("An error occurred loading polar world '" + worldName + "', skipping.");
            return;
        }

        newWorld.setDifficulty(config.difficulty());
        newWorld.setPVP(config.pvp());
        newWorld.setSpawnFlags(config.allowMonsters(), config.allowAnimals());
        newWorld.setAutoSave(false);
//        newWorld.setAutoSave(config.autoSave());

        for (Map<String, ?> gamerule : config.gamerules()) {
            for (Map.Entry<String, ?> entry : gamerule.entrySet()) {
                GameRule<?> rule = GameRule.getByName(entry.getKey());
                if (rule == null) return;
                setGameRule(newWorld, rule, entry.getValue());
            }
        }
    }

    private static <T> void setGameRule(World world, GameRule<?> rule, Object value) {
        world.setGameRule((GameRule<T>) rule, (T) value);
    }

    /**
     * Load a polar world using the source defined in the config
     *
     * @param worldName The name of the world to load
     * @param onSuccess Runnable executed when the world is successfully loaded.
     * @param onFailure Runnable executed when the world fails to be loaded.
     */
    public static void loadWorldConfigSource(String worldName, @Nullable Runnable onSuccess, @Nullable Runnable onFailure) {
        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        if (config == null) {
            LOGGER.warning("Polar world '" + worldName + "' has an invalid config, skipping.");
            if (onFailure != null) onFailure.run();
            return;
        }

        switch (config.source()) {
            case "file" -> {
                Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
                Path worldsFolder = pluginFolder.resolve("worlds");

                Path worldPath = worldsFolder.resolve(worldName + ".polar");
                if (!Files.exists(worldPath)) {
                    if (onFailure != null) onFailure.run();
                    return;
                }

                BukkitScheduler scheduler = Bukkit.getScheduler();

                scheduler.runTaskAsynchronously(PolarPaper.getPlugin(), () -> {
                    try {
                        byte[] bytes = Files.readAllBytes(worldPath);
                        PolarWorld polarWorld = PolarReader.read(bytes);

                        scheduler.runTask(PolarPaper.getPlugin(), () -> {
                            loadWorld(polarWorld, worldName, config);
                            if (onSuccess != null) onSuccess.run();
                        });
                    } catch (IOException e) {
                        LOGGER.warning("Failed to read polar world from file");
                        LOGGER.warning(e.toString());
                        if (onFailure != null) onFailure.run();
                    }
                });
            }
            // TODO: mysql?
            default -> {
                LOGGER.warning("Source " + config.source() + " not recognised");
                if (onFailure != null) onFailure.run();
            }
        }
    }

    public static void loadWorldConfigSource(String worldName) {
        loadWorldConfigSource(worldName, null, null);
    }

    /**
     * Save a polar world using the source defined in the config
     *
     * @param world         The bukkit world
     * @param polarWorld    The polar world
     * @param chunkSelector Used to filter which chunks should save
     * @param offsetX       Offset in chunks added to the new chunk
     * @param offsetZ       Offset in chunks added to the new chunk
     * @param onSuccess     Runnable executed when the world is successfully loaded.
     * @param onFailure     Runnable executed when the world fails to be loaded.
     */
    public static void saveWorldConfigSource(World world, PolarWorld polarWorld, PolarGenerator polarGenerator, Path path, ChunkSelector chunkSelector, int offsetX, int offsetZ, @Nullable Runnable onSuccess, @Nullable Runnable onFailure) {
        String worldName = world.getName();

        FileConfiguration fileConfig = PolarPaper.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName); // If world not in config, use defaults
        if (config == null) {
            LOGGER.warning("Polar world '" + worldName + "' has an invalid config, skipping.");
            if (onFailure != null) onFailure.run();
            return;
        }

        LOGGER.info("Saving world " + world.getName());

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
                config.loadOnStartup(),
                config.spawn(),
                config.difficulty(),
                config.allowMonsters(),
                config.allowAnimals(),
                config.allowWorldExpansion(),
                config.pvp(),
                config.worldType(),
                config.environment(),
                gameruleList
        );
        Config.writeToConfig(fileConfig, worldName, newConfig);

        switch (config.source()) {
            case "file" -> {
                Bukkit.getScheduler().runTaskAsynchronously(PolarPaper.getPlugin(), () -> {
                    saveWorld(world, polarWorld, polarGenerator, path, chunkSelector, offsetX, offsetZ).thenAccept(successful -> {
                        if (successful) {
                            if (onSuccess != null) onSuccess.run();
                        } else {
                            if (onFailure != null) onFailure.run();
                        }
                    });
                });
            }
            // TODO: mysql?
            default -> {
                LOGGER.warning("Source " + config.source() + " not recognised");
                if (onFailure != null) onFailure.run();
            }
        }
    }

    /**
     * Save a polar world using the source defined in the config
     *
     * @param world     The bukkit world
     * @param onSuccess Runnable executed when the world is successfully loaded.
     * @param onFailure Runnable executed when the world fails to be loaded.
     */
    public static void saveWorldConfigSource(World world, PolarWorld polarWorld, PolarGenerator polarGenerator, @Nullable Runnable onSuccess, @Nullable Runnable onFailure) {
        Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");
        saveWorldConfigSource(world, polarWorld, polarGenerator, worldsFolder.resolve(world.getName() + ".polar"), ChunkSelector.all(), 0, 0, onSuccess, onFailure);
    }

    /**
     * Save a polar world to a file
     *
     * @param world         The bukkit World
     * @param path          The path to save the polar to (.polar extension recommended)
     * @param chunkSelector Used to filter which chunks should save
     * @param offsetX       Offset in chunks added to the new chunk
     * @param offsetZ       Offset in chunks added to the new chunk
     * @return Whether it was successful
     */
    public static CompletableFuture<Boolean> saveWorld(World world, PolarWorld polarWorld, PolarGenerator polarGenerator, Path path, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        return updateWorld(world, polarWorld, polarGenerator, chunkSelector, offsetX, offsetZ).thenApply((a) -> {
            byte[] worldBytes = PolarWriter.write(polarWorld);
            return saveWorld(worldBytes, path);
        }).exceptionally(e -> {
            e.printStackTrace();
            return false;
        });
    }

    /**
     * Save a polar world to a file
     *
     * @param world The bukkit World
     * @param path  The path to save the polar to (.polar extension recommended)
     * @return Whether it was successful
     */
    public static CompletableFuture<Boolean> saveWorld(World world, PolarWorld polarWorld, PolarGenerator polarGenerator, Path path) {
        return saveWorld(world, polarWorld, polarGenerator, path, ChunkSelector.all(), 0, 0);
    }

    /**
     * Save a polar world to a file
     *
     * @param worldBytes The bytes of the polar world
     * @param path       The path to save the polar to (.polar extension recommended)
     * @return Whether it was successful
     */
    public static boolean saveWorld(byte[] worldBytes, Path path) {
        if (worldBytes == null) return false;
        try {
            Files.write(path, worldBytes);
            return true;
        } catch (IOException e) {
            LOGGER.warning("Failed to save world to file");
            LOGGER.warning(e.toString());
            throw new RuntimeException(e);
        }
    }

    /**
     * Save a polar world
     * Runs updateChunkData on all polar chunks
     *
     * @param world The bukkit World
     * @return A CompletableFuture that completes once the world has finished updating
     * @see Polar#saveWorld(World, PolarWorld, PolarGenerator, Path)
     */
    public static CompletableFuture<Void> updateWorld(World world, PolarWorld polarWorld, PolarGenerator polarGenerator, ChunkSelector chunkSelector, int offsetX, int offsetZ) {
        List<PolarChunk> chunks = new ArrayList<>(polarWorld.chunks());
        List<CompletableFuture<Void>> futures = new ArrayList<>(chunks.size());
        for (PolarChunk chunk : chunks) {
            if (!chunkSelector.test(chunk.x(), chunk.z())) {
                polarWorld.removeChunkAt(chunk.x(), chunk.z());
                continue;
            }

            CompletableFuture<Void> future = new CompletableFuture<>();

            world.getChunkAtAsync(chunk.x() + offsetX, chunk.z() + offsetZ)
                    .thenAccept((c) -> {
                        Bukkit.getScheduler().runTaskAsynchronously(PolarPaper.getPlugin(), () -> {
                            updateChunkData(polarWorld, polarGenerator.getWorldAccess(), c, chunk.x(), chunk.z());
                            future.complete(null);
                        });
                    })
                    .exceptionally(e -> {
                        LOGGER.warning(e.toString());
                        return null;
                    });

            futures.add(future);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public static @Nullable World createPolarWorld(WorldCreator creator) {
        return createPolarWorld(creator, new Location(null, 0, 64, 0));
    }

    public static @Nullable World createPolarWorld(WorldCreator creator, Location spawnLocation) {
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

//        this.pluginManager.callEvent(new WorldLoadEvent(internal.getWorld()));

        return internal.getWorld();
    }

    public static void updateChunkData(PolarWorld polarWorld, PolarWorldAccess worldAccess, Chunk chunk, int newChunkX, int newChunkZ) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        if (craftChunk == null) {
            polarWorld.removeChunkAt(newChunkX, newChunkZ);
            return;
        }
        ChunkSnapshot snapshot = craftChunk.getChunkSnapshot(true, true, false, true);
        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        LevelChunk chunkAccess = (LevelChunk) craftChunk.getHandle(ChunkStatus.FULL);
        HashSet<Map.Entry<BlockPos, BlockEntity>> blockEntities = new HashSet<>(chunkAccess.blockEntities.entrySet());
        Entity[] entities = Arrays.copyOf(craftChunk.getEntities(), craftChunk.getEntities().length);

        int worldHeight = maxHeight - minHeight + 1; // I hate paper
        int sectionCount = worldHeight / 16;
        if (entities.length == 0 || entities.length == 1 && entities[0].getType() == EntityType.PLAYER) {
            boolean allEmpty = true;
            for (int i = 0; i < sectionCount; i++) {
                if (!snapshot.isSectionEmpty(i)) {
                    allEmpty = false;
                    break;
                }
            }
            if (allEmpty) {
                polarWorld.updateChunkAt(newChunkX, newChunkZ, new PolarChunk(newChunkX, newChunkZ, sectionCount));
                return;
            }
        }


        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();

        updateChunkData(polarWorld, worldAccess, snapshot, newChunkX, newChunkZ, minHeight, maxHeight, blockEntities, entities, registryAccess);
    }

    public static void updateChunkData(PolarWorld polarWorld, PolarWorldAccess worldAccess, ChunkSnapshot snapshot,
                                       int newChunkX, int newChunkZ, int minHeight, int maxHeight,
                                       Set<Map.Entry<BlockPos, BlockEntity>> blockEntities, Entity[] entities,
                                       RegistryAccess.Frozen registryAccess) {
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

            // Lighting Data
            byte[] blockLight = new byte[2048];
            byte[] skyLight = new byte[2048];

            for (int y = 0; y < 16; y++) {
                int chunkY = sectionY + y;

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x ++) {
                        // Conforms to Minestom's section lighting layout, which is used by Hollow Cube's Polar
                        // https://github.com/Minestom/Minestom/blob/master/src/main/java/net/minestom/server/instance/light/LightCompute.java#L98-L102
                        int pos = x | (z << 4) | (y << 8);
                        int shift = (pos & 1) << 2;
                        int idx = pos >>> 1;

                        blockLight[idx] |= (byte) (snapshot.getBlockEmittedLight(x, chunkY, z) << shift);
                        skyLight[idx] |= (byte) (snapshot.getBlockSkyLight(x, chunkY, z) << shift);
                    }
                }
            }

            sections[i] = new PolarSection(
                    blockPaletteStrings.toArray(new String[0]), blockData,
                    biomePalette.toArray(new String[0]), biomeData,
                    PolarSection.LightContent.calculateLightContent(blockLight), blockLight,
                    PolarSection.LightContent.calculateLightContent(skyLight), skyLight
            );
        }

        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities) {
            BlockPos blockPos = entry.getKey();
            BlockEntity blockEntity = entry.getValue();

            if (blockPos == null || blockEntity == null) {
                continue;
            }

            CompoundTag compoundTag = blockEntity.saveWithFullMetadata(registryAccess);

            CompoundBinaryTag nbt;
            try {
                nbt = TagStringIO.get().asCompound(compoundTag.toString());
            } catch (Exception e) {
                LOGGER.warning("Failed to save block entity data for " + blockPos);
                LOGGER.warning("Compound tag: " + compoundTag);
                throw new RuntimeException(e);
            }

            int index = CoordConversion.chunkBlockIndex(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            polarBlockEntities.add(new PolarChunk.BlockEntity(index, nbt.getString("id"), nbt));
        }

        int[][] heightMaps = new int[PolarChunk.MAX_HEIGHTMAPS][0];
        worldAccess.saveHeightmaps(snapshot, heightMaps);

        ByteArrayDataOutput userDataOutput = ByteStreams.newDataOutput();
        worldAccess.saveChunkData(snapshot, blockEntities, entities, userDataOutput);
        byte[] userData = userDataOutput.toByteArray();

        polarWorld.updateChunkAt(
                newChunkX,
                newChunkZ,
                new PolarChunk(
                        newChunkX,
                        newChunkZ,
                        sections,
                        polarBlockEntities,
                        null, // Entities
                        heightMaps,
                        userData
                )
        );
    }


}
