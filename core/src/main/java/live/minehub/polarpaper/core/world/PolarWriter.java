package live.minehub.polarpaper.core.world;

import dev.hallock.zstd.Zstd;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import live.minehub.polarpaper.core.util.PaletteUtil;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

public class PolarWriter {

    private PolarWriter() {
    }

    public static void write(@NotNull PolarSource source, @NotNull PolarWorld world) {
        write(source, world, PolarDataConverter.DEFAULT, PolarConstants.DEFAULT_COMPRESSION, PolarConstants.DEFAULT_COMPRESSION_LEVEL);
    }

    public static void write(@NotNull PolarSource source, @NotNull PolarWorld world, @NotNull PolarDataConverter dataConverter, PolarWorld.CompressionType compression, int compressionLevel) {
        Zstd zstd = Zstd.zstd();
        long contentSize = 0;
        try (Arena arena = Arena.ofConfined();
             var writer = new MemorySegmentWriter(256)) {
            writer.writeByte(world.minSection());
            writer.writeByte(world.maxSection());
            writer.writeByteArray(world.userData());

            List<PolarChunk> nonEmptyChunks = world.nonEmptyChunks();
            writer.writeVarInt(nonEmptyChunks.size());
            for (PolarChunk chunk : nonEmptyChunks) {
                writeChunk(writer, chunk, world.maxSection() - world.minSection() + 1);
            }

            MemorySegment writtenSegment = writer.getWrittenSegment();

            MemorySegment dst = null;

            switch (compression) {
                case ZSTD -> {
                    contentSize = writtenSegment.byteSize();
                    long smallestSize = zstd.compressBound(writtenSegment.byteSize());
                    dst = arena.allocate(smallestSize);
                    long compressedSize = zstd.compress(dst, smallestSize, writtenSegment, writtenSegment.byteSize(), compressionLevel);
                    dst = dst.asSlice(0, compressedSize);
                }
                case NONE -> {
                    dst = writtenSegment;
                    contentSize = writtenSegment.byteSize();
                }
            }

            // write header:
            // magic (int)
            // version (short)
            // dataversion (varint)
            // compression (byte)
            // content length (varint)
            // content (byte[])
            long headerSize = ValueLayout.JAVA_INT.byteSize() +
                    ValueLayout.JAVA_SHORT.byteSize() +
                    5 /* (varint max size) */ +
                    ValueLayout.JAVA_BYTE.byteSize() +
                    5 /* (varint max size) */;

            try (var finalWriter = new MemorySegmentWriter(headerSize + dst.byteSize())) {
                finalWriter.writeInt(PolarConstants.MAGIC_NUMBER);
                finalWriter.writeShort(PolarConstants.LATEST_VERSION);
                finalWriter.writeVarInt(dataConverter.dataVersion());
                finalWriter.writeByte((byte) compression.ordinal());
                finalWriter.writeVarInt((int) contentSize);
                finalWriter.writeSegment(dst);

                source.save(finalWriter.getWrittenSegment());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private static void writeChunk(@NotNull MemorySegmentWriter writer, @NotNull PolarChunk chunk, int sectionCount) {
        writer.writeVarInt(chunk.x());
        writer.writeVarInt(chunk.z());

        assert sectionCount == chunk.sections().length : "section count and chunk section length mismatch";

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

    public static void writeBlockEntity(@NotNull MemorySegmentWriter writer, @NotNull PolarChunk.BlockEntity blockEntity) {
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

    private static void writeSection(@NotNull MemorySegmentWriter writer, @NotNull PolarSection section) {
        boolean empty = section.isEmpty();
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