package live.minehub.polarpaper.core.world;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import dev.hallock.zstd.Zstd;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.util.CoordConversion;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import live.minehub.polarpaper.core.util.PaletteUtil;
import live.minehub.polarpaper.core.util.TaskFutures;
import net.minecraft.nbt.NbtIo;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PolarWriter {

    private static final int CONVERSIONS_IN_FLIGHT = 64;

    private PolarWriter() {
    }

    public static void write(PolarSource source, PolarWorld world, Config config) {
        write(source, world, PolarDataConverter.DEFAULT, config.compression(), config.compressionLevel());
    }

    public static void write(PolarSource source, PolarWorld world) {
        write(source, world, PolarDataConverter.DEFAULT, PolarConstants.DEFAULT_COMPRESSION, PolarConstants.DEFAULT_COMPRESSION_LEVEL);
    }

    public static void write(PolarSource source, PolarWorld world, PolarDataConverter dataConverter,
                             PolarWorld.CompressionType compression, int compressionLevel) {
        write(source, world.minSection(), world.maxSection(), world.userData(), world.chunks(),
                dataConverter, compression, compressionLevel);
    }

    /**
     * Converts and writes a world one chunk at a time with the default config
     */
    public static CompletableFuture<Void> convertAndWrite(PolarSource source, World world, PolarWorldAccess worldAccess) {
        return convertAndWrite(source, world, worldAccess, Config.Builder.defaults().build(), BlockSelector.ALL, List.of(), false, new byte[0]);
    }

    /**
     * Converts and writes a world one chunk at a time
     */
    public static CompletableFuture<Void> convertAndWrite(PolarSource source, World world, PolarWorldAccess worldAccess, Config config) {
        return convertAndWrite(source, world, worldAccess, config, BlockSelector.ALL, List.of(), false, new byte[0]);
    }

    /**
     * Converts and writes a world one chunk at a time
     * @param loadChunks Whether to allow loading chunks, otherwise unloaded chunks will be ignored
     */
    public static CompletableFuture<Void> convertAndWrite(PolarSource source, World world,
                                                          PolarWorldAccess worldAccess, Config config, BlockSelector selector,
                                                          Collection<PolarChunk> chunks, boolean loadChunks, byte[] userData) {
        boolean saveLight = config.saveLight();
        PolarWorld.CompressionType compression = config.compression();
        int compressionLevel = config.compressionLevel();

        return CompletableFuture.runAsync(() -> {
            Set<Vector2i> positions = new HashSet<>();
            for (PolarChunk chunk : chunks) {
                if (selector.testChunk(chunk.x(), chunk.z())) positions.add(new Vector2i(chunk.x(), chunk.z()));
            }
            selector.forEachChunk(pos -> positions.add(new Vector2i(pos)));

            ChunkHolderManager manager = ((CraftWorld) world).getHandle().moonrise$getChunkTaskScheduler().chunkHolderManager;
            for (NewChunkHolder holder : manager.getChunkHolders()) {
                if (selector.testChunk(holder.chunkX, holder.chunkZ)) positions.add(new Vector2i(holder.chunkX, holder.chunkZ));
            }

            Iterable<@Nullable PolarChunk> chunkIterator = () -> new Iterator<>() {
                private final Iterator<Vector2i> iterator = positions.iterator();
                private final ArrayDeque<CompletableFuture<@Nullable PolarChunk>> inFlight = new ArrayDeque<>();

                private void fill() {
                    while (inFlight.size() < CONVERSIONS_IN_FLIGHT && iterator.hasNext()) {
                        Vector2i pos = iterator.next();
                        inFlight.add(PolarChunk.convert(world, pos.x, pos.y, worldAccess, selector, saveLight, loadChunks));
                    }
                }

                @Override
                public boolean hasNext() {
                    fill();
                    return !inFlight.isEmpty();
                }

                @Override
                public @Nullable PolarChunk next() {
                    fill();
                    CompletableFuture<@Nullable PolarChunk> next = inFlight.poll();
                    if (next == null) throw new NoSuchElementException();
                    return next.join();
                }
            };

            write(source, (byte) CoordConversion.sectionIndex(world.getMinHeight()),
                    (byte) CoordConversion.sectionIndex(world.getMaxHeight() - 1), userData, chunkIterator,
                    PolarDataConverter.DEFAULT, compression, compressionLevel);
        }, TaskFutures.VIRTUAL_THREAD_EXECUTOR);
    }

    private static void write(PolarSource source, byte minSection, byte maxSection, byte[] userData,
            Iterable<@Nullable PolarChunk> chunks, PolarDataConverter dataConverter,
            PolarWorld.CompressionType compression, int compressionLevel) {
        try {
            var body = Files.createTempFile("polar-chunks-", ".tmp");
            try {
                long chunkBytes = 0;
                int chunkCount = 0;
                try (var spool = new BufferedOutputStream(Files.newOutputStream(body))) {
                    for (PolarChunk chunk : chunks) {
                        if (chunk == null || chunk.isEmpty()) continue;
                        try (var writer = new MemorySegmentWriter(256)) {
                            int sectionCount = maxSection - minSection + 1;
                            writeChunk(writer, chunk, sectionCount);
                            chunkBytes += writer.getWriteIndex();
                            if (chunkBytes > Integer.MAX_VALUE) {
                                throw new IllegalArgumentException("Polar body exceeds the format's maximum supported size");
                            }
                            spool.write(writer.getWrittenBytes());
                            chunkCount++;
                        }
                    }
                }
                try (var prefix = new MemorySegmentWriter(256); var header = new MemorySegmentWriter(17)) {
                    prefix.writeByte(minSection);
                    prefix.writeByte(maxSection);
                    prefix.writeByteArray(userData);
                    prefix.writeVarInt(chunkCount);
                    long contentSize = chunkBytes + prefix.getWriteIndex();
                    if (contentSize > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("Polar body exceeds the format's maximum supported size");
                    }
                    header.writeInt(PolarConstants.POLAR_MAGIC);
                    header.writeShort(PolarConstants.LATEST_VERSION);
                    header.writeVarInt(dataConverter.dataVersion());
                    header.writeByte((byte) compression.ordinal());
                    header.writeVarInt((int) contentSize);
                    byte[] headerBytes = header.getWrittenBytes();
                    byte[] prefixBytes = prefix.getWrittenBytes();
                    source.save(output -> {
                        output.write(headerBytes);
                        switch (compression) {
                            case NONE -> copyBody(output, prefixBytes, body);
                            case ZSTD -> {
                                try (var compressed = Zstd.zstd().createCompressorOutputStream(new OutputStream() {
                                    @Override
                                    public void write(int value) throws IOException {
                                        output.write(value);
                                    }

                                    @Override
                                    public void write(byte[] bytes, int offset, int length) throws IOException {
                                        output.write(bytes, offset, length);
                                    }

                                    @Override
                                    public void flush() throws IOException {
                                        output.flush();
                                    }
                                }, compressionLevel)) {
                                    copyBody(compressed, prefixBytes, body);
                                }
                            }
                        }
                    });
                }
            } finally {
                Files.deleteIfExists(body);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyBody(OutputStream output, byte[] prefix, Path payload) throws IOException {
        output.write(prefix);
        try (var input = Files.newInputStream(payload)) {
            input.transferTo(output);
        }
    }

    private static void writeChunk(MemorySegmentWriter writer, PolarChunk chunk, int sectionCount) {
        writer.writeVarInt(chunk.x());
        writer.writeVarInt(chunk.z());

        if (sectionCount != chunk.sections().length) {
            throw new IllegalArgumentException("Section count and chunk section length mismatch");
        }

        for (var section : chunk.sections()) {
            writeSection(writer, section);
        }

        writer.writeVarInt(chunk.blockEntities().length);
        for (var blockEntity : chunk.blockEntities()) {
            writeBlockEntity(writer, blockEntity);
        }

        {
            int heightmapBits = 0;
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                if (chunk.heightmap(i) != null)
                    heightmapBits |= 1 << i;
            }
            writer.writeInt(heightmapBits);

            int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * PolarConstants.CHUNK_SECTION_SIZE);
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                var heightmap = chunk.heightmap(i);
                if (heightmap == null) continue;
                if (heightmap.length == 0) writer.writeLongArray(new long[0]);
                else writer.writeLongArray(PaletteUtil.pack(heightmap, bitsPerEntry));
            }
        }

        writer.writeByteArray(chunk.userData());
    }

    public static void writeBlockEntity(MemorySegmentWriter writer, PolarChunk.BlockEntity blockEntity) {
        writer.writeInt(blockEntity.index());
        writer.writeByte((byte) (blockEntity.id() == null ? 0 : 1));
        if (blockEntity.id() != null) {
            writer.writeString(blockEntity.id());
        }

        writer.writeByte((byte) (blockEntity.data() == null ? 0 : 1));
        if (blockEntity.data() != null) {
            try {
                NbtIo.writeAnyTag(blockEntity.data(), writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void writeSection(MemorySegmentWriter writer, PolarSection section) {
        boolean empty = section.empty();
        writer.writeByte((byte) (empty ? 1 : 0));
        if (empty) return;

        // Blocks
        var blockPalette = section.blockPalette();
        writer.writeStringArray(blockPalette);
        if (blockPalette.length > 1) {
            var blockData = section.blockData();
            writer.writeLongArray(blockData);
        }

        // Biomes
        var biomePalette = section.biomePalette();
        writer.writeStringArray(biomePalette);
        if (biomePalette.length > 1) {
            var biomeData = section.biomeData();
            writer.writeLongArray(biomeData);
        }

        // Light
        writer.writeByte((byte) section.blockLightContent().ordinal());
        if (section.blockLightContent() == PolarSection.LightContent.PRESENT) writer.write(section.blockLight().toVanillaNibble().getData());
        writer.writeByte((byte) section.skyLightContent().ordinal());
        if (section.skyLightContent() == PolarSection.LightContent.PRESENT) writer.write(section.skyLight().toVanillaNibble().getData());
    }

}
