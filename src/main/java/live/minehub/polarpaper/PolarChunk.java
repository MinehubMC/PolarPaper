package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.util.ByteArrayUtil;
import live.minehub.polarpaper.util.CoordConversion;
import live.minehub.polarpaper.util.PaletteUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.LevelLightEngine;
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
        BlockEntity[] blockEntities,
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
        this(x, z, new PolarSection[sectionCount], new BlockEntity[0], new int[PolarChunk.MAX_HEIGHTMAPS][0], new byte[0]);
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
     * Converts a bukkit world chunk to a polar chunk without light data
     * @param world The bukkit world
     * @param chunkX The X coordinate of the chunk in the bukkit world
     * @param chunkZ The Z coordinate of the chunk in the bukkit world
     * @param blockSelector Used to filter which blocks are converted
     * @return The new PolarChunk
     */
    public static PolarChunk convert(World world, int chunkX, int chunkZ, PolarWorldAccess worldAccess, BlockSelector blockSelector) {
        return convert(world, chunkX, chunkZ, worldAccess, blockSelector, false);
    }

    /**
     * Converts a bukkit world chunk to a polar chunk
     * @param world The bukkit world
     * @param chunkX The X coordinate of the chunk in the bukkit world
     * @param chunkZ The Z coordinate of the chunk in the bukkit world
     * @param blockSelector Used to filter which blocks are converted
     * @param saveLight Whether to save light data
     * @return The new PolarChunk
     */
    public static PolarChunk convert(World world, int chunkX, int chunkZ, PolarWorldAccess worldAccess, BlockSelector blockSelector, boolean saveLight) {
        ServerLevel chunkSystemServerLevel = ((CraftWorld) world).getHandle();
        ChunkHolderManager chunkHolderManager = chunkSystemServerLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;
        return convert(chunkHolderManager.getChunkHolder(chunkX, chunkZ), worldAccess, blockSelector, saveLight ? chunkSystemServerLevel.getLightEngine() : null);
    }


    public static PolarChunk convert(NewChunkHolder chunkHolder, PolarWorldAccess worldAccess, BlockSelector blockSelector, @Nullable LevelLightEngine lightEngine) {
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        ChunkEntitySlices entityChunk = chunkHolder.getEntityChunk();
        int chunkX = chunkHolder.chunkX;
        int chunkZ = chunkHolder.chunkZ;

        Registry<Biome> biomeRegistry = MinecraftServer.getServer().registryAccess().lookupOrThrow(Registries.BIOME);

        int sectionCount = chunkAccess.getSectionsCount();
        int minSection = chunkAccess.getMinSectionY();

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            LevelChunkSection chunkAccessSection = chunkAccess.getSection(i);
            sections[i] = convertSection(chunkX, chunkZ, chunkAccessSection, biomeRegistry, blockSelector, minSection, i, lightEngine);
        }

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        Set<Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>> blockEntities = chunkAccess.blockEntities.entrySet();
        List<PolarChunk.BlockEntity> polarBlockEntities = new ArrayList<>();
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

        ByteBuf userDataOutput = Unpooled.directBuffer();
        List<net.minecraft.world.entity.Entity> allEntities = entityChunk == null ? List.of() : entityChunk.getAllEntities();
        List<org.bukkit.entity.Entity> newAllEntities = new ArrayList<>();
        for (net.minecraft.world.entity.Entity ent : allEntities) {
            if (blockSelector.test(ent.getBlockX(), ent.getBlockY(), ent.getBlockZ())) newAllEntities.add(ent.getBukkitEntity());
        }
        org.bukkit.entity.Entity[] entitiesArray = newAllEntities.toArray(new org.bukkit.entity.Entity[0]);
        worldAccess.saveChunkData(chunkAccess, blockEntities, entitiesArray, userDataOutput);
        byte[] userData = ByteArrayUtil.outputArray(userDataOutput);

        return new PolarChunk(
                chunkX,
                chunkZ,
                sections,
                polarBlockEntities.toArray(new PolarChunk.BlockEntity[0]),
                heightMaps,
                userData
        );
    }

    private static PolarSection convertSection(int chunkX, int chunkZ, LevelChunkSection chunkAccessSection, Registry<Biome> biomeRegistry, BlockSelector blockSelector, int minSection, int sectionI, @Nullable LevelLightEngine lightEngine) {
        long[] blockData = null;
        long[] biomeData;

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

            // TODO: measure time impact of this
            BitStorage blockBitStorage = blockPaletteData.storage().copy();
            for (int index = 0; index < blockBitStorage.getSize(); ++index) {
                boolean included = blockSelector.test(index, chunkX, chunkZ, minSection + sectionI);
                if (included) continue;
                blockBitStorage.set(index, airIndex);
            }

            int bitsPerEntry = (int) Math.ceil(Math.log(blockPaletteStrings.size()) / Math.log(2));
            if (4 > bitsPerEntry && blockBitStorage.getBits() != 0) {
                int[] ints = new int[blockBitStorage.getSize()];
                PaletteUtil.unpack(ints, blockBitStorage.getRaw(), blockBitStorage.getBits());
                blockData = PaletteUtil.pack(ints, bitsPerEntry);
            } else {
                blockData = blockBitStorage.getRaw();
            }
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
        biomeData = biomeBitStorage.getRaw();

        PolarSection.LightContent blockLightContent = PolarSection.LightContent.MISSING;
        PolarSection.LightContent skyLightContent = PolarSection.LightContent.MISSING;
        byte[] blockLight = new byte[2048];
        byte[] skyLight = new byte[2048];

        if (lightEngine != null) {
            DataLayer skyLightArray = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkX, minSection + sectionI, chunkZ));
            DataLayer blockLightArray = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkX, minSection + sectionI, chunkZ));

            if (skyLightArray != null) {
                skyLight = skyLightArray.getData();
                skyLightContent = PolarSection.LightContent.PRESENT;
            }
            if (blockLightArray != null) {
                blockLight = blockLightArray.getData();
                blockLightContent = PolarSection.LightContent.PRESENT;
            }
        }

        return new PolarSection(
                blockPaletteStrings.toArray(new String[0]), blockData,
                biomePaletteStrings.toArray(new String[0]), biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

}