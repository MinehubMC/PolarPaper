package live.minehub.polarpaper;

import com.github.luben.zstd.Zstd;
import live.minehub.polarpaper.PolarSection.LightContent;
import live.minehub.polarpaper.nbt.BinaryTagReader;
import live.minehub.polarpaper.util.PaletteUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static live.minehub.polarpaper.util.ByteArrayUtil.*;

public class PolarReader {

    private static final Logger LOGGER = Logger.getLogger(PolarReader.class.getName());

    private static final boolean FORCE_LEGACY_NBT = Boolean.getBoolean("polar.debug.force-legacy-nbt");
    private static final int MAX_BLOCK_PALETTE_SIZE = 16 * 16 * 16;
    private static final int MAX_BIOME_PALETTE_SIZE = 8 * 8 * 8;

    public static @NotNull PolarWorld read(byte @NotNull [] data) {
        return read(data, PolarDataConverter.NOOP);
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data, @NotNull PolarDataConverter dataConverter) {
        ByteBuffer bb = ByteBuffer.wrap(data);

        int magic = bb.getInt();
        assertThat(magic == PolarWorld.MAGIC_NUMBER, "Invalid magic number");

        short version = bb.getShort();
        validateVersion(version);

        int dataVersion = version >= PolarWorld.VERSION_DATA_CONVERTER
                ? getVarInt(bb)
                : dataConverter.defaultDataVersion();

        LOGGER.info("Polar version: " + version + " (" + dataVersion + ")");


        byte compressionByte = bb.get();
        PolarWorld.CompressionType compression = PolarWorld.CompressionType.fromId(compressionByte);
        assertThat(compression != null, "Invalid compression type");

        LOGGER.info("Polar compression: " + compression.name());

        int compressedDataLength = getVarInt(bb);

        // Replace the buffer with a "decompressed" version. This is a no-op if compression is NONE.
        bb = decompressBuffer(bb, compression, compressedDataLength);

        byte minSection = bb.get();
        byte maxSection = bb.get();
        assertThat(minSection < maxSection, "Invalid section range");

        // User (world) data
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_WORLD_USERDATA) {
            int userDataLength = getVarInt(bb);
            byte[] bytes = new byte[userDataLength];
            bb.get(bytes);
            userData = bytes;
        }

        ByteBuffer finalBb = bb;
        BinaryTagReader nbtReader = new BinaryTagReader(new DataInputStream(new InputStream() {
            @Override
            public int read() {
                return finalBb.get() & 0xFF;
            }

            @Override
            public int available() {
                return finalBb.remaining();
            }
        }));

        int chunkCount = getVarInt(bb);
        List<PolarChunk> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(readChunk(dataConverter, version, dataVersion, bb, maxSection - minSection + 1, nbtReader));
        }

        return new PolarWorld(version, dataVersion, compression, minSection, maxSection, userData, chunks);
    }

    private static @NotNull PolarChunk readChunk(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull ByteBuffer bb, int sectionCount, BinaryTagReader nbtReader) {
        var chunkX = getVarInt(bb);
        var chunkZ = getVarInt(bb);

        var sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(dataConverter, version, dataVersion, bb);
        }

        int blockEntityCount = getVarInt(bb);
        List<PolarChunk.BlockEntity> blockEntities = new ArrayList<>(blockEntityCount);
        for (int i = 0; i < blockEntityCount; i++) {
            blockEntities.add(readBlockEntity(dataConverter, version, dataVersion, bb, nbtReader));
        }

        var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        int heightmapMask = bb.getInt();
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
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT) {
            int userDataLength = getVarInt(bb);
            byte[] bytes = new byte[userDataLength];
            bb.get(bytes);
            userData = bytes;
        }

        return new PolarChunk(
                chunkX, chunkZ,
                sections,
                blockEntities,
                heightmaps,
                userData
        );
    }

    private static @NotNull PolarSection readSection(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull ByteBuffer bb) {
        // If section is empty exit immediately
        if (bb.get() == 1) return new PolarSection();

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

        LightContent blockLightContent = LightContent.MISSING, skyLightContent = LightContent.MISSING;
        byte[] blockLight = null, skyLight = null;
        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            blockLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[bb.get()]
                    : ((bb.get() == 1) ? LightContent.PRESENT : LightContent.MISSING);
            if (blockLightContent == LightContent.PRESENT)
                blockLight = getLightData(bb);
            skyLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[bb.get()]
                    : (bb.get() == 1 ? LightContent.PRESENT : LightContent.MISSING);
            if (skyLightContent == LightContent.PRESENT)
                skyLight = getLightData(bb);
        } else if (bb.get() == 1) {
            blockLightContent = LightContent.PRESENT;
            blockLight = getLightData(bb);
            skyLightContent = LightContent.PRESENT;
            skyLight = getLightData(bb);
        }

        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

    private static @NotNull PolarChunk.BlockEntity readBlockEntity(@NotNull PolarDataConverter dataConverter, int version, int dataVersion, @NotNull ByteBuffer bb, BinaryTagReader nbtReader) {
        int posIndex = bb.getInt();
        String id = getStringOptional(bb);

        CompoundBinaryTag nbt = CompoundBinaryTag.empty();
        if (version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || bb.get() == 1) {
            if (version <= PolarWorld.VERSION_MINESTOM_NBT_READ_BREAK || FORCE_LEGACY_NBT) {
                // TODO: do
//                nbt = (CompoundBinaryTag) legacyReadNBT(buffer);
            } else {
                try {
                    nbt = (CompoundBinaryTag) nbtReader.readNameless();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (dataVersion < dataConverter.dataVersion()) {
            var converted = dataConverter.convertBlockEntityData(id == null ? "" : id, nbt, dataVersion, dataConverter.dataVersion());
            id = converted.getKey();
            if (id.isEmpty()) id = null;
            nbt = converted.getValue();
            if (nbt.size() == 0) nbt = null;
        }

        return new PolarChunk.BlockEntity(
                posIndex,
                id, nbt
        );
    }

    private static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Polar version. Up to %d is supported, found %d.",
                PolarWorld.LATEST_VERSION, version);
        assertThat(version <= PolarWorld.LATEST_VERSION, invalidVersionError);
    }

    private static @NotNull ByteBuffer decompressBuffer(@NotNull ByteBuffer buffer, @NotNull PolarWorld.CompressionType compression, int compressedLength) {
        return switch (compression) {
            case NONE -> buffer;
            case ZSTD -> {
                int limit = buffer.limit();
                int length = limit - buffer.position();
                assertThat(length >= 0, "Invalid remaining: " + length);

                byte[] bytes = new byte[length];
                buffer.get(bytes);

                var decompressed = Zstd.decompress(bytes, compressedLength);
                yield ByteBuffer.wrap(decompressed);
            }
        };
    }


    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }



}
