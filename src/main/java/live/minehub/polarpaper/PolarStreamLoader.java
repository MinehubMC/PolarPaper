package live.minehub.polarpaper;

import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.poi.PoiChunk;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.util.*;
import net.kyori.adventure.key.Key;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkTaskScheduler chunkTaskScheduler = serverLevel.moonrise$getChunkTaskScheduler();
        ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            levelChunkSections[i] = readSection(serverLevel.registryAccess(), dataConverter, version, dataVersion, bb);
        }

        // Begin reflection hell :D
        // TODO: Fix "ProtoChunk cannot be cast to class LevelChunk" when rejoining while chunks are loading (scheduleTickingState)
        // TODO: Chunks are not lit and cannot be relit with /paper fixlight
        try {
            ReentrantAreaLock.Node lock1 = chunkTaskScheduler.schedulingLockArea.lock(chunkX, chunkZ);
            ReentrantAreaLock.Node lock = chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ);
            Method getOrCreateChunkHolderMethod = chunkHolderManager.getClass().getDeclaredMethod("getOrCreateChunkHolder", int.class, int.class);
            getOrCreateChunkHolderMethod.setAccessible(true);
            NewChunkHolder newChunkHolder = (NewChunkHolder) getOrCreateChunkHolderMethod.invoke(chunkHolderManager, chunkX, chunkZ);
            chunkHolderManager.ticketLockArea.unlock(lock);
            chunkTaskScheduler.schedulingLockArea.unlock(lock1);

            ChunkEntitySlices entityChunk = new ChunkEntitySlices(serverLevel, chunkX, chunkZ, FullChunkStatus.FULL, new ChunkData(), WorldUtil.getMinSection(serverLevel), WorldUtil.getMaxSection(serverLevel));
            entityChunk.setTransient(false);
            serverLevel.moonrise$getEntityLookup().entitySectionLoad(chunkX, chunkZ, entityChunk);
            Field entityChunkField = newChunkHolder.getClass().getDeclaredField("entityChunk");
            entityChunkField.setAccessible(true);
            entityChunkField.set(newChunkHolder, entityChunk);
            chunkHolderManager.getOrCreateEntityChunk(chunkX, chunkZ, false);

            NoUnloadLevelChunk newLevelChunk = new NoUnloadLevelChunk(serverLevel, new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, levelChunkSections, null, null);
            newLevelChunk.needsDecoration = false;
//            newLevelChunk.setLightCorrect(true);
            Field currentChunkField = newChunkHolder.getClass().getDeclaredField("currentChunk");
            currentChunkField.setAccessible(true);
            currentChunkField.set(newChunkHolder, newLevelChunk);
//            currentChunkField.set(newChunkHolder, imposter);

//            ImposterProtoChunk imposter = new ImposterNoBiomeChunk(newLevelChunk, false);
//            newChunkHolder.replaceProtoChunk(imposter);

//            Field chunkCompletionArrayHandleField = newChunkHolder.getClass().getDeclaredField("CHUNK_COMPLETION_ARRAY_HANDLE");
//            chunkCompletionArrayHandleField.setAccessible(true);
//            VarHandle chunkCompletionArrayHandle = (VarHandle)chunkCompletionArrayHandleField.get(newChunkHolder);
//            Field chunkCompletionsField = newChunkHolder.getClass().getDeclaredField("chunkCompletions");
//            chunkCompletionsField.setAccessible(true);
//            NewChunkHolder.ChunkCompletion[] chunkCompletions = (NewChunkHolder.ChunkCompletion[])chunkCompletionsField.get(newChunkHolder);
//            Field allStatusesField = newChunkHolder.getClass().getDeclaredField("ALL_STATUSES");
//            allStatusesField.setAccessible(true);
//            ChunkStatus[] allStatuses = (ChunkStatus[])allStatusesField.get(newChunkHolder);
//
//            for (int i = 0, max = ChunkStatus.FULL.getIndex(); i < max; ++i) {
//                chunkCompletionArrayHandle.setVolatile(chunkCompletions, i, new NewChunkHolder.ChunkCompletion(newLevelChunk, allStatuses[i]));
//            }

            Field poiChunkField = newChunkHolder.getClass().getDeclaredField("poiChunk");
            poiChunkField.setAccessible(true);
            poiChunkField.set(newChunkHolder, PoiChunk.empty(serverLevel, chunkX, chunkZ));

            Field processingFullStatus = newChunkHolder.getClass().getDeclaredField("processingFullStatus");
            processingFullStatus.setAccessible(true);
            processingFullStatus.set(newChunkHolder, true);

            Field currentFullChunkStatusField = newChunkHolder.getClass().getDeclaredField("currentFullChunkStatus");
            currentFullChunkStatusField.setAccessible(true);
            currentFullChunkStatusField.set(newChunkHolder, FullChunkStatus.FULL);
            Field pendingFullChunkStatusField = newChunkHolder.getClass().getDeclaredField("pendingFullChunkStatus");
            pendingFullChunkStatusField.setAccessible(true);
            pendingFullChunkStatusField.set(newChunkHolder, FullChunkStatus.FULL);

            newLevelChunk.runPostLoad();
            newLevelChunk.setLoaded(true);
            newLevelChunk.registerAllBlockEntitiesAfterLevelLoad();
            newLevelChunk.registerTickContainerInLevel(serverLevel);


            serverLevel.getChunkSource().moonrise$setFullChunk(chunkX, chunkZ, newLevelChunk);

//            Method updateTicketLevelMethod = newChunkHolder.getClass().getDeclaredMethod("updateTicketLevel", int.class);
//            updateTicketLevelMethod.setAccessible(true);
//            updateTicketLevelMethod.invoke(newChunkHolder, 0);

//            serverLevel.getChunkSource().updateChunkForced(new ChunkPos(chunkX, chunkZ), true);

//            Bukkit.getGlobalRegionScheduler().runDelayed(PolarPaper.getPlugin(), (t) -> {
//                newChunkHolder.handleFullStatusChange(List.of(newChunkHolder));
//                chunkHolderManager.addTicketAtLevel(TicketType.CHUNK_LOAD, chunkX, chunkZ, 33, null);
//                chunkHolderManager.processTicketUpdates();
//            }, 60);

//            ChunkLoadTask chunkLoadTask = new ChunkLoadTask(chunkTaskScheduler, serverLevel, chunkX, chunkZ, newChunkHolder, Priority.NORMAL);
//            chunkLoadTask.onComplete((a, b) -> {
//                Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
//                    ChunkFullTask chunkFullTask = new ChunkFullTask(chunkTaskScheduler, serverLevel, chunkX, chunkZ, newChunkHolder, newLevelChunk, Priority.NORMAL);
//                    chunkFullTask.run();
//                });
//            });
//            chunkLoadTask.schedule();
//            chunkTaskScheduler.scheduleChunkLoad(chunkX, chunkZ, ChunkStatus.FULL, true, Priority.LOWEST, ca -> {
//                System.out.println("returned : " + ca.locX + " " + ca.locZ);
//            });

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
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
            ByteBuf newData = Unpooled.directBuffer();
            newData.writeByte((byte) 1);
            EntityUtil.writeEntities(entities, newData);

            userData = outputArray(newData);
        }

        //TODO: this
//        worldAccess.populateChunkData(chunkHolderManager, chunkHolder, userData);
    }

    private static @NotNull LevelChunkSection readSection(RegistryAccess registryAccess, @NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull ByteBuf bb) {
        // If section is empty exit immediately
        if (bb.readByte() == 1) return createEmptySection(registryAccess);

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
        long[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = getLongArray(bb);
        }

        String[] biomePalette = getStringList(bb, MAX_BIOME_PALETTE_SIZE);
        long[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = getLongArray(bb);
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

        return loadSection(registryAccess, blockPalette, blockData, biomePalette, biomeData);
    }

    public static LevelChunkSection loadSection(RegistryAccess registryAccess, String[] rawBlockPalette, long[] blockData, String[] rawBiomePalette, long[] biomeData) {
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

        // Biomes
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        Holder<Biome>[] biomePalette = new Holder[rawBiomePalette.length];
        for (int i = 0; i < rawBiomePalette.length; i++) {
            Identifier identifier = Identifier.tryParse(rawBiomePalette[i]);
            if (identifier == null) {
                System.out.println("Failed to parse " + rawBiomePalette[i]);
                biomePalette[i] = orThrow;
                continue;
            }
            Holder.Reference<Biome> biome = registry.get(identifier).orElse(null);
            if (biome == null) {
                System.out.println("Failed to get " + rawBiomePalette[i]);
                biomePalette[i] = orThrow;
                continue;
            }
            biomePalette[i] = biome;
        }

        int bitsPerBlockEntry = (int) Math.ceil(Math.log(rawBlockPalette.length) / Math.log(2));
        int longBitsPerBlockEntry = bitsPerBlockEntry;
        if (blockData != null) {
            longBitsPerBlockEntry = PaletteUtil.getBitsForLongLength(blockData.length);
        }

        int bitsPerBiomeEntry = (int) Math.ceil(Math.log(rawBiomePalette.length) / Math.log(2));
        int longBitsPerBiomeEntry = bitsPerBiomeEntry;
        if (biomeData != null) {
            longBitsPerBiomeEntry = PaletteUtil.getBitsForLongLength(biomeData.length);
        }

        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, materialPalette);


        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, biomePalette);

        if (biomeData == null) {
            biomes.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCountBiome(0),
                    new ZeroBitStorage(PolarSection.BIOME_PALETTE_SIZE),
                    PaletteUtil.createPalette(0, Arrays.asList(biomePalette))
            );
        } else {
            biomes.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCountBiome(bitsPerBiomeEntry),
                    new SimpleBitStorage(bitsPerBiomeEntry, PolarSection.BIOME_PALETTE_SIZE, biomeData),
                    PaletteUtil.createPalette(bitsPerBiomeEntry, Arrays.asList(biomePalette))
            );
        }


        if (blockData == null) {
            if (materialPalette.length == 1) {
                BlockState first = materialPalette[0];
                if (first.isAir()) return createEmptySection(registryAccess);
            }
        }
        if (blockData == null || bitsPerBlockEntry == 0) {
            states.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCountBlock(0),
                    new ZeroBitStorage(PolarSection.BLOCK_PALETTE_SIZE),
                    PaletteUtil.createPalette(0, Arrays.asList(materialPalette))
            );
        } else {
            if (4 > longBitsPerBlockEntry) {
                int[] unpacked = new int[PolarSection.BLOCK_PALETTE_SIZE];
                PaletteUtil.unpack(unpacked, blockData, bitsPerBlockEntry);
                long[] newLongs = PaletteUtil.pack(unpacked, 4);

                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBlock(bitsPerBlockEntry),
                        new SimpleBitStorage(4, PolarSection.BLOCK_PALETTE_SIZE, newLongs),
                        PaletteUtil.createPalette(bitsPerBlockEntry, Arrays.asList(materialPalette))
                );
            } else {
                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBlock(bitsPerBlockEntry),
                        new SimpleBitStorage(Math.max(4, longBitsPerBlockEntry), PolarSection.BLOCK_PALETTE_SIZE, blockData),
                        PaletteUtil.createPalette(bitsPerBlockEntry, Arrays.asList(materialPalette))
                );
            }
        }

        return new LevelChunkSection(states, biomes);

//        chunkAccessSection.recalcBlockCounts();
    }

    private static LevelChunkSection createEmptySection(RegistryAccess registryAccess) {
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, null);

        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, null);

        return new LevelChunkSection(states, biomes);
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

        // TODO: reenable
        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
//        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, blockState, nbt, registryAccess);
//        if (blockEntity == null) return;
//        chunkAccess.blockEntities.put(new BlockPos(x, y, z), blockEntity);
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
