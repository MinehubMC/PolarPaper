package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.github.luben.zstd.Zstd;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.util.*;
import net.kyori.adventure.key.Key;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static live.minehub.polarpaper.util.ByteArrayUtil.*;

public class PolarStreamLoader {

    private static final int MAX_BLOCK_PALETTE_SIZE = 16 * 16 * 16;
    private static final int MAX_BIOME_PALETTE_SIZE = 8 * 8 * 8;

    public static void read(PolarSource source, World world, @NotNull PolarWorldAccess worldAccess) {
        read(source.readBytes(), world, worldAccess);
    }

    public static void read(PolarSource source, World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess) {
        read(source.readBytes(), world, dataConverter, worldAccess);
    }

    public static void read(byte @NotNull [] data, World world, @NotNull PolarWorldAccess worldAccess) {
        read(data, world, PolarDataConverter.DEFAULT, worldAccess);
    }

    public static void read(byte @NotNull [] data, World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess) {
        ByteBuf bb = Unpooled.wrappedBuffer(data);

        int magic = bb.readInt();
        assertThat(magic == PolarConstants.MAGIC_NUMBER, "Invalid magic number");

        short version = bb.readShort();
        validateVersion(version);

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
        if (polarGenerator == null) return;
        polarGenerator.setVersion(version);

        int dataVersion = version >= PolarConstants.VERSION_DATA_CONVERTER
                ? getVarInt(bb)
                : dataConverter.defaultDataVersion();

        polarGenerator.setDataVersion(dataVersion);

        byte compressionByte = bb.readByte();
        CompressionType compression = CompressionType.fromId(compressionByte);
        assertThat(compression != null, "Invalid compression type");

        int compressedDataLength = getVarInt(bb);

        // Replace the buffer with a "decompressed" version.
        ByteBuf uncompressed = decompressBuffer(bb, compression, compressedDataLength);

        byte minSection = uncompressed.readByte();
        byte maxSection = uncompressed.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        // User (world) data
        byte[] userData = new byte[0];
        if (version > PolarConstants.VERSION_WORLD_USERDATA) {
            int userDataLength = getVarInt(uncompressed);
            byte[] bytes = new byte[userDataLength];
            uncompressed.readBytes(bytes);
            userData = bytes;
        }

        polarGenerator.setUserData(userData);

        int chunkCount = getVarInt(uncompressed);
        for (int i = 0; i < chunkCount; i++) {
            readChunk(world, dataConverter, worldAccess, version, dataVersion, uncompressed, maxSection - minSection + 1);
        }
    }

    private static void readChunk(World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess, short version, int dataVersion, @NotNull ByteBuf bb, int sectionCount) {
        var chunkX = getVarInt(bb);
        var chunkZ = getVarInt(bb);

        world.loadChunk(chunkX, chunkZ, false);

        for (int i = 0; i < sectionCount; i++) {
            readSection(world, i, chunkX, chunkZ, dataConverter, version, dataVersion, bb);
        }

        int blockEntityCount = getVarInt(bb);
        for (int i = 0; i < blockEntityCount; i++) {
            readBlockEntity(dataConverter, world, chunkX, chunkZ, dataVersion, bb);
        }

        // If the version is set to 8 copy the contents over to the beginning of userdata
        List<PolarEntity> entities = null;
        if (version == PolarConstants.VERSION_DEPRECATED_ENTITIES) {
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

        var heightmaps = new int[PolarConstants.MAX_HEIGHTMAPS][];
        int heightmapMask = bb.readInt();
        for (int i = 0; i < PolarConstants.MAX_HEIGHTMAPS; i++) {
            if ((heightmapMask & (1 << i)) == 0) continue;

            long[] packed = getLongArray(bb);
            if (packed.length == 0) {
                heightmaps[i] = new int[0];
            } else {
                var bitsPerEntry = packed.length * 64 / PolarConstants.HEIGHTMAP_SIZE;
                heightmaps[i] = new int[PolarConstants.HEIGHTMAP_SIZE];
                PaletteUtil.unpack(heightmaps[i], packed, bitsPerEntry);
            }
        }
        // TODO: use what normal polar does to skip bytes here

        // Objects
        int userDataLength = getVarInt(bb);
        byte[] userData = new byte[userDataLength];
        bb.readBytes(userData);

        if (entities != null) { // deprecated entities
            ByteArrayDataOutput newData = ByteStreams.newDataOutput();
            newData.writeByte((byte) 1);
            EntityUtil.writeEntities(entities, newData);

            userData = newData.toByteArray();
        }

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;

        worldAccess.populateChunkData(chunkHolderManager, chunkHolder, userData);
    }

    private static void readSection(World world, int sectionI, int chunkX, int chunkZ, @NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull ByteBuf bb) {
        // If section is empty exit immediately
        if (bb.readByte() == 1) return;

        String[] blockPalette = getStringList(bb, MAX_BLOCK_PALETTE_SIZE);
        if (dataVersion < dataConverter.dataVersion()) {
            dataConverter.convertBlockPalette(blockPalette, dataVersion, dataConverter.dataVersion());
        }
        if (version <= PolarConstants.VERSION_SHORT_GRASS) {
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
            blockData = new int[PolarConstants.BLOCK_PALETTE_SIZE];

            long[] rawBlockData = getLongArray(bb);
            int bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        String[] biomePalette = getStringList(bb, MAX_BIOME_PALETTE_SIZE);
        int[] biomeData;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarConstants.BIOME_PALETTE_SIZE];

            long[] rawBiomeData = getLongArray(bb);
            int bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        byte[] blockLight = null;
        byte[] skyLight = null;
        LightContent blockLightContent = version >= PolarConstants.VERSION_IMPROVED_LIGHT
                ? LightContent.VALUES[bb.readByte()]
                : ((bb.readByte() == 1) ? LightContent.PRESENT : LightContent.MISSING);
        if (blockLightContent == LightContent.PRESENT) blockLight = getLightData(bb);
        LightContent skyLightContent = version >= PolarConstants.VERSION_IMPROVED_LIGHT
                ? LightContent.VALUES[bb.readByte()]
                : (bb.readByte() == 1 ? LightContent.PRESENT : LightContent.MISSING);
        if (skyLightContent == LightContent.PRESENT) skyLight = getLightData(bb);


        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return;

        LevelChunkSection levelChunkSection = chunkAccess.getSection(sectionI);

        loadSection(blockPalette, blockData, chunkAccess, levelChunkSection, sectionI);
    }

    public static void loadSection(String[] rawBlockPalette, int[] blockData, @NotNull ChunkAccess chunkAccess, LevelChunkSection chunkAccessSection, int sectionI) {
        // Blocks
        BlockState[] materialPalette = new BlockState[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                materialPalette[i] = ((CraftBlockData) Bukkit.getServer().createBlockData(rawBlockPalette[i])).getState();
            } catch (IllegalArgumentException e) {
                PolarPaper.logger().warning("Failed to parse block state: " + rawBlockPalette[i]);
                materialPalette[i] = Blocks.AIR.defaultBlockState();
            }
        }

        var bitsPerEntry = (int) Math.ceil(Math.log(rawBlockPalette.length) / Math.log(2));

//        PalettedContainer<BlockState> states = chunkAccessSection.getStates();
        PalettedContainerRO<Holder<Biome>> biomes = chunkAccessSection.getBiomes();


        Strategy<BlockState> strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), strategy, materialPalette);


        if (blockData == null) {
            if (materialPalette.length == 1) {
                BlockState first = materialPalette[0];
                if (first.isAir()) return;
            }
        }

        if (blockData == null || bitsPerEntry == 0) {
            states.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCount(0),
                    new ZeroBitStorage(4096),
                    PaletteUtil.createPalette(0, Arrays.asList(materialPalette))
            );
        } else {
            states.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCount(bitsPerEntry),
                    new SimpleBitStorage(Math.max(4, bitsPerEntry), blockData.length, blockData),
                    PaletteUtil.createPalette(bitsPerEntry, Arrays.asList(materialPalette))
            );
        }

        LevelChunkSection newLevelChunkSection = new LevelChunkSection(
                states,
                biomes.copy() // TODO: do biomes
        );
        chunkAccess.getSections()[sectionI] = newLevelChunkSection;

//        chunkAccessSection.recalcBlockCounts();
    }

    private static void readBlockEntity(@NotNull PolarDataConverter dataConverter, World world, int chunkX, int chunkZ, int dataVersion, @NotNull ByteBuf bb) {
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
        if (nbt == null) return;

        int x = CoordConversion.chunkBlockIndexGetX(posIndex);
        int y = CoordConversion.chunkBlockIndexGetY(posIndex);
        int z = CoordConversion.chunkBlockIndexGetZ(posIndex);

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return;

        BlockState blockState = chunkAccess.getBlockState(x, y, z);
        BlockPos blockPos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, blockState, nbt, registryAccess);
        if (blockEntity == null) return;
        chunkAccess.blockEntities.put(new BlockPos(x, y, z), blockEntity);
    }

    private static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Polar version. Versions %d - %d are supported, found %d.",
                PolarConstants.LATEST_VERSION, PolarConstants.MIN_VERSION, version);
        assertThat((version <= PolarConstants.LATEST_VERSION && version >= PolarConstants.MIN_VERSION) || version == PolarConstants.VERSION_DEPRECATED_ENTITIES,
                invalidVersionError);
    }

    private static @NotNull ByteBuf decompressBuffer(@NotNull ByteBuf buffer, @NotNull CompressionType compression, int compressedLength) {
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
