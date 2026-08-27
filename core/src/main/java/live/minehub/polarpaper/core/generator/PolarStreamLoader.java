package live.minehub.polarpaper.core.generator;

import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import com.mojang.logging.LogUtils;
import io.papermc.paper.FeatureHooks;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.userdata.WorldUserData;
import live.minehub.polarpaper.core.util.CoordConversion;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.TaskFutures;
import live.minehub.polarpaper.core.world.*;
import net.kyori.adventure.builder.AbstractBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PolarStreamLoader extends PolarGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolarStreamLoader.class);

    private static final MethodHandle GET_OR_CREATE_CHUNK_HOLDER_HANDLE;
    private static final VarHandle CURRENT_CHUNK_HANDLE;
    private static final VarHandle CURRENT_GEN_STATUS_HANDLE;
    private static final VarHandle CHUNK_COMPLETIONS_HANDLE;
    private static final VarHandle LAST_CHUNK_COMPLETION_HANDLE;
    private static final VarHandle ENTITY_CHUNK_HANDLE;
    private static final VarHandle CHUNK_COMPLETION_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(NewChunkHolder.ChunkCompletion[].class);
    private static final ChunkStatus[] ALL_STATUSES = ChunkStatus.getStatusList().toArray(new ChunkStatus[0]);

    static {
        try {
            GET_OR_CREATE_CHUNK_HOLDER_HANDLE = MethodHandles
                    .privateLookupIn(ChunkHolderManager.class, MethodHandles.lookup())
                    .findVirtual(ChunkHolderManager.class, "getOrCreateChunkHolder", MethodType.methodType(NewChunkHolder.class, int.class, int.class));

            CURRENT_CHUNK_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "currentChunk", ChunkAccess.class);
            CURRENT_GEN_STATUS_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "currentGenStatus", ChunkStatus.class);
            CHUNK_COMPLETIONS_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "chunkCompletions", NewChunkHolder.ChunkCompletion[].class);
            LAST_CHUNK_COMPLETION_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "lastChunkCompletion", NewChunkHolder.ChunkCompletion.class);
            ENTITY_CHUNK_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "entityChunk", ChunkEntitySlices.class);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final @NotNull PolarDataConverter dataConverter;
    private short version;
    private int dataVersion;
    private byte[] userData = new byte[0];
    public PolarStreamLoader(@NotNull Config config, @Nullable PolarSource polarSource, @NotNull PolarWorldAccess worldAccess, @NotNull PolarDataConverter dataConverter) {
        super(config, polarSource, worldAccess);
        this.dataConverter = dataConverter;

        this.dataVersion = dataConverter.defaultDataVersion();
    }

    //https://github.com/hollow-cube/polar/blob/main/src/main/java/net/hollowcube/polar/StreamingPolarLoader.java#L64
    public void load(@NotNull World world) throws IOException {
        if (getSource() == null) return;

        try (Arena dstArena = Arena.ofConfined()) {
            final MemorySegment dst;

            dev.hallock.zstd.Zstd zstd = dev.hallock.zstd.Zstd.zstd();
            try (Arena srcArena = Arena.ofConfined();
                 ReadableByteChannel channel = getSource().read()) {

                long fileSize = getSource().size();

                final MemorySegment src;

                if (channel instanceof FileChannel fileChannel) {
                    src = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileSize, srcArena);
                } else {
                    final MemorySegment segment = srcArena.allocate(fileSize);
                    long offset = 0L; // readFully, but for large files
                    while (offset < fileSize) {
                        long n = channel.read(segment.asSlice(offset, fileSize - offset).asByteBuffer());
                        if (n < 0) {
                            throw new EOFException("Unexpected EOF: expected " + fileSize + " bytes, got " + offset);
                        }
                        offset += n;
                    }
                    src = segment.asReadOnly();
                }

                MemorySegmentReader reader = new MemorySegmentReader(src);

                var magic = reader.readInt();
                assertThat(magic == PolarConstants.MAGIC_NUMBER, "Invalid magic number");

                this.version = reader.readShort();
                PolarReader.validateVersion(version);

                this.dataVersion = reader.readVarInt();

                var compressionByte = reader.readByte();
                PolarWorld.CompressionType compression = PolarWorld.CompressionType.fromId(compressionByte);
                assertThat(compression != null, "Invalid compression type");

                int dataLength = reader.readVarInt();

                switch (compression) {
                    case NONE -> {
                        readData(src.asSlice(reader.getOffset()), world);
                        return;
                    }
                    // src should be unreachable following the dst copy.
                    case ZSTD -> {
                        var decompression = dstArena.allocate(dataLength);
                        zstd.decompress(decompression, dataLength, src.asSlice(reader.getOffset()), fileSize - reader.getOffset());
                        dst = decompression.asReadOnly();
                    }
                    default -> throw new UnsupportedOperationException(
                            "Unsupported compression type: " + compression
                    );
                }

            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
            // Now we can just read the dst buffer without having to worry about the extra footprint of src
            readData(dst, world);
        }
    }

    private void readData(MemorySegment segment, World world) {
        MemorySegmentReader reader = new MemorySegmentReader(segment);

        byte minSection = reader.readByte();
        byte maxSection = reader.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        this.userData = reader.readByteArray();

        int chunkCount = reader.readVarInt();
        for (int i = 0; i < chunkCount; i++) {
            readChunk(reader, world, maxSection - minSection + 1);
        }
    }

    private CompletableFuture<Void> readChunk(MemorySegmentReader reader, World world, int sectionCount) {
        var chunkX = reader.readVarInt();
        var chunkZ = reader.readVarInt();

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();

        SWMRNibbleArray[] blockNibbles = new SWMRNibbleArray[sectionCount + 2]; // light includes extra top and bottom section
        SWMRNibbleArray[] skyNibbles = new SWMRNibbleArray[sectionCount + 2];
        blockNibbles[0] = new SWMRNibbleArray();
        blockNibbles[sectionCount + 1] = new SWMRNibbleArray();
        skyNibbles[0] = new SWMRNibbleArray();
        skyNibbles[sectionCount + 1] = new SWMRNibbleArray();
        boolean lightPresent = false;
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            PolarSection polarSection = PolarReader.readSection(dataConverter, dataVersion, reader);
            if (!lightPresent && (polarSection.skyLightContent() != PolarSection.LightContent.MISSING || polarSection.blockLightContent() != PolarSection.LightContent.MISSING)) lightPresent = true;

            try {
                LevelChunkSection section = polarSection.createLevelChunkSection(serverLevel.registryAccess());
                levelChunkSections[i] = section;
                skyNibbles[i + 1] = new SWMRNibbleArray(polarSection.skyLight());
                blockNibbles[i + 1] = new SWMRNibbleArray(polarSection.blockLight());
            } catch (Exception e) {
                LOGGER.error("Failed to load chunk at {} {} (section {}/{}) in {}", chunkX, chunkZ, i, sectionCount, world.getKey());
//                throw e;
            }

        }

        NoUnloadLevelChunk newLevelChunk = new NoUnloadLevelChunk(serverLevel, new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, levelChunkSections, null, null);

        Bukkit.getRegionScheduler().execute(getWorldAccess().getPlugin(), world, chunkX, chunkZ, newLevelChunk::tryMarkSaved);

        int blockEntityCount = reader.readVarInt();
        for (int i = 0; i < blockEntityCount; i++) {
            PolarChunk.BlockEntity polarBlockEntity = PolarReader.readBlockEntity(dataConverter, dataVersion, reader);
            addBlockEntity(polarBlockEntity, newLevelChunk);
        }

        var heightmaps = PolarReader.readHeightmaps(reader);

        byte[] userData = reader.readByteArray();

        boolean finalLightPresent = lightPresent;

        return TaskFutures.runRegion(getWorldAccess().getPlugin(), world, chunkX, chunkZ, () -> {
            if (finalLightPresent) {
                Boolean[] emptinessMap = StarLightEngine.getEmptySectionsForChunk(newLevelChunk);
                boolean[] emptinessMapPrim = new boolean[emptinessMap.length];
                for (int i = 0; i < emptinessMap.length; i++) {
                    Boolean bool = emptinessMap[i];
                    emptinessMapPrim[i] = bool != null && bool;
                }
                newLevelChunk.starlight$setBlockEmptinessMap(emptinessMapPrim);
                newLevelChunk.starlight$setSkyEmptinessMap(emptinessMapPrim);
                newLevelChunk.starlight$setSkyNibbles(skyNibbles);
                newLevelChunk.starlight$setBlockNibbles(blockNibbles);
            } else {
                lightChunk(serverLevel, newLevelChunk);
            }
            newLevelChunk.setLightCorrect(true);

            insertChunk(serverLevel, newLevelChunk);
            getWorldAccess().loadChunkData(world, newLevelChunk, userData);

            return null;
        });
    }

    public static void insertChunk(ServerLevel serverLevel, NoUnloadLevelChunk newLevelChunk) {
        int chunkX = newLevelChunk.locX;
        int chunkZ = newLevelChunk.locZ;
        ChunkTaskScheduler chunkTaskScheduler = serverLevel.moonrise$getChunkTaskScheduler();
        ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        // Begin reflection hell :D
        ReentrantAreaLock.Node lock = chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ);
        ReentrantAreaLock.Node lock1 = chunkTaskScheduler.schedulingLockArea.lock(chunkX, chunkZ);
        NewChunkHolder newChunkHolder;
        try {
            newChunkHolder = (NewChunkHolder) GET_OR_CREATE_CHUNK_HOLDER_HANDLE.invoke(chunkHolderManager, chunkX, chunkZ);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            chunkTaskScheduler.schedulingLockArea.unlock(lock1);
            chunkHolderManager.ticketLockArea.unlock(lock);
        }

        newLevelChunk.needsDecoration = false;
        newLevelChunk.mustNotSave = true;
        CURRENT_CHUNK_HANDLE.set(newChunkHolder, newLevelChunk);
        CURRENT_GEN_STATUS_HANDLE.set(newChunkHolder, ChunkStatus.FULL);
        newLevelChunk.moonrise$setChunkHolder(newChunkHolder);

        // Populate every status up to and including FULL
        // This mirrors what replaceProtoChunk() does, but for all statuses including FULL
        NewChunkHolder.ChunkCompletion[] chunkCompletions = (NewChunkHolder.ChunkCompletion[]) CHUNK_COMPLETIONS_HANDLE.get(newChunkHolder);
        for (ChunkStatus status : ALL_STATUSES) {
            NewChunkHolder.ChunkCompletion completion = new NewChunkHolder.ChunkCompletion(newLevelChunk, status);
            CHUNK_COMPLETION_ARRAY_HANDLE.setVolatile(chunkCompletions, status.getIndex(), completion);

            if (status == ChunkStatus.FULL) {
                LAST_CHUNK_COMPLETION_HANDLE.set(newChunkHolder, completion);
            }
        }

        CompletableFuture.runAsync(() -> {
            Heightmap.primeHeightmaps(newLevelChunk, ChunkStatus.FULL.heightmapsAfter());
        });

        newLevelChunk.setFullStatus(() -> FullChunkStatus.ENTITY_TICKING);
        newLevelChunk.runPostLoad();
        newLevelChunk.setLoaded(true);
        newLevelChunk.registerAllBlockEntitiesAfterLevelLoad();
        newLevelChunk.registerTickContainerInLevel(serverLevel);

        initializeEntityChunk(newChunkHolder);
    }

    public static void lightChunk(ServerLevel level, LevelChunk chunk) {
        ThreadedLevelLightEngine threadedEngine = (ThreadedLevelLightEngine) level.getLightEngine();
        StarLightInterface starlight = threadedEngine.starlight$getLightEngine();
        starlight.lightChunk(chunk, StarLightEngine.getEmptySectionsForChunk(chunk));
    }

    private static ChunkEntitySlices initializeEntityChunk(NewChunkHolder holder) {
        ChunkEntitySlices slices = new ChunkEntitySlices(
                holder.world, holder.chunkX, holder.chunkZ, holder.getChunkStatus(),
                holder.holderData, WorldUtil.getMinSection(holder.world), WorldUtil.getMaxSection(holder.world)
        );
        slices.setTransient(false);

        ENTITY_CHUNK_HANDLE.set(holder, slices);

        holder.world.moonrise$getEntityLookup().entitySectionLoad(holder.chunkX, holder.chunkZ, slices);

        return slices;
    }

    public static void addBlockEntity(PolarChunk.BlockEntity polarBlockEntity, ChunkAccess chunk) {
        int posIndex = polarBlockEntity.index();
        CompoundTag nbt = polarBlockEntity.data();

        int x = CoordConversion.chunkBlockIndexGetX(posIndex);
        int y = CoordConversion.chunkBlockIndexGetY(posIndex);
        int z = CoordConversion.chunkBlockIndexGetZ(posIndex);

        BlockState blockState = chunk.getBlockState(x, y, z);
        BlockPos blockPos = new BlockPos(chunk.locX * 16 + x, y, chunk.locZ * 16 + z);

        if (!(blockState.getBlock() instanceof EntityBlock entityBlock)) {
//            PolarPaper.logger().warning("Block " + blockState + " does not have a block entity");
//            throw new IllegalArgumentException("Block " + blockState + " does not have a block entity");
            return;
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(blockPos, blockState);
        if (blockEntity == null) {
//            PolarPaper.logger().warning("Block " + blockState + " returned null block entity");
//            throw new IllegalArgumentException("Block " + blockState + " returned null block entity");
            return;
        }

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();

        // Load NBT data into the block entity
        ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "addBlockEntity", LogUtils.getLogger());
        blockEntity.loadWithComponents(TagValueInput.create(problemReporter, registryAccess, nbt));

        if (chunk instanceof LevelChunk levelChunk) {
            blockEntity.setLevel(levelChunk.getLevel());
            levelChunk.addAndRegisterBlockEntity(blockEntity);
        } else {
            chunk.blockEntities.put(blockPos, blockEntity);
        }

    }

    public static CompletableFuture<Void> insertEmptyChunks(Plugin plugin, ServerLevel level) {
        int paddedViewDist = FeatureHooks.getViewDistance(level) + 2;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int x = -paddedViewDist; x <= paddedViewDist; x++) {
            for (int z = -paddedViewDist; z <= paddedViewDist; z++) {
                int finalX = x;
                int finalZ = z;
                CompletableFuture<Void> future = TaskFutures.runRegion(plugin, level.getWorld(), x, z, () -> {
                    NoUnloadLevelChunk emptyChunk = createEmptyChunk(level, finalX, finalZ);
                    insertChunk(level, emptyChunk);
                    return null;
                });

                futures.add(future);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private static NoUnloadLevelChunk createEmptyChunk(ServerLevel level, int x, int z) {
        NoUnloadLevelChunk emptyChunk = new NoUnloadLevelChunk(level, new ChunkPos(x, z));
        boolean[] emptySections = new boolean[emptyChunk.getSectionsCount()];
        Arrays.fill(emptySections, true);
        emptyChunk.starlight$setBlockEmptinessMap(emptySections.clone());
        emptyChunk.starlight$setSkyEmptinessMap(emptySections);
        emptyChunk.setLightCorrect(true);
        return emptyChunk;
    }

    @Override
    public @Nullable PolarWorld getPolarWorld() {
        return null;
    }

    @Override
    public Component getInfoComponent(World world) {
        Vector3i offset = WorldUserData.readSchematicOffset(userData);

        TextComponent.Builder builder = Component.text()
                .append(Component.text("Info for ", NamedTextColor.AQUA))
                .append(Component.text(world.getKey().getKey(), NamedTextColor.AQUA))
                .append(Component.text(":", NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text(" Version: ", NamedTextColor.AQUA))
                .append(Component.text(version, NamedTextColor.AQUA))
                .append(Component.text(" (", NamedTextColor.AQUA))
                .append(Component.text(dataVersion, NamedTextColor.AQUA))
                .append(Component.text(")", NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text(" Compression: ", NamedTextColor.AQUA))
                .append(Component.text(getConfig().compression().name(), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text(" Source: ", NamedTextColor.AQUA))
                .append(Component.text(getSource() == null ? "None" : getSource().getClass().getSimpleName(), NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text(" Generator: STREAMING", NamedTextColor.AQUA))
                .appendNewline()
                .append(Component.text(" Spawn: ", NamedTextColor.AQUA))
                .append(Component.text(getConfig().spawnString(), NamedTextColor.AQUA));

        if (offset != null) {
            builder.appendNewline();
            builder.append(Component.text(" Schematic center: ", NamedTextColor.AQUA));
            builder.append(Component.text(offset.x + ", " + offset.y + ", " + offset.z, NamedTextColor.AQUA));
        }

        return ((AbstractBuilder<TextComponent>)builder).build();
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    public byte[] getUserData() {
        return userData;
    }

    public void setUserData(byte[] userData) {
        this.userData = userData;
    }

    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }



}
