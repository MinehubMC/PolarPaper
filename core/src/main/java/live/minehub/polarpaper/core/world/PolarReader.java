package live.minehub.polarpaper.core.world;

import dev.hallock.zstd.Zstd;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.util.LightUtil;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.PaletteUtil;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;

public class PolarReader {

    private final @NotNull PolarDataConverter dataConverter;
    private short version;
    private int dataVersion;
    private byte[] userData = new byte[0];
    public PolarReader(@NotNull PolarDataConverter dataConverter) {
        this.dataConverter = dataConverter;
        this.dataVersion = dataConverter.defaultDataVersion();
    }

    public PolarReader() {
        this(PolarDataConverter.DEFAULT);
    }

    public PolarWorld read(PolarSource source) throws IOException {
        return read(source.read(), source.size());
    }

    //https://github.com/hollow-cube/polar/blob/main/src/main/java/net/hollowcube/polar/StreamingPolarLoader.java#L64
    public PolarWorld read(
            @NotNull ReadableByteChannel channel,
            long fileSize
    ) throws IOException {
        try (Arena dstArena = Arena.ofConfined()) {
            final MemorySegment dst;

            Zstd zstd = Zstd.zstd();
            try (Arena srcArena = Arena.ofConfined()) {
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
                        return readData(src.asSlice(reader.getOffset()));
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

            } // src is deallocated
            // Now we can just read the dst buffer without having to worry about the extra footprint of src
            return readData(dst);
        }
    }

    private PolarWorld readData(MemorySegment segment) {
        MemorySegmentReader reader = new MemorySegmentReader(segment);

        byte minSection = reader.readByte();
        byte maxSection = reader.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        this.userData = reader.readByteArray();

        int chunkCount = reader.readVarInt();
        List<PolarChunk> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            PolarChunk polarChunk = readChunk(reader, maxSection - minSection + 1);
            chunks.add(polarChunk);
        }

        return new PolarWorld(version, dataVersion, minSection, maxSection, userData, chunks);
    }

    private @NotNull PolarChunk readChunk(@NotNull MemorySegmentReader reader, int sectionCount) {
        var chunkX = reader.readVarInt();
        var chunkZ = reader.readVarInt();

        var sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(dataConverter, dataVersion, reader);
        }

        int blockEntityCount = reader.readVarInt();
        PolarChunk.BlockEntity[] blockEntities = new PolarChunk.BlockEntity[blockEntityCount];
        for (int i = 0; i < blockEntityCount; i++) {
            blockEntities[i] = readBlockEntity(dataConverter, dataVersion, reader);
        }

        var heightmaps = readHeightmaps(reader);

        byte[] userData = reader.readByteArray();

        return new PolarChunk(
                chunkX, chunkZ,
                sections,
                blockEntities,
                heightmaps,
                userData
        );
    }

    public static int @NotNull [][] readHeightmaps(@NotNull MemorySegmentReader reader) {
        int[][] heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        int heightmapMask = reader.readInt();
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if ((heightmapMask & (1 << i)) == 0)
                continue;

            long[] packed = reader.readLongArray();
            if (packed.length == 0) {
                heightmaps[i] = new int[0];
            } else {
                int bitsPerEntry = packed.length * 64 / PolarChunk.HEIGHTMAP_SIZE;
                heightmaps[i] = new int[PolarChunk.HEIGHTMAP_SIZE];
                PaletteUtil.unpack(heightmaps[i], packed, bitsPerEntry);
            }
        }
        return heightmaps;
    }

    public static @NotNull PolarSection readSection(@NotNull PolarDataConverter dataConverter, int dataVersion, @NotNull MemorySegmentReader reader) {
        // If section is empty exit immediately
        if (reader.readByte() == 1) return new PolarSection();

        String[] blockPalette = reader.readStringArray();
        if (dataVersion < dataConverter.dataVersion()) {
            dataConverter.convertBlockPalette(blockPalette, dataVersion, dataConverter.dataVersion());
        }
        long[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = reader.readLongArray();
        }

        String[] biomePalette = reader.readStringArray();
        long[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = reader.readLongArray();
        }

        byte[] blockLight;
        byte[] skyLight;
        PolarSection.LightContent blockLightContent = PolarSection.LightContent.VALUES[reader.readByte()];
        blockLight = LightUtil.getLightArray(blockLightContent, blockLightContent == PolarSection.LightContent.PRESENT ? reader.readByteArray(LightUtil.LIGHT_LENGTH) : null);
        PolarSection.LightContent skyLightContent = PolarSection.LightContent.VALUES[reader.readByte()];
        skyLight = LightUtil.getLightArray(skyLightContent, skyLightContent == PolarSection.LightContent.PRESENT ? reader.readByteArray(LightUtil.LIGHT_LENGTH) : null);


        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

    private static void fixSignNBT(CompoundTag nbt) {
        CompoundTag frontCompound = nbt.getCompound("front_text").orElse(null);
        CompoundTag backCompound = nbt.getCompound("back_text").orElse(null);
        if (frontCompound == null || backCompound == null) return;
        fixSignMessages(frontCompound.getListOrEmpty("messages"));
        fixSignMessages(backCompound.getListOrEmpty("messages"));
    }

    private static void fixSignMessages(ListTag messages) {
        for (Tag message : messages) {
            String string = message.asString().orElse(null);
            if (!"\"\"".equalsIgnoreCase(string)) return;
        }
        for (int i = 0; i < messages.size(); i++) {
            messages.set(i, StringTag.valueOf(""));
        }
    }

    public static @NotNull PolarChunk.BlockEntity readBlockEntity(@NotNull PolarDataConverter dataConverter, int dataVersion, @NotNull MemorySegmentReader reader) {
        int posIndex = reader.readInt();
        String id = reader.readOptionalString();

        CompoundTag nbt = new CompoundTag();
        if (reader.readByte() == 1) {
            try {
                nbt = (CompoundTag) NbtIo.readAnyTag(reader, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            fixSignNBT(nbt);
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

    public static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Polar version. Versions %d - %d are supported, found %d.",
                PolarConstants.LATEST_VERSION, PolarConstants.MIN_VERSION, version);
        assertThat((version <= PolarConstants.LATEST_VERSION && version >= PolarConstants.MIN_VERSION),
                invalidVersionError);
    }

    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }



}