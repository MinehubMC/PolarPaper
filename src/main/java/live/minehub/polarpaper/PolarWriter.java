package live.minehub.polarpaper;

import com.github.luben.zstd.Zstd;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import live.minehub.polarpaper.util.PaletteUtil;
import org.jetbrains.annotations.NotNull;

import static live.minehub.polarpaper.util.ByteArrayUtil.*;

public class PolarWriter {

    private PolarWriter() {
    }

    private static final int CHUNK_SECTION_SIZE = 16;

    public static byte[] write(@NotNull PolarWorld world) {
        return write(world, PolarDataConverter.NOOP);
    }

    public static byte[] write(@NotNull PolarWorld world, @NotNull PolarDataConverter dataConverter) {
        ByteArrayDataOutput bb = ByteStreams.newDataOutput();

        bb.write(world.minSection());
        bb.write(world.maxSection());
        writeVarInt(world.userData().length, bb);
        bb.write(world.userData());

        writeVarInt(world.nonEmptyChunks(), bb);
        for (PolarChunk chunk : world.chunks()) {
            if (chunk.isEmpty()) continue;
            writeChunk(bb, chunk, world.maxSection() - world.minSection() + 1);
        }

        byte[] contentBytes = bb.toByteArray();


        // Create final buffer
        ByteArrayDataOutput finalBB = ByteStreams.newDataOutput();
        finalBB.writeInt(PolarWorld.MAGIC_NUMBER);
        finalBB.writeShort(PolarWorld.LATEST_VERSION);
        writeVarInt(dataConverter.dataVersion(), finalBB);
        finalBB.write(world.compression().ordinal());
        switch (world.compression()) {
            case NONE -> {
                writeVarInt(contentBytes.length, finalBB);
                finalBB.write(contentBytes);
            }
            case ZSTD -> {
                writeVarInt(contentBytes.length, finalBB);
                finalBB.write(Zstd.compress(contentBytes));
            }
        }

        return finalBB.toByteArray();
    }

    private static void writeChunk(@NotNull ByteArrayDataOutput bb, @NotNull PolarChunk chunk, int sectionCount) {
        writeVarInt(chunk.x(), bb);
        writeVarInt(chunk.z(), bb);

        assert sectionCount == chunk.sections().length : "section count and chunk section length mismatch";

        for (var section : chunk.sections()) {
            writeSection(bb, section);
        }

        writeVarInt(chunk.blockEntities().size(), bb);
        for (var blockEntity : chunk.blockEntities()) {
            writeBlockEntity(bb, blockEntity);
        }

        {
            int heightmapBits = 0;
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                if (chunk.heightmap(i) != null)
                    heightmapBits |= 1 << i;
            }
            bb.writeInt(heightmapBits);

            int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * CHUNK_SECTION_SIZE);
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                var heightmap = chunk.heightmap(i);
                if (heightmap == null) continue;
                if (heightmap.length == 0) writeLongArray(new long[0], bb);
                else writeLongArray(PaletteUtil.pack(heightmap, bitsPerEntry), bb);
            }
        }

        writeByteArray(chunk.userData(), bb);
    }

    private static void writeSection(@NotNull ByteArrayDataOutput bb, @NotNull PolarSection section) {
        bb.write(section.isEmpty() ? 1 : 0);
        if (section.isEmpty()) return;

        // Blocks
        var blockPalette = section.blockPalette();
        writeStringArray(blockPalette, bb);
        if (blockPalette.length > 1) {
            var blockData = section.blockData();
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            writeLongArray(PaletteUtil.pack(blockData, bitsPerEntry), bb);
        }

        // Biomes
        var biomePalette = section.biomePalette();
        writeStringArray(biomePalette, bb);
        if (biomePalette.length > 1) {
            var biomeData = section.biomeData();
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            writeLongArray(PaletteUtil.pack(biomeData, bitsPerEntry), bb);
        }

        // Light
        bb.write((byte) section.blockLightContent().ordinal());
        if (section.blockLightContent() == PolarSection.LightContent.PRESENT)
            bb.write(section.blockLight());
        bb.write((byte) section.skyLightContent().ordinal());
        if (section.skyLightContent() == PolarSection.LightContent.PRESENT)
            bb.write(section.skyLight());
    }

}