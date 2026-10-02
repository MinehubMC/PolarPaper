package live.minehub.polarpaper.core.generator;

import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.FilePolarSource;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.source.SlimeFilePolarSource;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.util.LightUtil;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.TaskFutures;
import live.minehub.polarpaper.core.world.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class SlimeStreamLoader extends PolarGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlimeStreamLoader.class);

    private final @NotNull PolarDataConverter dataConverter;
    private final @NotNull EntitySerializer entitySerializer;
    private int version;
    private int dataVersion;
    private boolean poiChunks = false;
    private boolean blockTicks = false;
    private boolean fluidTicks = false;
    private byte[] userData = new byte[0];

    /**
     * Automatically converts Slime worlds to Polar worlds (reads slime, only saves polar).
     * If using a FilePolarSource, it will change the file extension to .slime.bak,
     * otherwise it will overwrite the previous file
     */
    public SlimeStreamLoader(@NotNull Config config, @Nullable PolarSource polarSource, @NotNull PolarWorldAccess worldAccess, @NotNull PolarDataConverter dataConverter, @NotNull EntitySerializer entitySerializer) {
        // Fix the source
        PolarSource realSource = polarSource;
        if (polarSource instanceof FilePolarSource fileSource) realSource = new SlimeFilePolarSource(fileSource);
        super(config, realSource, worldAccess);
        this.dataConverter = dataConverter;

        this.dataVersion = dataConverter.defaultDataVersion();
        this.entitySerializer = entitySerializer;
    }

    //https://github.com/hollow-cube/polar/blob/main/src/main/java/net/hollowcube/polar/StreamingPolarLoader.java#L64
    public CompletableFuture<Void> load(@NotNull World world) {
        if (getSource() == null) return CompletableFuture.completedFuture(null);

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

                // https://github.com/InfernalSuite/AdvancedSlimePaper/blob/main/SLIME_FORMAT
                var magic = reader.readUnsignedShort();
                assertThat(magic == PolarConstants.SLIME_MAGIC, "Invalid magic number");

                this.version = reader.readUnsignedByte();
                validateVersion(version);

                this.dataVersion = reader.readInt();

                var worldFlags = reader.readUnsignedByte();
                this.poiChunks = (worldFlags & 1) == 1;
                this.blockTicks = (((worldFlags) >> 1) & 1) == 1;
                this.fluidTicks = (((worldFlags) >> 2) & 1) == 1;

                int compressedChunkSize = reader.readInt();
                int uncompressedChunkSize = reader.readInt();

                var decompression = dstArena.allocate(uncompressedChunkSize);
                zstd.decompress(decompression, uncompressedChunkSize, src.asSlice(reader.getOffset()), compressedChunkSize);
                dst = decompression.asReadOnly();
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
            // Now we can just read the dst buffer without having to worry about the extra footprint of src
            return readData(dst, world);
        }
    }

    private CompletableFuture<Void> readData(MemorySegment segment, World world) {
        MemorySegmentReader reader = new MemorySegmentReader(segment);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int chunkCount = reader.readInt();
        for (int i = 0; i < chunkCount; i++) {
            futures.add(readChunk(reader, world));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Void> readChunk(MemorySegmentReader reader, World world) {
        var chunkX = reader.readInt();
        var chunkZ = reader.readInt();
        var sectionCount = reader.readInt();

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();

        boolean[] emptinessMap = new boolean[sectionCount];
        SWMRNibbleArray[] blockNibbles = ChunkUtils.createNibbleArray(sectionCount + 2); // light includes extra top and bottom section
        SWMRNibbleArray[] skyNibbles = ChunkUtils.createNibbleArray(sectionCount + 2);
        boolean lightPresent = false;
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            try {
                PolarSection polarSection = readSection(dataConverter, dataVersion, reader);
                if (!lightPresent && (polarSection.skyLightContent() != PolarSection.LightContent.MISSING || polarSection.blockLightContent() != PolarSection.LightContent.MISSING)) lightPresent = true;
                LevelChunkSection section = polarSection.createLevelChunkSection(serverLevel.registryAccess());
                levelChunkSections[i] = section;
                emptinessMap[i] = section.hasOnlyAir();
                skyNibbles[i + 1] = polarSection.skyLight();
                blockNibbles[i + 1] = polarSection.blockLight();
            } catch (Exception e) {
                LOGGER.error("Failed to load chunk at {} {} (section {}/{}) in {}", chunkX, chunkZ, i, sectionCount, world.getKey());
                return CompletableFuture.failedFuture(e);
            }
        }

        // heightmaps
        try {
            int length = reader.readInt();
            NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        if (poiChunks) {
            try {
                int length = reader.readInt();
                NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        if (blockTicks) {
            try {
                int length = reader.readInt();
                NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        if (fluidTicks) {
            try {
                int length = reader.readInt();
                NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        // TODO: count of unsupported data
        // TODO: give block/fluid ticks to chunk
        NoUnloadLevelChunk newLevelChunk = new NoUnloadLevelChunk(serverLevel, new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, levelChunkSections, null, null);

        try {
            int length = reader.readInt();
            Tag tileEntities = NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            if (tileEntities instanceof CompoundTag tag) {
                for (Tag entities : tag.getListOrEmpty("tileEntities")) {
                    CompoundTag entityCompound = entities.asCompound().orElse(null);
                    if (entityCompound == null) continue;
                    ChunkUtils.addBlockEntity(entityCompound, newLevelChunk);
                }
            }
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        List<net.minecraft.world.entity.Entity> successEntities = new ArrayList<>();

        try {
            int length = reader.readInt();
            Tag entities = NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            if (entities instanceof CompoundTag tag) {
                for (Tag entitiesTag : tag.getListOrEmpty("entities")) {
                    CompoundTag entityCompound = entitiesTag.asCompound().orElse(null);
                    if (entityCompound == null) continue;
                    ListTag posCompound = entityCompound.getList("Pos").orElse(null);
                    if (posCompound == null) continue;
                    entityCompound.putInt("DataVersion", dataVersion);
                    Location spawnLocation = new Location(world, posCompound.getDoubleOr(0, 0.0), posCompound.getDoubleOr(1, 0.0), posCompound.getDoubleOr(2, 0.0));
                    net.minecraft.world.entity.Entity entity = PolarEntity.toNMSEntity(this.entitySerializer, world, spawnLocation, entityCompound);
                    if (entity == null) continue;
                    successEntities.add(entity);
                }
            }
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        // extra tag
        try {
            int length = reader.readInt();
            NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        boolean finalLightPresent = lightPresent;

        return TaskFutures.runRegion(getWorldAccess().getPlugin(), world, chunkX, chunkZ, () -> {
            if (finalLightPresent) {
                newLevelChunk.starlight$setBlockEmptinessMap(emptinessMap);
                newLevelChunk.starlight$setSkyEmptinessMap(emptinessMap);
                newLevelChunk.starlight$setSkyNibbles(skyNibbles);
                newLevelChunk.starlight$setBlockNibbles(blockNibbles);
            } else {
                ChunkUtils.lightChunk(serverLevel, newLevelChunk);
            }
            ChunkUtils.insertChunk(serverLevel, newLevelChunk);
            serverLevel.moonrise$getEntityLookup().addEntityChunkEntities(successEntities, newLevelChunk.getPos());
            // TODO: load userdata

            return null;
        });
    }

    public static @NotNull PolarSection readSection(@NotNull PolarDataConverter dataConverter, int dataVersion, @NotNull MemorySegmentReader reader) {
        var flags = reader.readByte();
        boolean hasBlockLight = (flags & 1) == 1;
        boolean hasSkyLight = ((flags >> 1) & 1) == 1;

        SWMRNibbleArray blockLight;
        SWMRNibbleArray skyLight;
        if (hasBlockLight) {
            blockLight = new SWMRNibbleArray(reader.readByteArray(LightUtil.LIGHT_LENGTH).clone());
        } else {
            blockLight = LightUtil.getLightNibble(PolarSection.LightContent.EMPTY);
        }

        if (hasSkyLight) {
            skyLight = new SWMRNibbleArray(reader.readByteArray(LightUtil.LIGHT_LENGTH).clone());
        } else {
            skyLight = LightUtil.getLightNibble(PolarSection.LightContent.EMPTY);
        }

        String[] blockPalette;
        long[] blockData = null;
        try {
            int length = reader.readInt();
            Tag blockStates = NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            if (blockStates instanceof CompoundTag tagg) {
                ListTag paletteList = tagg.getListOrEmpty("palette");
                blockPalette = new String[paletteList.size()];
                int i = 0;
                for (Tag tag : paletteList) {
                    if (!(tag instanceof CompoundTag paletteCompound)) continue;
                    blockPalette[i++] = compoundToPaletteEntry(paletteCompound);
                }

                if (dataVersion < dataConverter.dataVersion()) {
                    dataConverter.convertBlockPalette(blockPalette, dataVersion, dataConverter.dataVersion());
                }

                if (blockPalette.length > 1) {
                    blockData = tagg.getLongArray("data").orElse(null);
                }
            } else {
                blockPalette = new String[] { "minecraft:air" };
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String[] biomePalette = new String[] { "minecraft:plains" };
        long[] biomeData = null;
        try {
            int length = reader.readInt();
            Tag biomes = NbtIo.readUnnamedTag(reader, NbtAccounter.uncompressedQuota());
            if (biomes instanceof CompoundTag tagg) {
                ListTag paletteList = tagg.getListOrEmpty("palette");
                biomePalette = new String[paletteList.size()];
                int i = 0;
                for (Tag tag : paletteList) {
                    if (!(tag instanceof StringTag(String value))) continue;
                    biomePalette[i++] = value;
                }

                if (biomePalette.length > 1) {
                    biomeData = tagg.getLongArray("data").orElse(null);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                hasBlockLight ? PolarSection.LightContent.PRESENT : PolarSection.LightContent.EMPTY, blockLight,
                hasSkyLight ? PolarSection.LightContent.PRESENT : PolarSection.LightContent.EMPTY, skyLight
        );
    }

    /**
     * Converts a Compound (e.g. <code>{Name:"minecraft:birch_leaves",Properties:{distance:"7",persistent:"true",waterlogged:"false"}}</code>)
     * to a palette string (e.g. <code>minecraft:birch_leaves[distance=7,persistent=true,waterlogged=false]</code>)
     */
    private static @Nullable String compoundToPaletteEntry(CompoundTag compound) {
        String name = compound.getString("Name").orElse(null);
        if (name == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(name);

        CompoundTag properties = compound.getCompound("Properties").orElse(null);
        if (properties == null) return sb.toString();
        sb.append("[");
        properties.forEach(new BiConsumer<>() {
            boolean first = true;

            @Override
            public void accept(String key, Tag value) {
                if (!first) sb.append(",");
                first = false;

                sb.append(key);
                sb.append("=");
                if (value instanceof StringTag(String value1)) {
                    sb.append(value1);
                } else {
                    sb.append(value);
                }
            }
        });
        sb.append("]");
        return sb.toString();
    }

    @Override
    public @Nullable PolarWorld getPolarWorld() {
        return null;
    }

    @Override
    public void addInfoComponent(World world, TextComponent.Builder builder) {
        builder.append(Component.text(" Version: ", NamedTextColor.AQUA))
                .append(Component.text(version, NamedTextColor.AQUA))
                .append(Component.text(" (", NamedTextColor.AQUA))
                .append(Component.text(dataVersion, NamedTextColor.AQUA))
                .append(Component.text(")", NamedTextColor.AQUA));
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

    public static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Slime version. Versions %d - %d are supported, found %d.",
                PolarConstants.LATEST_SLIME_VERSION, PolarConstants.MIN_SLIME_VERSION, version);
        assertThat((version <= PolarConstants.LATEST_SLIME_VERSION && version >= PolarConstants.MIN_SLIME_VERSION),
                invalidVersionError);
    }



}
