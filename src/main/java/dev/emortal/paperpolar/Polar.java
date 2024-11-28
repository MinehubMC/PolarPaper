package dev.emortal.paperpolar;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import dev.emortal.paperpolar.util.CoordConversion;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class Polar {

    private static final Logger LOGGER = Logger.getLogger(Polar.class.getName());

    private Polar() {

    }

    /**
     * Manually load a polar world
     * @param world The polar world
     * @param worldName The name for the polar world
     */
    public static void loadWorld(@NotNull PolarWorld world, @NotNull String worldName) {
        FileConfiguration fileConfig = Main.getPlugin().getConfig();
        Config config = Config.readFromConfig(fileConfig, worldName);
        if (config == null) {
            LOGGER.warning("Polar world '" + worldName + "' has an invalid config, skipping.");
            return;
        }

        if (Bukkit.getWorld(worldName) != null) {
            LOGGER.warning("A world with the name '" + worldName + "' already exists, skipping.");
            return;
        }

        PolarGenerator polar = new PolarGenerator(world);
        PolarBiomeProvider polarBiomeProvider = new PolarBiomeProvider(world);

        WorldCreator worldCreator = WorldCreator.name(worldName)
                .type(config.worldType())
                .environment(config.environment())
                .generator(polar)
                .biomeProvider(polarBiomeProvider)
                .keepSpawnLoaded(TriState.FALSE);

        World newWorld = Polar.createPolarWorld(worldCreator);
        if (newWorld == null) {
            LOGGER.warning("An error occurred loading polar world '" + worldName + "', skipping.");
            return;
        }

        newWorld.setDifficulty(config.difficulty());
        newWorld.setPVP(config.pvp());
        newWorld.setSpawnFlags(config.allowMonsters(), config.allowAnimals());
        newWorld.setAutoSave(false);
//        newWorld.setAutoSave(config.autoSave());
    }

    public static boolean saveWorldConfigSource(World world) {
        PolarWorld polarWorld = PolarWorld.fromWorld(world);
        if (polarWorld == null) return false;
        PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
        if (polarGenerator == null) return false;

        String worldName = world.getName();

        FileConfiguration fileConfig = Main.getPlugin().getConfig();
        if (!fileConfig.isSet("worlds." + worldName)) return false; // Save world from config but config is not set
        Config config = Config.readFromConfig(fileConfig, worldName);
        if (config == null) {
            LOGGER.warning("Polar world '" + worldName + "' has an invalid config, skipping.");
            return false;
        }

        Path pluginFolder = Path.of(Main.getPlugin().getDataFolder().getAbsolutePath());
        Path worldsFolder = pluginFolder.resolve("worlds");

        switch (config.source()) {
            case "file" -> {
                return saveWorld(world, worldsFolder.resolve(worldName + ".polar"));
            }
            // TODO: mysql?
            default -> {
                return false;
            }
        }
    }

    /**
     * Save a polar world to a file
     * @param world The bukkit World
     * @param path The path to save the polar to (.polar extension recommended)
     * @return Whether it was successful
     */
    public static boolean saveWorld(World world, Path path) {
        byte[] worldBytes = saveWorld(world);
        if (worldBytes == null) return false;
        try {
            Files.write(path, worldBytes);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save a polar world
     * @param world The bukkit World
     * @return The byte array of the polar world, or null if it was not a polar world
     * @see Polar#saveWorld(World, Path)
     */
    public static byte @Nullable [] saveWorld(World world) {
        PolarWorld polarWorld = PolarWorld.fromWorld(world);
        if (polarWorld == null) return null;
        PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
        if (polarGenerator == null) return null;

        for (PolarChunk chunk : polarWorld.chunks()) {
            updateChunkData(polarWorld, polarGenerator.getWorldAccess(), world.getChunkAt(chunk.x(), chunk.z())); // TODO: async?
        }

        return PolarWriter.write(polarWorld);
    }

    public static @Nullable World createPolarWorld(WorldCreator creator) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();

        // Check if already existing
        if (craftServer.getWorld(creator.name()) != null) {
            return null;
        }

        String name = creator.name();
        ChunkGenerator generator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();

        ResourceKey<LevelStem> actualDimension;
        switch (creator.environment()) {
            case NORMAL:
                actualDimension = LevelStem.OVERWORLD;
                break;
            case NETHER:
                actualDimension = LevelStem.NETHER;
                break;
            case THE_END:
                actualDimension = LevelStem.END;
                break;
            default:
                throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        }

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

        internal.setSpawnSettings(true);
        // Paper - Put world into worldlist before initing the world; move up

        craftServer.getServer().prepareLevels(internal.getChunkSource().chunkMap.progressListener, internal);
        // Paper - rewrite chunk system

//        this.pluginManager.callEvent(new WorldLoadEvent(internal.getWorld()));

        return internal.getWorld();
    }

    public static void updateChunkData(PolarWorld polarWorld, PolarWorldAccess worldAccess, Chunk chunk) {
        updateChunkData(polarWorld, worldAccess, chunk, chunk.getX(), chunk.getZ());
    }
    public static void updateChunkData(PolarWorld polarWorld, PolarWorldAccess worldAccess, Chunk chunk, int newChunkX, int newChunkZ) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        LevelChunk chunkAccess = (LevelChunk) craftChunk.getHandle(ChunkStatus.FULL);
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, true, false, true);

        List<PolarChunk.BlockEntity> blockEntities = new ArrayList<>();

        int worldHeight = chunk.getWorld().getMaxHeight() - chunk.getWorld().getMinHeight();
        int sectionCount = worldHeight / 16;

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            int sectionY = chunk.getWorld().getMinHeight() + i * 16;

            // Blocks
            int[] blockData = new int[4096];
            List<String> blockPalette = new ArrayList<>();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int sectionLocalY = sectionY + y;
                        int blockIndex = x + y * 16 * 16 + z * 16;

                        BlockData data = snapshot.getBlockData(x, sectionLocalY, z);
                        String blockString = data.getAsString();
                        int paletteId = blockPalette.indexOf(blockString);
                        if (paletteId == -1) {
                            paletteId = blockPalette.size();
                            blockPalette.add(blockString);
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
                        Biome biome = snapshot.getBiome(x*4, sectionY + y*4, z*4);
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
                    blockPalette.toArray(new String[0]), blockData,
                    biomePalette.toArray(new String[0]), biomeData,
                    PolarSection.LightContent.MISSING, null, // TODO: Provide block light
                    PolarSection.LightContent.MISSING, null
            );
        }

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();

        for (Map.Entry<BlockPos, BlockEntity> entry : chunkAccess.blockEntities.entrySet()) {
            BlockPos blockPos = entry.getKey();
            BlockEntity blockEntity = entry.getValue();
            CompoundTag compoundTag = blockEntity.saveWithFullMetadata(registryAccess);

            CompoundBinaryTag nbt;
            try {
                nbt = TagStringIO.get().asCompound(compoundTag.getAsString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            int index = CoordConversion.chunkBlockIndex(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            blockEntities.add(new PolarChunk.BlockEntity(index, nbt.getString("id"), nbt));
        }

        int[][] heightMaps = new int[PolarChunk.MAX_HEIGHTMAPS][0];
        worldAccess.saveHeightmaps(chunk, heightMaps);

        ByteArrayDataOutput userDataOutput = ByteStreams.newDataOutput();
        worldAccess.saveChunkData(chunk, userDataOutput);
        byte[] userData = userDataOutput.toByteArray();

        List<PolarChunk.Entity> polarEntities = new ArrayList<>();
        for (@NotNull Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.PLAYER) continue;
            byte[] entityBytes = Bukkit.getUnsafe().serializeEntity(entity);

            Location entityPos = entity.getLocation();
            polarEntities.add(new PolarChunk.Entity(
                    ((entityPos.x() % 16) + 16) % 16,
                    entityPos.y(),
                    ((entityPos.z() % 16) + 16) % 16,
                    entityPos.getYaw(),
                    entityPos.getPitch(),
                    entityBytes
            ));
        }

        polarWorld.updateChunkAt(
                newChunkX,
                newChunkZ,
                new PolarChunk(
                        newChunkX,
                        newChunkZ,
                        sections,
                        blockEntities,
                        polarEntities,
                        heightMaps,
                        userData
                )
        );
    }

}
