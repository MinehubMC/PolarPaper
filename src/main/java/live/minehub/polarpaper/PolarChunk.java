package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import live.minehub.polarpaper.util.CoordConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record PolarChunk(
        int x,
        int z,
        PolarSection[] sections,
        List<BlockEntity> blockEntities,
        int[][] heightmaps,
        byte[] userData
) {

    public static final int HEIGHTMAP_NONE = 0b0;
    public static final int HEIGHTMAP_MOTION_BLOCKING = 0b1;
    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0b10;
    public static final int HEIGHTMAP_OCEAN_FLOOR = 0b100;
    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0b1000;
    public static final int HEIGHTMAP_WORLD_SURFACE = 0b10000;
    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0b100000;
    static final int[] HEIGHTMAPS = new int[]{
            HEIGHTMAP_NONE,
            HEIGHTMAP_MOTION_BLOCKING,
            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
            HEIGHTMAP_OCEAN_FLOOR,
            HEIGHTMAP_OCEAN_FLOOR_WG,
            HEIGHTMAP_WORLD_SURFACE,
            HEIGHTMAP_WORLD_SURFACE_WG,
    };
    static final int HEIGHTMAP_SIZE = 16 * 16; // Chunk Size X * Chunk Size Z
    static final int MAX_HEIGHTMAPS = 32;

    public int @Nullable [] heightmap(int type) {
        return heightmaps[type];
    }

    public boolean isEmpty() {
        for (PolarSection section : sections) {
            if (!section.isEmpty()) return false;
        }
        return true;
    }

    public PolarChunk(int x, int z, int sectionCount) {
        // Blank chunk
        this(x, z, new PolarSection[sectionCount], List.of(), new int[PolarChunk.MAX_HEIGHTMAPS][0], new byte[0]);
        Arrays.setAll(sections, (i) -> new PolarSection());
    }

    public PolarChunk withUserData(byte[] newUserData) {
        return new PolarChunk(x, z, sections, blockEntities, heightmaps, newUserData);
    }

    public record BlockEntity(
            int index,
            @Nullable String id,
            @Nullable CompoundTag data
    ) {

    }

    /**
     * Converts a bukkit world chunk to a polar chunk
     * @param world The bukkit world
     * @param chunkX The X coordinate of the chunk in the bukkit world
     * @param chunkZ The Z coordinate of the chunk in the bukkit world
     * @param blockSelector Used to filter which blocks are converted
     * @return The new PolarChunk
     */
    public static PolarChunk convert(World world, int chunkX, int chunkZ, PolarWorldAccess worldAccess, BlockSelector blockSelector) {
        ChunkSystemServerLevel chunkSystemServerLevel = ((CraftWorld) world).getHandle();
        ChunkHolderManager chunkHolderManager = chunkSystemServerLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        return convert(chunkHolderManager.getChunkHolder(chunkX, chunkZ), worldAccess, blockSelector);
    }


    public static PolarChunk convert(NewChunkHolder chunkHolder, PolarWorldAccess worldAccess, BlockSelector blockSelector) {
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();
        int chunkX = chunkHolder.chunkX;
        int chunkZ = chunkHolder.chunkZ;

        List<PolarChunk.BlockEntity> polarBlockEntities = new ArrayList<>();

        Registry<Biome> biomeRegistry = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);

        int sectionCount = chunkAccess.getSectionsCount();
        int minSection = chunkAccess.getMinSectionY();

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            LevelChunkSection chunkAccessSection = chunkAccess.getSection(i);

            int[] blockData = null;
            int[] biomeData;

            List<String> blockPaletteStrings = new ArrayList<>();
            List<String> biomePaletteStrings = new ArrayList<>();
            if (!chunkAccessSection.hasOnlyAir()) {
                PalettedContainer.Data<BlockState> blockPaletteData = chunkAccessSection.getStates().data;
                Object[] palette = blockPaletteData.palette().moonrise$getRawPalette(blockPaletteData);
                for (Object p : palette) {
                    if (p == null) continue;
                    if (!(p instanceof BlockState blockState)) continue;
                    blockPaletteStrings.add(blockState.toString()
                            .replace("Block{", "").replace("}", "")); // e.g. Block{minecraft:oak_fence}[...] to minecraft:oak_fence[...]
                }

                int airIndex = blockPaletteStrings.indexOf("minecraft:air");
                if (airIndex == -1) {
                    blockPaletteStrings.add("minecraft:air");
                    airIndex = blockPaletteStrings.size() - 1;
                }

                BitStorage blockBitStorage = blockPaletteData.storage();
                int blockPaletteSize = blockBitStorage.getSize();
                blockData = new int[blockPaletteSize];

                for(int index = 0; index < blockPaletteSize; ++index) {
                    boolean included = blockSelector.test(index, chunkX, chunkZ, minSection + i);
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
                blockPaletteStrings.add(Blocks.AIR.defaultBlockState().toString()
                        .replace("Block{", "").replace("}", ""));
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

            for(int index = 0; index < biomePaletteSize; ++index) {
                int paletteIdx = biomeBitStorage.get(index);// TODO: use blockselector here
                biomeData[index] = paletteIdx;
            }

            sections[i] = new PolarSection(
                    blockPaletteStrings.toArray(new String[0]), blockData,
                    biomePaletteStrings.toArray(new String[0]), biomeData,
                    PolarSection.LightContent.MISSING, null, // TODO: Provide block light
                    PolarSection.LightContent.MISSING, null
            );
        }

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        Set<Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>> blockEntities = chunkAccess.blockEntities.entrySet();
        for (Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry : blockEntities) {
            BlockPos blockPos = entry.getKey();
            net.minecraft.world.level.block.entity.BlockEntity blockEntity = entry.getValue();

            if (blockPos == null || blockEntity == null) continue;
            if (!blockSelector.test(blockPos.getX(), blockPos.getY(), blockPos.getZ())) continue;

            CompoundTag compoundTag = blockEntity.saveWithFullMetadata(registryAccess);

            Optional<String> id = compoundTag.getString("id");
            if (id.isEmpty()) {
                PolarPaper.logger().warning("No ID in block entity data at: " + blockPos);
                PolarPaper.logger().warning("Compound tag: " + compoundTag);
                continue;
            }

            int index = CoordConversion.chunkBlockIndex(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            polarBlockEntities.add(new PolarChunk.BlockEntity(index, id.get(), compoundTag));
        }

        int[][] heightMaps = new int[PolarChunk.MAX_HEIGHTMAPS][0];
        worldAccess.saveHeightmaps(chunkAccess, heightMaps);

        ByteArrayDataOutput userDataOutput = ByteStreams.newDataOutput();
        List<net.minecraft.world.entity.Entity> allEntities = entityChunk == null ? List.of() : entityChunk.getAllEntities();
        List<org.bukkit.entity.Entity> newAllEntities = new ArrayList<>();
        for (net.minecraft.world.entity.Entity ent : allEntities) {
            if (blockSelector.test(ent.getBlockX(), ent.getBlockY(), ent.getBlockZ())) newAllEntities.add(ent.getBukkitEntity());
        }
        org.bukkit.entity.Entity[] entitiesArray = newAllEntities.toArray(new org.bukkit.entity.Entity[0]);
        worldAccess.saveChunkData(chunkAccess, blockEntities, entitiesArray, userDataOutput);
        byte[] userData = userDataOutput.toByteArray();

        return new PolarChunk(
                chunkX,
                chunkZ,
                sections,
                polarBlockEntities,
                heightMaps,
                userData
        );
    }

}