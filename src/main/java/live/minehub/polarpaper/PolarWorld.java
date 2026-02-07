package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.util.CoordConversion;
import live.minehub.polarpaper.util.FoliaUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PolarWorld {

    public static final int MAGIC_NUMBER = 0x506F6C72; // `Polr`
    public static final short LATEST_VERSION = 7;
    public static final short MIN_VERSION = 4;

//    static final short VERSION_UNIFIED_LIGHT = 1;
//    static final short VERSION_USERDATA_OPT_BLOCK_ENT_NBT = 2;
//    static final short VERSION_MINESTOM_NBT_READ_BREAK = 3;
    static final short VERSION_WORLD_USERDATA = 4;
    static final short VERSION_SHORT_GRASS = 5; // >:(
    static final short VERSION_DATA_CONVERTER = 6;
    static final short VERSION_IMPROVED_LIGHT = 7;
    static final short VERSION_DEPRECATED_ENTITIES = 8;

    public static CompressionType DEFAULT_COMPRESSION = CompressionType.ZSTD;

    // Polar metadata
    private final short version;
    private final int dataVersion;
    private CompressionType compression;

    // World metadata
    private final byte minSection;
    private final byte maxSection;
    private byte @NotNull [] userData;

    // Chunk data
    private final Long2ObjectMap<PolarChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final ReentrantReadWriteLock chunksLock = new ReentrantReadWriteLock();

    public PolarWorld(byte minSection, byte maxSection) {
        this(LATEST_VERSION, Bukkit.getUnsafe().getDataVersion(), DEFAULT_COMPRESSION, minSection, maxSection, new byte[0], List.of());
    }

    public PolarWorld(
            short version,
            int dataVersion,
            @NotNull CompressionType compression,
            byte minSection, byte maxSection,
            byte @NotNull [] userData,
            @NotNull List<PolarChunk> chunks
    ) {
        this.version = version;
        this.dataVersion = dataVersion;
        this.compression = compression;

        this.minSection = minSection;
        this.maxSection = maxSection;
        this.userData = userData;

        for (var chunk : chunks) {
            var index = CoordConversion.chunkIndex(chunk.x(), chunk.z());
            this.chunks.put(index, chunk);
        }
    }

    public enum CompressionType {
        NONE,
        ZSTD;

        private static final CompressionType[] VALUES = values();

        public static @Nullable CompressionType fromId(int id) {
            if (id < 0 || id >= VALUES.length) return null;
            return VALUES[id];
        }
    }

    public short version() {
        return version;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public @NotNull CompressionType compression() {
        return compression;
    }

    public void compression(@NotNull CompressionType compression) {
        this.compression = compression;
    }

    public byte minSection() {
        return minSection;
    }

    public byte maxSection() {
        return maxSection;
    }

    public byte @NotNull [] userData() {
        return userData;
    }

    public void userData(byte @NotNull [] userData) {
        this.userData = userData;
    }

    public boolean hasChunkAt(int x, int z) {
        chunksLock.readLock().lock();
        boolean containsChunk = chunks.containsKey(CoordConversion.chunkIndex(x, z));
        chunksLock.readLock().unlock();
        return containsChunk;
    }

    public @Nullable PolarChunk chunkAt(int x, int z) {
        chunksLock.readLock().lock();
        PolarChunk chunk = chunks.getOrDefault(CoordConversion.chunkIndex(x, z), null);
        chunksLock.readLock().unlock();
        return chunk;
    }

    public void removeChunkAt(int x, int z) {
        chunksLock.writeLock().lock();
        chunks.remove(CoordConversion.chunkIndex(x, z));
        chunksLock.writeLock().unlock();
    }

    public void updateChunkAt(int x, int z, @NotNull PolarChunk chunk) {
        chunksLock.writeLock().lock();
        chunks.put(CoordConversion.chunkIndex(x, z), chunk);
        chunksLock.writeLock().unlock();
    }

    public @NotNull Collection<PolarChunk> chunks() {
        return chunks.values();
    }

    public int nonEmptyChunks() {
        int count = 0;
        for (PolarChunk chunk : chunks()) {
            if (chunk.isEmpty()) continue;
            count++;
        }
        return count;
    }

    /**
     * Get a polar world from a Bukkit world
     * @param world The bukkit world
     * @return The PolarWorld or null if the world is not from polar
     */
    public static @Nullable PolarWorld fromWorld(World world) {
        if (world == null) return null;
        ChunkGenerator generator = world.getGenerator();
        if (!(generator instanceof PolarGenerator polarGenerator)) return null;
        return polarGenerator.getPolarWorld();
    }

    /**
     * Updates the chunks in this PolarWorld
     *
     * @param world The bukkit world to retrieve the updated chunks from
     * @param skipUnsaved Skips already saved chunks to make updating faster (false for converting)
     * @see Polar#saveWorld(World, PolarSource)
     * @see BlockSelector#ALL
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     * @return runnable to remove chunks that weren't being held before updating. To be ran after using the updated chunks (e.g. saving to file)
     */
    public Runnable updateChunks(World world, boolean skipUnsaved) {
        return updateChunks(world, PolarWorldAccess.POLAR_PAPER_FEATURES, BlockSelector.ALL, skipUnsaved);
    }

    /**
     * Updates the chunks in this PolarWorld
     *
     * @param world The bukkit world to retrieve the updated chunks from
     * @param polarWorldAccess Describes how userdata should be handled (default PolarWorldAccess.POLAR_PAPER_FEATURES)
     * @param blockSelector Used to filter which blocks should be updated (essentially a crop)
     * @param skipUnsaved Skips already saved chunks to make updating faster (false for converting)
     * @see Polar#saveWorld(World, PolarSource)
     * @see BlockSelector#ALL
     * @see PolarWorldAccess#POLAR_PAPER_FEATURES
     * @return runnable to remove chunks that weren't being held before updating. To be ran after using the updated chunks (e.g. saving to file)
     */
    public Runnable updateChunks(World world, PolarWorldAccess polarWorldAccess, BlockSelector blockSelector, boolean skipUnsaved) {
        // TODO: consider offsets
        // TODO: chunk holders should probably be eventually released/removed (config option?)

        if (FoliaUtil.isFolia()) skipUnsaved = false;

        ChunkSystemServerLevel chunkSystemServerLevel = ((CraftWorld) world).getHandle();
        ChunkHolderManager chunkHolderManager = chunkSystemServerLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        for (PolarChunk chunk : new ArrayList<>(chunks())) {
            if (!blockSelector.testChunk(chunk.x(), chunk.z())) {
                removeChunkAt(chunk.x(), chunk.z());
            }
        }

        Set<ChunkPos> chunksToUnload = new HashSet<>();

        for (NewChunkHolder chunkHolder : chunkHolderManager.getChunkHolders()) {
            if (chunkHolder == null) continue;
            int chunkX = chunkHolder.chunkX;
            int chunkZ = chunkHolder.chunkZ;
            if (!blockSelector.testChunk(chunkX, chunkZ)) continue;
            ChunkAccess currentChunk = chunkHolder.getCurrentChunk();
            if (currentChunk == null) continue;

            ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();

            boolean onlyPlayers = true;
            if (entityChunk != null) {
                for (net.minecraft.world.entity.Entity nmsEntity : entityChunk.getAllEntities()) {
                    Entity entity = nmsEntity.getBukkitEntity();
                    if (entity.getType() != EntityType.PLAYER) {
                        onlyPlayers = false;
                        break;
                    }
                }
            }

            if (onlyPlayers) { // if contains no entities or the entities are all players (only difference is blocks)
                boolean allEmpty = true;
                for (LevelChunkSection section : currentChunk.getSections()) {
                    if (!section.hasOnlyAir()) {
                        allEmpty = false;
                        break;
                    }
                }

                if (allEmpty) {
                    // check if the chunk has generated the surface yet
                    // (otherwise we don't know if it's blank because its really blank, or because it hasn't generated yet)
                    if (currentChunk.getPersistedStatus().isOrBefore(ChunkStatus.SURFACE)) continue;
                    removeChunkAt(chunkX, chunkZ);
                    if (skipUnsaved) currentChunk.tryMarkSaved();
                    continue;
                }
            }

            // if was not held before saving - already generated, should be unloaded after using the chunks
            if (!hasChunkAt(chunkX, chunkZ)) {
                chunksToUnload.add(new ChunkPos(chunkX, chunkZ));
            }

            PolarChunk polarChunk = PolarChunk.convert(chunkHolder, polarWorldAccess, blockSelector);
            updateChunkAt(chunkX, chunkZ, polarChunk);
        }

        return () -> {
            for (ChunkPos chunkPos : chunksToUnload) {
                removeChunkAt(chunkPos.x, chunkPos.z);
            }
        };
    }

}
