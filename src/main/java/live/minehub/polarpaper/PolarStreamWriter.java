package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static live.minehub.polarpaper.util.ByteArrayUtil.*;

public class PolarStreamWriter {

    private PolarStreamWriter() {
    }



    public static byte[] write(@NotNull World world, byte[] userData, BlockSelector blockSelector) {
        return write(world, userData, blockSelector, false, CompressionType.ZSTD, PolarDataConverter.DEFAULT, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    public static byte[] write(@NotNull World world, byte[] userData, BlockSelector blockSelector, boolean skipUnsaved, CompressionType compression, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess) {
        ByteBuf bb = Unpooled.directBuffer();
        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight() - 1;
        int minSection = (byte) CoordConversion.sectionIndex(minHeight);
        int maxSection = (byte) CoordConversion.sectionIndex(maxHeight);

        bb.writeByte(minSection);
        bb.writeByte(maxSection);
        writeVarInt(userData.length, bb);
        bb.writeBytes(userData);

        ChunkSystemServerLevel chunkSystemServerLevel = ((CraftWorld) world).getHandle();
        ChunkHolderManager chunkHolderManager = chunkSystemServerLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        List<NewChunkHolder> chunkHoldersToWrite = new ArrayList<>();
        for (NewChunkHolder chunkHolder : chunkHolderManager.getChunkHolders()) {
            if (chunkHolder == null) continue;
            ChunkAccess currentChunk = chunkHolder.getCurrentChunk();
            if (currentChunk == null) continue;
            if (!blockSelector.testChunk(chunkHolder.chunkX, chunkHolder.chunkZ)) continue;

            ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();
            boolean unsaved = blockSelector == BlockSelector.ALL || !skipUnsaved || currentChunk.isUnsaved(); // if selector is not ALL blocks, we need to update

            boolean onlyPlayers = true;
            if (entityChunk != null) {
                for (net.minecraft.world.entity.Entity nmsEntity : entityChunk.getAllEntities()) {
                    Entity entity = nmsEntity.getBukkitEntity();
                    if (entity.getType() != EntityType.PLAYER) {
                        onlyPlayers = false;
                        break;
                    }
                }
            }

            if (onlyPlayers) { // if contains no entities or the entities are all players (only difference is blocks)
                if (!unsaved) continue;

                boolean allEmpty = true;
                for (LevelChunkSection section : currentChunk.getSections()) {
                    if (!section.hasOnlyAir()) {
                        allEmpty = false;
                        break;
                    }
                }

                if (allEmpty) {
                    // check if the chunk has generated the surface yet
                    // (otherwise we don't know if it's blank because its really blank, or because it hasn't generated yet)
                    if (currentChunk.getPersistedStatus().isOrBefore(ChunkStatus.SURFACE)) continue;
                    currentChunk.tryMarkSaved();
                    continue;
                }
            } else {
                // TODO: maybe redo this optimisation - unsaved boolean may include entities however... needs more testing
//                if (!unsaved) { // if only difference is entities
//                    PolarChunk prevChunk = chunkAt(chunkX, chunkZ);
//                    if (prevChunk == null) continue;
//
//                    // only update entities
//                    ByteArrayDataOutput userDataOutput = ByteStreams.newDataOutput();
//                    List<net.minecraft.world.entity.Entity> allEntities = entityChunk.getAllEntities();
//                    Entity[] entitiesArray = new Entity[allEntities.size()];
//                    for (int i = 0; i < allEntities.size(); i++) {
//                        entitiesArray[i] = allEntities.get(i).getBukkitEntity();
//                    }
//                    polarWorldAccess.saveChunkData(currentChunk, currentChunk.blockEntities.entrySet(), entitiesArray, userDataOutput);
//                    byte[] userData = userDataOutput.toByteArray();
//
//                    updateChunkAt(chunkX, chunkZ, prevChunk.withUserData(userData));
//
//                    continue;
//                }
            }

            chunkHoldersToWrite.add(chunkHolder);

            if (!skipUnsaved) currentChunk.tryMarkSaved();
        }

        writeVarInt(chunkHoldersToWrite.size(), bb);
        for (NewChunkHolder chunkHolder : chunkHoldersToWrite) {
            writeChunk(bb, chunkHolder, worldAccess, blockSelector);
        }

        byte[] contentBytes = ByteArrayUtil.outputArray(bb);


        // Create final buffer
        ByteBuf finalBB = Unpooled.directBuffer();
        finalBB.writeInt(PolarConstants.MAGIC_NUMBER);
        finalBB.writeShort(PolarConstants.LATEST_VERSION);
        writeVarInt(dataConverter.dataVersion(), finalBB);
        finalBB.writeByte(compression.ordinal());
        switch (compression) {
            case NONE -> {
                writeVarInt(contentBytes.length, finalBB);
                finalBB.writeBytes(contentBytes);
            }
            case ZSTD -> {
                writeVarInt(contentBytes.length, finalBB);
                finalBB.writeBytes(Zstd.compress(contentBytes));
            }
        }

        return ByteArrayUtil.outputArray(finalBB);
    }

    private static void writeSection(@NotNull ByteBuf bb, int chunkX, int chunkZ, ChunkAccess chunkAccess, int sectionI, int minSection, BlockSelector blockSelector) {
        Registry<Biome> biomeRegistry = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);

        LevelChunkSection chunkAccessSection = chunkAccess.getSection(sectionI);

        int[] blockData;
        int[] biomeData;

        List<String> blockPaletteStrings = new ArrayList<>();
        List<String> biomePaletteStrings = new ArrayList<>();
        if (!chunkAccessSection.hasOnlyAir()) {
            PalettedContainer.Data<BlockState> blockPaletteData = chunkAccessSection.getStates().data;
            Palette<BlockState> chunkPalette = blockPaletteData.palette();
            if (chunkPalette instanceof GlobalPalette<BlockState> globalPalette) {
                for (int i1 = 0; i1 < globalPalette.getSize(); i1++) {
                    BlockState blockState = globalPalette.valueFor(i1);
                    blockPaletteStrings.add(blockState.toString()
                            .replace("Block{", "").replace("}", "")); // e.g. Block{minecraft:oak_fence}[...] to minecraft:oak_fence[...]
                }
            } else {
                Object[] palette = chunkPalette.moonrise$getRawPalette(blockPaletteData);
                if (palette != null) {
                    for (Object p : palette) {
                        if (p == null) continue;
                        if (!(p instanceof BlockState blockState)) continue;
                        blockPaletteStrings.add(blockState.toString()
                                .replace("Block{", "").replace("}", "")); // e.g. Block{minecraft:oak_fence}[...] to minecraft:oak_fence[...]
                    }
                }
            }

            int airIndex = blockPaletteStrings.indexOf("minecraft:air");
            if (airIndex == -1) {
                blockPaletteStrings.add("minecraft:air");
                airIndex = blockPaletteStrings.size() - 1;
            }

            BitStorage blockBitStorage = blockPaletteData.storage();
            int blockPaletteSize = blockBitStorage.getSize();
            blockData = new int[blockPaletteSize];

            for (int index = 0; index < blockPaletteSize; ++index) {
                boolean included = blockSelector.test(index, chunkX, chunkZ, minSection + sectionI);
                if (included) {
                    int paletteIdx = blockBitStorage.get(index);
                    blockData[index] = paletteIdx;
                } else {
                    blockData[index] = airIndex;
                }
            }

            // TODO: trim the palette (needed?)
//                // remove unused blocks from the palette
//                blockPaletteStrings = Arrays.stream(blockData).distinct().mapToObj(blockPaletteStrings::get).toList();
        } else {
            // section is empty
            bb.writeByte(1);
            return;
//            blockPaletteStrings.add(Blocks.AIR.defaultBlockState().toString()
//                    .replace("Block{", "").replace("}", ""));
        }
        PalettedContainer.Data<Holder<Biome>> biomePaletteData = ((PalettedContainer<Holder<Biome>>)chunkAccessSection.getBiomes()).data;
        Object[] biomePalette = biomePaletteData.palette().moonrise$getRawPalette(biomePaletteData);
        for (Object p : biomePalette) {
            if (p == null) continue;
            if (!(p instanceof Holder<?> biomeHolder)) continue;
            if (!(biomeHolder.value() instanceof Biome biome)) continue;
            Identifier key = biomeRegistry.getKey(biome);
            if (key == null) continue;
            String biomeString = key.getPath();
            biomePaletteStrings.add(biomeString);
        }

        BitStorage biomeBitStorage = biomePaletteData.storage();
        int biomePaletteSize = biomeBitStorage.getSize();
        biomeData = new int[biomePaletteSize];

        for (int index = 0; index < biomePaletteSize; ++index) {
            int paletteIdx = biomeBitStorage.get(index);// TODO: use blockselector here
            biomeData[index] = paletteIdx;
        }

        // Section is not empty by this point
        bb.writeByte(0);

        // Blocks
        writeStringCollection(blockPaletteStrings, bb);
        if (blockPaletteStrings.size() > 1) {
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPaletteStrings.size()) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            writeLongArray(PaletteUtil.pack(blockData, bitsPerEntry), bb);
        }

        // Biomes
        writeStringCollection(biomePaletteStrings, bb);
        if (biomePaletteStrings.size() > 1) {
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePaletteStrings.size()) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            writeLongArray(PaletteUtil.pack(biomeData, bitsPerEntry), bb);
        }

        // Light
        // TODO: do eventually
        bb.writeByte((byte) LightContent.MISSING.ordinal());
        bb.writeByte((byte) LightContent.MISSING.ordinal());
//        bb.write((byte) section.blockLightContent().ordinal());
//        if (section.blockLightContent() == PolarSection.LightContent.PRESENT)
//            bb.write(section.blockLight());
//        bb.write((byte) section.skyLightContent().ordinal());
//        if (section.skyLightContent() == PolarSection.LightContent.PRESENT)
//            bb.write(section.skyLight());
    }

    /**
     * Converts a bukkit world chunk to a polar chunk
     * @param world The bukkit world
     * @param chunkX The X coordinate of the chunk in the bukkit world
     * @param chunkZ The Z coordinate of the chunk in the bukkit world
     * @param blockSelector Used to filter which blocks are converted
     */
    public static void writeChunk(ByteBuf bb, World world, int chunkX, int chunkZ, PolarWorldAccess worldAccess, BlockSelector blockSelector) {
        ChunkSystemServerLevel chunkSystemServerLevel = ((CraftWorld) world).getHandle();
        ChunkHolderManager chunkHolderManager = chunkSystemServerLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        writeChunk(bb, chunkHolderManager.getChunkHolder(chunkX, chunkZ), worldAccess, blockSelector);
    }

    public static void writeChunk(ByteBuf bb, NewChunkHolder chunkHolder, PolarWorldAccess worldAccess, BlockSelector blockSelector) {
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();
        int chunkX = chunkHolder.chunkX;
        int chunkZ = chunkHolder.chunkZ;

        int[][] heightMaps = new int[PolarConstants.MAX_HEIGHTMAPS][0];
        worldAccess.saveHeightmaps(chunkAccess, heightMaps);

        writeVarInt(chunkX, bb);
        writeVarInt(chunkZ, bb);

        int sectionCount = chunkAccess.getSectionsCount();
        int minSection = chunkAccess.getMinSectionY();
        for (int i = 0; i < sectionCount; i++) {
            writeSection(bb, chunkX, chunkZ, chunkAccess, i, minSection, blockSelector);
        }

        ByteBuf userDataOutput = Unpooled.directBuffer();
        writeBlockEntities(bb, chunkAccess, entityChunk, blockSelector, worldAccess, userDataOutput);
        byte[] userData = ByteArrayUtil.outputArray(userDataOutput);

        writeHeightMaps(bb, sectionCount, heightMaps);

        writeByteArray(userData, bb);
    }

    private static void writeHeightMaps(ByteBuf bb, int sectionCount, int[][] heightMaps) {
        int heightmapBits = 0;
        for (int i = 0; i < PolarConstants.MAX_HEIGHTMAPS; i++) {
            if (heightMaps[i] != null)
                heightmapBits |= 1 << i;
        }
        bb.writeInt(heightmapBits);

        int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * PolarConstants.CHUNK_SECTION_SIZE);
        for (int i = 0; i < PolarConstants.MAX_HEIGHTMAPS; i++) {
            var heightmap = heightMaps[i];
            if (heightmap == null) continue;
            if (heightmap.length == 0) writeLongArray(new long[0], bb);
            else writeLongArray(PaletteUtil.pack(heightmap, bitsPerEntry), bb);
        }
    }

    private static void writeBlockEntities(ByteBuf bb, ChunkAccess chunkAccess, ChunkEntitySlices entityChunk, BlockSelector blockSelector, PolarWorldAccess worldAccess, ByteBuf userDataOutput) {
        int filteredBlockEntityCount = 0;
        Set<Map.Entry<BlockPos, BlockEntity>> blockEntities = chunkAccess.blockEntities.entrySet();
        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities) {
            BlockPos blockPos = entry.getKey();
            BlockEntity blockEntity = entry.getValue();

            if (blockPos == null || blockEntity == null) continue;
            if (!blockSelector.test(blockPos.getX(), blockPos.getY(), blockPos.getZ())) continue;

            filteredBlockEntityCount++;
        }
        writeVarInt(filteredBlockEntityCount, bb);
        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities) {
            BlockPos blockPos = entry.getKey();
            BlockEntity blockEntity = entry.getValue();

            if (blockPos == null || blockEntity == null) continue;
            if (!blockSelector.test(blockPos.getX(), blockPos.getY(), blockPos.getZ())) continue;

            writeBlockEntity(bb, blockPos, blockEntity);
        }
        List<net.minecraft.world.entity.Entity> allEntities = entityChunk == null ? List.of() : entityChunk.getAllEntities();
        List<Entity> newAllEntities = new ArrayList<>();
        for (net.minecraft.world.entity.Entity ent : allEntities) {
            if (blockSelector.test(ent.getBlockX(), ent.getBlockY(), ent.getBlockZ())) newAllEntities.add(ent.getBukkitEntity());
        }
        Entity[] entitiesArray = newAllEntities.toArray(new Entity[0]);
        worldAccess.saveChunkData(chunkAccess, blockEntities, entitiesArray, userDataOutput);
    }

}