package live.minehub.polarpaper.core.world;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.core.util.ByteArrayUtil;
import live.minehub.polarpaper.core.util.PaletteUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static live.minehub.polarpaper.core.util.ByteArrayUtil.*;

public class PolarWriter {

    private PolarWriter() {
    }

    private static final int CHUNK_SECTION_SIZE = 16;

    public static byte[] write(@NotNull PolarWorld world) {
        return write(world, live.minehub.polarpaper.core.world.PolarDataConverter.DEFAULT);
    }

    public static byte[] write(@NotNull PolarWorld world, @NotNull live.minehub.polarpaper.core.world.PolarDataConverter dataConverter) {
        ByteBuf bb = Unpooled.directBuffer();

        bb.writeByte(world.minSection());
        bb.writeByte(world.maxSection());
        writeVarInt(world.userData().length, bb);
        bb.writeBytes(world.userData());

        List<live.minehub.polarpaper.core.world.PolarChunk> nonEmptyChunks = world.nonEmptyChunks();
        writeVarInt(nonEmptyChunks.size(), bb);
        for (live.minehub.polarpaper.core.world.PolarChunk chunk : nonEmptyChunks) {
            writeChunk(bb, chunk, world.maxSection() - world.minSection() + 1);
        }

        byte[] contentBytes = ByteArrayUtil.outputArray(bb);


        // Create final buffer
        ByteBuf finalBB = Unpooled.directBuffer();
        finalBB.writeInt(PolarWorld.MAGIC_NUMBER);
        finalBB.writeShort(PolarWorld.LATEST_VERSION);
        writeVarInt(dataConverter.dataVersion(), finalBB);
        finalBB.writeByte(world.compression().ordinal());
        switch (world.compression()) {
            case NONE -> {
                writeVarInt(contentBytes.length, finalBB);
                finalBB.writeBytes(contentBytes);
            }
            case ZSTD -> {
                writeVarInt(contentBytes.length, finalBB);
                finalBB.writeBytes(Zstd.compress(contentBytes, world.compressionLevel()));
            }
        }

        return ByteArrayUtil.outputArray(finalBB);
    }

    private static void writeChunk(@NotNull ByteBuf bb, @NotNull live.minehub.polarpaper.core.world.PolarChunk chunk, int sectionCount) {
        writeVarInt(chunk.x(), bb);
        writeVarInt(chunk.z(), bb);

        assert sectionCount == chunk.sections().length : "section count and chunk section length mismatch";

        for (var section : chunk.sections()) {
            writeSection(bb, section);
        }

        writeVarInt(chunk.blockEntities().length, bb);
        for (var blockEntity : chunk.blockEntities()) {
            writeBlockEntity(bb, blockEntity);
        }

        {
            int heightmapBits = 0;
            for (int i = 0; i < live.minehub.polarpaper.core.world.PolarChunk.MAX_HEIGHTMAPS; i++) {
                if (chunk.heightmap(i) != null)
                    heightmapBits |= 1 << i;
            }
            bb.writeInt(heightmapBits);

            int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * CHUNK_SECTION_SIZE);
            for (int i = 0; i < live.minehub.polarpaper.core.world.PolarChunk.MAX_HEIGHTMAPS; i++) {
                var heightmap = chunk.heightmap(i);
                if (heightmap == null) continue;
                if (heightmap.length == 0) writeLongArray(new long[0], bb);
                else writeLongArray(PaletteUtil.pack(heightmap, bitsPerEntry), bb);
            }
        }

        writeByteArray(chunk.userData(), bb);
    }

    private static void writeSection(@NotNull ByteBuf bb, @NotNull live.minehub.polarpaper.core.world.PolarSection section) {
        boolean empty = section.isEmpty();
        bb.writeByte(empty ? 1 : 0);
        if (empty) return;

        // Blocks
        var blockPalette = section.blockPalette();
        writeStringArray(blockPalette, bb);
        if (blockPalette.length > 1) {
            var blockData = section.blockData();
            writeLongArray(blockData, bb);
        }

        // Biomes
        var biomePalette = section.biomePalette();
        writeStringArray(biomePalette, bb);
        if (biomePalette.length > 1) {
            var biomeData = section.biomeData();
            writeLongArray(biomeData, bb);
        }

        // Light
        bb.writeByte((byte) section.blockLightContent().ordinal());
        if (section.blockLightContent() == live.minehub.polarpaper.core.world.PolarSection.LightContent.PRESENT)
            bb.writeBytes(section.blockLight());
        bb.writeByte((byte) section.skyLightContent().ordinal());
        if (section.skyLightContent() == live.minehub.polarpaper.core.world.PolarSection.LightContent.PRESENT)
            bb.writeBytes(section.skyLight());
    }

}