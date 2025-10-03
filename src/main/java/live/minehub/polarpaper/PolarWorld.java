package live.minehub.polarpaper;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import live.minehub.polarpaper.util.CoordConversion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PolarWorld {

    public static final int MAGIC_NUMBER = 0x506F6C72; // `Polr`
    public static final short LATEST_VERSION = 7;

    static final short VERSION_UNIFIED_LIGHT = 1;
    static final short VERSION_USERDATA_OPT_BLOCK_ENT_NBT = 2;
    static final short VERSION_MINESTOM_NBT_READ_BREAK = 3;
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
    private byte minSection;
    private byte maxSection;
    private byte @NotNull [] userData;

    // Chunk data
    private final Long2ObjectMap<PolarChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final LongArraySet expandChunks = new LongArraySet();
    private final ReentrantReadWriteLock chunksLock = new ReentrantReadWriteLock();

    public PolarWorld() {
        this(LATEST_VERSION, Bukkit.getUnsafe().getDataVersion(), DEFAULT_COMPRESSION, (byte) -4, (byte) 19, new byte[0], List.of());
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

    public void setCompression(@NotNull CompressionType compression) {
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

    public void addExpandChunk(int x, int z) {
        chunksLock.writeLock().lock();
        expandChunks.add(CoordConversion.chunkIndex(x, z));
        chunksLock.writeLock().unlock();
    }

    public @NotNull Collection<PolarChunk> chunks() {
        return chunks.values();
    }

    public LongArraySet expandChunks() {
        return expandChunks;
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

}
