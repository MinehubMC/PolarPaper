package live.minehub.polarpaper;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.PolarSection.LightContent;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.util.ByteArrayUtil;
import live.minehub.polarpaper.util.PaletteUtil;
import net.kyori.adventure.key.Key;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static live.minehub.polarpaper.util.ByteArrayUtil.*;

public class PolarReader {

    private static final int MAX_BLOCK_PALETTE_SIZE = 16 * 16 * 16;
    private static final int MAX_BIOME_PALETTE_SIZE = 8 * 8 * 8;

    public static @NotNull PolarWorld read(PolarSource source) {
        return read(source.readBytes());
    }

    public static @NotNull PolarWorld read(PolarSource source, @NotNull PolarDataConverter dataConverter) {
        return read(source.readBytes(), dataConverter);
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data) {
        return read(data, PolarDataConverter.DEFAULT);
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data, @NotNull PolarDataConverter dataConverter) {
        ByteBuf bb = Unpooled.wrappedBuffer(data);

        int magic = bb.readInt();
        assertThat(magic == PolarWorld.MAGIC_NUMBER, "Invalid magic number");

        short version = bb.readShort();
        validateVersion(version);

        int dataVersion = version >= PolarWorld.VERSION_DATA_CONVERTER
                ? getVarInt(bb)
                : dataConverter.defaultDataVersion();

//        PolarPaper.logger().info("Polar version: " + version + " (" + dataVersion + ")");


        byte compressionByte = bb.readByte();
        PolarWorld.CompressionType compression = PolarWorld.CompressionType.fromId(compressionByte);
        assertThat(compression != null, "Invalid compression type");

//        PolarPaper.logger().info("Polar compression: " + compression.name());

        int compressedDataLength = getVarInt(bb);

        // Replace the buffer with a "decompressed" version.
        ByteBuf uncompressed = decompressBuffer(bb, compression, compressedDataLength);

        byte minSection = uncompressed.readByte();
        byte maxSection = uncompressed.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        // User (world) data
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_WORLD_USERDATA) {
            int userDataLength = getVarInt(uncompressed);
            byte[] bytes = new byte[userDataLength];
            uncompressed.readBytes(bytes);
            userData = bytes;
        }

        int chunkCount = getVarInt(uncompressed);
        List<PolarChunk> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(readChunk(dataConverter, version, dataVersion, uncompressed, maxSection - minSection + 1));
        }

        return new PolarWorld(version, dataVersion, compression, minSection, maxSection, userData, chunks);
    }

    private static @NotNull PolarChunk readChunk(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull ByteBuf bb, int sectionCount) {
        var chunkX = getVarInt(bb);
        var chunkZ = getVarInt(bb);

        var sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(dataConverter, version, dataVersion, bb);
        }

        int blockEntityCount = getVarInt(bb);
        PolarChunk.BlockEntity[] blockEntities = new PolarChunk.BlockEntity[blockEntityCount];
        for (int i = 0; i < blockEntityCount; i++) {
            blockEntities[i] = readBlockEntity(dataConverter, version, dataVersion, bb);
        }

        // If the version is set to 8 copy the contents over to the beginning of userdata
        List<PolarEntity> entities = null;
        if (version == PolarWorld.VERSION_DEPRECATED_ENTITIES) {
            entities = new ArrayList<>();
            int entityCount = getVarInt(bb);
            for (int i = 0; i < entityCount; i++) {
                entities.add(new PolarEntity(
                        bb.readDouble(),
                        bb.readDouble(),
                        bb.readDouble(),
                        bb.readFloat(),
                        bb.readFloat(),
                        getByteArray(bb)
                ));
            }
        }

        var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        int heightmapMask = bb.readInt();
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if ((heightmapMask & (1 << i)) == 0)
                continue;

            long[] packed = getLongArray(bb);
            if (packed.length == 0) {
                heightmaps[i] = new int[0];
            } else {
                var bitsPerEntry = packed.length * 64 / PolarChunk.HEIGHTMAP_SIZE;
                heightmaps[i] = new int[PolarChunk.HEIGHTMAP_SIZE];
                PaletteUtil.unpack(heightmaps[i], packed, bitsPerEntry);
            }
        }

        // Objects
        int userDataLength = getVarInt(bb);
        byte[] userData = new byte[userDataLength];
        bb.readBytes(userData);

        if (entities != null) {
            ByteBuf newData = Unpooled.directBuffer();
            newData.writeByte((byte) 1);
            EntityUtil.writeEntities(entities, newData);

            userData = ByteArrayUtil.outputArray(newData);
        }

        return new PolarChunk(
                chunkX, chunkZ,
                sections,
                blockEntities,
                heightmaps,
                userData
        );
    }

    private static @NotNull PolarSection readSection(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull ByteBuf bb) {
        // If section is empty exit immediately
        if (bb.readByte() == 1) return new PolarSection();

        String[] blockPalette = getStringList(bb, MAX_BLOCK_PALETTE_SIZE);
        if (dataVersion < dataConverter.dataVersion()) {
            dataConverter.convertBlockPalette(blockPalette, dataVersion, dataConverter.dataVersion());
        }
        if (version <= PolarWorld.VERSION_SHORT_GRASS) {
            for (int i = 0; i < blockPalette.length; i++) {
                if (blockPalette[i].contains("grass")) {
                    String strippedID = blockPalette[i].split("\\[")[0];
                    int index = strippedID.indexOf(Key.DEFAULT_SEPARATOR);
                    if (strippedID.substring(index + 1).equals("grass")) {
                        blockPalette[i] = "short_grass";
                    }
                }
            }
        }
        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

            long[] rawBlockData = getLongArray(bb);
            int bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        String[] biomePalette = getStringList(bb, MAX_BIOME_PALETTE_SIZE);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

            long[] rawBiomeData = getLongArray(bb);
            int bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        byte[] blockLight = null;
        byte[] skyLight = null;
        LightContent blockLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                ? LightContent.VALUES[bb.readByte()]
                : ((bb.readByte() == 1) ? LightContent.PRESENT : LightContent.MISSING);
        if (blockLightContent == LightContent.PRESENT) blockLight = getLightData(bb);
        LightContent skyLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                ? LightContent.VALUES[bb.readByte()]
                : (bb.readByte() == 1 ? LightContent.PRESENT : LightContent.MISSING);
        if (skyLightContent == LightContent.PRESENT) skyLight = getLightData(bb);


        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

    private static @NotNull PolarChunk.BlockEntity readBlockEntity(@NotNull PolarDataConverter dataConverter, int version, int dataVersion, @NotNull ByteBuf bb) {
        int posIndex = bb.readInt();
        String id = getStringOptional(bb);

        ByteBufInputStream bbis = new ByteBufInputStream(bb);

        CompoundTag nbt = new CompoundTag();
        if (bb.readByte() == 1) {
            try {
                nbt = (CompoundTag) NbtIo.readAnyTag(bbis, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (dataVersion < dataConverter.dataVersion()) {
            var converted = dataConverter.convertBlockEntityData(id == null ? "" : id, nbt, dataVersion, dataConverter.dataVersion());
            id = converted.getKey();
            if (id.isEmpty()) id = null;
            nbt = converted.getValue();
            if (nbt.isEmpty()) nbt = null;
        }

        return new PolarChunk.BlockEntity(
                posIndex,
                id, nbt
        );
    }

    private static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Polar version. Versions %d - %d are supported, found %d.",
                PolarWorld.LATEST_VERSION, PolarWorld.MIN_VERSION, version);
        assertThat((version <= PolarWorld.LATEST_VERSION && version >= PolarWorld.MIN_VERSION) || version == PolarWorld.VERSION_DEPRECATED_ENTITIES,
                invalidVersionError);
    }

    private static @NotNull ByteBuf decompressBuffer(@NotNull ByteBuf buffer, @NotNull PolarWorld.CompressionType compression, int compressedLength) {
        return switch (compression) {
            case NONE -> Unpooled.wrappedBuffer(buffer);
            case ZSTD -> {
                int limit = buffer.capacity();
                int length = limit - buffer.readerIndex();
                assertThat(length >= 0, "Invalid remaining: " + length);

                byte[] bytes = new byte[length];
                buffer.readBytes(bytes);

                var decompressed = Zstd.decompress(bytes, compressedLength);
                yield Unpooled.wrappedBuffer(decompressed);
            }
        };
    }


    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }



}
