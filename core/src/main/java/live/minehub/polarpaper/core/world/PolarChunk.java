package live.minehub.polarpaper.core.world;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine;
import live.minehub.polarpaper.core.generator.ChunkUtils;
import live.minehub.polarpaper.core.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public record PolarChunk(
        int x,
        int z,
        PolarSection[] sections,
        BlockEntity[] blockEntities,
        int[] @Nullable [] heightmaps,
        byte[] userData
) {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolarChunk.class);

    public static final int HEIGHTMAP_NONE = 0b0;
    public static final int HEIGHTMAP_MOTION_BLOCKING = 0b1;
    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0b10;
    public static final int HEIGHTMAP_OCEAN_FLOOR = 0b100;
    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0b1000;
    public static final int HEIGHTMAP_WORLD_SURFACE = 0b10000;
    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0b100000;
    static final int[] HEIGHTMAPS = new int[]{
            HEIGHTMAP_NONE,
            HEIGHTMAP_MOTION_BLOCKING,
            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
            HEIGHTMAP_OCEAN_FLOOR,
            HEIGHTMAP_OCEAN_FLOOR_WG,
            HEIGHTMAP_WORLD_SURFACE,
            HEIGHTMAP_WORLD_SURFACE_WG,
    };
    static final int HEIGHTMAP_SIZE = 16 * 16; // Chunk Size X * Chunk Size Z
    static final int MAX_HEIGHTMAPS = 32;

    public int @Nullable [] heightmap(int type) {
        return heightmaps[type];
    }

    public boolean isEmpty() {
        for (PolarSection section : sections) {
            if (!section.empty()) return false;
        }
        return true;
    }

    public PolarChunk(int x, int z, int sectionCount) {
        // Blank chunk
        this(x, z, new PolarSection[sectionCount], new BlockEntity[0], new int[PolarChunk.MAX_HEIGHTMAPS][0], new byte[0]);
        Arrays.setAll(sections, _ -> new PolarSection());
    }

    public PolarChunk withUserData(byte[] newUserData) {
        return new PolarChunk(x, z, sections, blockEntities, heightmaps, newUserData);
    }

    public NoUnloadLevelChunk createLevelChunk(ServerLevel serverLevel) {
        int sectionCount = sections().length;
        SWMRNibbleArray[] blockNibbles = new SWMRNibbleArray[sectionCount + 2]; // light includes extra top and bottom section
        SWMRNibbleArray[] skyNibbles = new SWMRNibbleArray[sectionCount + 2];
        blockNibbles[0] = new SWMRNibbleArray();
        blockNibbles[sectionCount + 1] = new SWMRNibbleArray();
        skyNibbles[0] = new SWMRNibbleArray();
        skyNibbles[sectionCount + 1] = new SWMRNibbleArray();
        boolean lightPresent = false;
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            PolarSection polarSection = sections()[i];
            if ((polarSection.skyLightContent() != PolarSection.LightContent.MISSING || polarSection.blockLightContent() != PolarSection.LightContent.MISSING)) lightPresent = true;
            LevelChunkSection section = polarSection.createLevelChunkSection(serverLevel.registryAccess());
            levelChunkSections[i] = section;
            skyNibbles[i + 1] = polarSection.skyLight();
            blockNibbles[i + 1] = polarSection.blockLight();

        }
        NoUnloadLevelChunk chunk = new NoUnloadLevelChunk(serverLevel, new ChunkPos(x, z), UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, levelChunkSections, null, null);

        if (lightPresent) {
            @Nullable Boolean[] emptinessMap = StarLightEngine.getEmptySectionsForChunk(chunk);
            boolean[] emptinessMapPrim = new boolean[emptinessMap.length];
            for (int i = 0; i < emptinessMap.length; i++) {
                Boolean bool = emptinessMap[i];
                emptinessMapPrim[i] = bool != null && bool;
            }
            chunk.starlight$setBlockEmptinessMap(emptinessMapPrim);
            chunk.starlight$setSkyEmptinessMap(emptinessMapPrim);
            chunk.starlight$setSkyNibbles(skyNibbles);
            chunk.starlight$setBlockNibbles(blockNibbles);
        } else {
            ChunkUtils.lightChunk(serverLevel, chunk);
        }
        chunk.setLightCorrect(true);

        return chunk;
    }

    public record BlockEntity(
            int index,
            @Nullable String id,
            @Nullable CompoundTag data
    ) {

    }

    /**
     * Converts a bukkit world chunk to a polar chunk without light data
     * @param world The bukkit world
     * @param chunkX The X coordinate of the chunk in the bukkit world
     * @param chunkZ The Z coordinate of the chunk in the bukkit world
     * @param blockSelector Used to filter which blocks are converted
     * @param loadChunks Whether to load chunks
     * @return The new PolarChunk, null if bukkit world chunk is empty
     */
    public static CompletableFuture<@Nullable PolarChunk> convert(World world, int chunkX, int chunkZ, PolarWorldAccess worldAccess, BlockSelector blockSelector, boolean loadChunks) {
        return convert(world, chunkX, chunkZ, worldAccess, blockSelector, false, loadChunks);
    }

    /**
     * Converts a bukkit world chunk to a polar chunk
     * @param world The bukkit world
     * @param chunkX The X coordinate of the chunk in the bukkit world
     * @param chunkZ The Z coordinate of the chunk in the bukkit world
     * @param blockSelector Used to filter which blocks are converted
     * @param saveLight Whether to save light data
     * @param loadChunks Whether to load chunks
     * @return The new PolarChunk, null if bukkit world chunk is empty
     */
    public static CompletableFuture<@Nullable PolarChunk> convert(World world, int chunkX, int chunkZ, PolarWorldAccess worldAccess, BlockSelector blockSelector, boolean saveLight, boolean loadChunks) {
        CompletableFuture<@Nullable PolarChunk> future = new CompletableFuture<>();

        ServerLevel serverLevel = ((CraftWorld) world).getHandle();

        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder != null && chunkHolder.getCurrentChunk() != null) {
            if (isChunkEmpty(chunkHolder)) return CompletableFuture.completedFuture(null);

            ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();
            CompletableFuture.supplyAsync(() -> convert(world, chunkHolder.getCurrentChunk(), entityChunk, worldAccess, blockSelector, saveLight))
                    .thenAccept(a -> a.thenAccept(future::complete))
                    .exceptionally(e -> {
                        LOGGER.info("Failed to convert world", e);
                        return null;
                    });
            return future;
        }

        // Chunk is not already loaded

        if (!loadChunks) return CompletableFuture.completedFuture(null);

        // FULL required when saving light; FEATURES sufficient otherwise
        ChunkStatus status = saveLight ? ChunkStatus.FULL : ChunkStatus.FEATURES;
        serverLevel.moonrise$getChunkTaskScheduler().scheduleChunkLoad(chunkX, chunkZ, status, true, Priority.LOW, chunkAccess -> {
            NewChunkHolder chunkHolder2 = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
            if (isChunkEmpty(chunkHolder2)) {
                future.complete(null);
                return;
            }

            ChunkEntitySlices entityChunk = chunkHolder2.getEntityChunk();

            CompletableFuture.supplyAsync(() -> convert(world, chunkAccess, entityChunk, worldAccess, blockSelector, saveLight))
                    .thenAccept(a -> a.thenAccept(future::complete))
                    .exceptionally(e -> {
                        LOGGER.info("Failed to convert world", e);
                        return null;
                    });
        });

        return future;
    }

    public static CompletableFuture<@Nullable PolarChunk> convert(World world, ChunkAccess chunkAccess, @Nullable ChunkEntitySlices entityChunk, PolarWorldAccess worldAccess, BlockSelector blockSelector, boolean saveLight) {
        int chunkX = chunkAccess.locX;
        int chunkZ = chunkAccess.locZ;
        ServerLevel serverLevel = ((CraftWorld) world).getHandle();
        LevelLightEngine lightEngine = saveLight ? serverLevel.getLightEngine() : null;

        Registry<Biome> biomeRegistry = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);

        int sectionCount = chunkAccess.getSectionsCount();
        int minSection = chunkAccess.getMinSectionY();

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            LevelChunkSection chunkAccessSection = chunkAccess.getSection(i);
            sections[i] = convertSection(chunkX, chunkZ, chunkAccessSection, biomeRegistry, blockSelector, minSection, i, lightEngine);
        }

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        List<PolarChunk.BlockEntity> polarBlockEntities = new ArrayList<>();
        Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> blockEntities = new HashMap<>();

        CompletableFuture<Void> future = new CompletableFuture<>();
        FoliaUtil.scheduleOnRegionIfFolia(worldAccess.getPlugin(), world, chunkX, chunkZ, () -> {
            try {
                for (BlockPos blockPos : chunkAccess.getBlockEntitiesPos()) {
                    net.minecraft.world.level.block.entity.BlockEntity blockEntity = getBlockEntity(chunkAccess, blockPos);

                    if (blockEntity == null) continue;
                    if (!blockSelector.test(blockPos.getX(), blockPos.getY(), blockPos.getZ())) continue;

                    CompoundTag compoundTag = blockEntity.saveWithFullMetadata(registryAccess);

                    Optional<String> id = compoundTag.getString("id");
                    if (id.isEmpty()) {
                        LOGGER.warn("No ID in block entity data at: {}", blockPos);
                        LOGGER.warn("Compound tag: {}", compoundTag);
                        continue;
                    }

                    int index = CoordConversion.chunkBlockIndex(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                    polarBlockEntities.add(new BlockEntity(index, id.get(), compoundTag));
                    blockEntities.put(blockPos, blockEntity);
                }

                future.complete(null);
            } catch (Exception e) {
                // the future is what the rest of the conversion waits on, so it has to be completed either way
                future.completeExceptionally(e);
            }
        });

        int[][] heightMaps = new int[PolarChunk.MAX_HEIGHTMAPS][0];
        worldAccess.saveHeightmaps(chunkAccess, heightMaps);

        List<net.minecraft.world.entity.Entity> allEntities = entityChunk == null ? List.of() : entityChunk.getAllEntities();
        List<org.bukkit.entity.Entity> newAllEntities = new ArrayList<>();
        for (net.minecraft.world.entity.Entity ent : allEntities) {
            if (blockSelector.test(ent.getBlockX(), ent.getBlockY(), ent.getBlockZ())) newAllEntities.add(ent.getBukkitEntity());
        }
        org.bukkit.entity.Entity[] entitiesArray = newAllEntities.toArray(new org.bukkit.entity.Entity[0]);

        byte[] userData;
        try (var writer = new MemorySegmentWriter(256)) {
            worldAccess.saveChunkData(chunkAccess, blockEntities, entitiesArray, writer);
            userData = writer.getWrittenBytes();
        }

        return future.thenApply(_ -> new PolarChunk(
                chunkX,
                chunkZ,
                sections,
                polarBlockEntities.toArray(new BlockEntity[0]),
                heightMaps,
                userData
        )).exceptionally(e -> {
            LOGGER.error("Failed to convert chunk", e);
            return null;
        });
    }

    /**
     * Reads a block entity out of a chunk that is being converted
     * <p>
     * While the server is stopping this goes straight to the chunk's own block entities. The captured block entities
     * that {@link LevelChunk#getBlockEntity} looks at first live in region data on Folia, which the thread stopping
     * the server cannot reach, and nothing is capturing block entities by then anyway
     */
    private static net.minecraft.world.level.block.entity.@Nullable BlockEntity getBlockEntity(ChunkAccess chunkAccess, BlockPos blockPos) {
        if (ShutdownExecutor.isRunning() && chunkAccess instanceof LevelChunk levelChunk) {
            return levelChunk.getBlockEntities().get(blockPos);
        }
        return chunkAccess.getBlockEntity(blockPos);
    }

    private static PolarSection convertSection(int chunkX, int chunkZ, LevelChunkSection chunkAccessSection, Registry<Biome> biomeRegistry, BlockSelector blockSelector, int minSection, int sectionI, @Nullable LevelLightEngine lightEngine) {
        if (chunkAccessSection.hasOnlyAir()) return createEmptySection(chunkX, chunkZ, minSection, sectionI, lightEngine);

        PalettedContainer<BlockState> states = chunkAccessSection.getStates().copy();
        PalettedContainer<Holder<Biome>> biomes = chunkAccessSection.getBiomes().copy();

        int blocksSize = states.data.storage().getSize();
        int biomesSize = biomes.data.storage().getSize();

        // blocks
        Map<BlockState, Integer> blockIds = new LinkedHashMap<>();
        int[] blockInts = new int[blocksSize];
        for (int index = 0; index < blocksSize; index++) {
            BlockState state = blockSelector.test(index, chunkX, chunkZ, minSection + sectionI)
                    ? states.get(index)
                    : Blocks.AIR.defaultBlockState();
            blockInts[index] = blockIds.computeIfAbsent(state, _ -> blockIds.size());
        }
        String[] blockPalette = new String[blockIds.size()];
        int p = 0;
        for (BlockState state : blockIds.keySet()) {
            blockPalette[p++] = BlockCodec.stringFromBlock(state);
        }
        long[] blockData = blockIds.size() == 1 ? null : PaletteUtil.pack(blockInts, Mth.ceillog2(blockIds.size()));

        // biomes
        Map<Holder<Biome>, Integer> biomeIds = new LinkedHashMap<>();
        int[] biomeInts = new int[biomesSize];
        for (int index = 0; index < biomesSize; index++) {
            Holder<Biome> biome = biomes.get(index);
            biomeInts[index] = biomeIds.computeIfAbsent(biome, _ -> biomeIds.size());
        }
        String[] biomePalette = new String[biomeIds.size()];
        p = 0;
        for (Holder<Biome> biome : biomeIds.keySet()) {
            biomePalette[p++] = biomeRegistry.getKey(biome.value()).toString();
        }
        long[] biomeData = biomeIds.size() == 1 ? null : PaletteUtil.pack(biomeInts, Mth.ceillog2(biomeIds.size()));

        PolarSection.LightContent blockLightContent = PolarSection.LightContent.MISSING;
        PolarSection.LightContent skyLightContent = PolarSection.LightContent.MISSING;
        SWMRNibbleArray blockLight = null;
        SWMRNibbleArray skyLight = null;

        if (lightEngine != null) {
            DataLayer skyLightArray = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkX, minSection + sectionI, chunkZ));
            DataLayer blockLightArray = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkX, minSection + sectionI, chunkZ));

            if (skyLightArray != null) {
                skyLightContent = LightUtil.getLightContent(skyLightArray);
                skyLight = LightUtil.getLightNibble(skyLightContent);
                if (skyLight == null) skyLight = new SWMRNibbleArray(skyLightArray.getData());
            }
            if (blockLightArray != null) {
                blockLightContent = LightUtil.getLightContent(blockLightArray);
                blockLight = LightUtil.getLightNibble(blockLightContent);
                if (blockLight == null) blockLight = new SWMRNibbleArray(blockLightArray.getData());
            }
        }

        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

    public static boolean isChunkEmpty(NewChunkHolder chunkHolder) {
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();

        // if any entities that should be saved, return false
        if (entityChunk != null) {
            for (net.minecraft.world.entity.Entity nmsEntity : entityChunk.getAllEntities()) {
                if (!nmsEntity.shouldBeSaved()) continue;
                return false;
            }
        }

        // if any non-air sections, return false
        for (LevelChunkSection section : chunkAccess.getSections()) {
            if (section.hasOnlyAir()) continue;
            return false;
        }

        return true;
    }

    private static PolarSection createEmptySection(int chunkX, int chunkZ, int minSection, int sectionI, @Nullable LevelLightEngine lightEngine) {
        if (lightEngine == null) return new PolarSection();

        PolarSection.LightContent blockLightContent = PolarSection.LightContent.MISSING;
        PolarSection.LightContent skyLightContent = PolarSection.LightContent.MISSING;
        SWMRNibbleArray blockLight = null;
        SWMRNibbleArray skyLight = null;

        DataLayer skyLightArray = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkX, minSection + sectionI, chunkZ));
        DataLayer blockLightArray = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkX, minSection + sectionI, chunkZ));

        if (skyLightArray != null) {
            skyLightContent = LightUtil.getLightContent(skyLightArray);
            skyLight = LightUtil.getLightNibble(skyLightContent);
            if (skyLight == null) skyLight = new SWMRNibbleArray(skyLightArray.getData());
        }
        if (blockLightArray != null) {
            blockLightContent = LightUtil.getLightContent(blockLightArray);
            blockLight = LightUtil.getLightNibble(blockLightContent);
            if (blockLight == null) blockLight = new SWMRNibbleArray(blockLightArray.getData());
        }

        return new PolarSection(
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

}