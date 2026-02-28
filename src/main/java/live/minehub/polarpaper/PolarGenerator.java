package live.minehub.polarpaper;

import live.minehub.polarpaper.util.CoordConversion;
import live.minehub.polarpaper.util.PaletteUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Random;

public class PolarGenerator extends ChunkGenerator {
    private final PolarWorld polarWorld;
    private final PolarWorldAccess worldAccess;
    private Config config;

    public PolarGenerator(PolarWorld polarWorld, Config config) {
        this(polarWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, config);
    }

    public PolarGenerator(PolarWorld polarWorld, PolarWorldAccess worldAccess, Config config) {
        this.polarWorld = polarWorld;
        this.worldAccess = worldAccess;
        this.config = config;
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        PolarChunk chunk = polarWorld.chunkAt(chunkX, chunkZ);
        if (chunk == null) return;

//        long before = System.nanoTime();

        ChunkAccess chunkAccess = ((CraftChunkData) chunkData).getHandle();
        int i = 0;
        for (PolarSection section : chunk.sections()) {
            try {
                // TODO: switching world to nether environment causes out of bounds here
                LevelChunkSection chunkAccessSection = chunkAccess.getSection(i++);
                loadSection(section, chunkAccessSection);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load section in '%s' at: chunk: %s, %s, section: %s".formatted(worldInfo.getName(), chunkX, chunkZ, i - 1), e);
            }
        }

        // TODO: load light

        for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
            loadBlockEntity(blockEntity, chunkAccess, chunkX, chunkZ);
        }

        this.worldAccess.loadHeightmaps(chunkData, chunk.heightmaps());

        if (chunk.userData().length > 0) {
            this.worldAccess.loadChunkData(chunkData, chunk.userData());
        }

//        System.out.println("Generated surface in " + (System.nanoTime() - before) + "ns");
    }

    private void loadBlockEntity(@NotNull PolarChunk.BlockEntity polarBlockEntity, @NotNull ChunkAccess chunkAccess, int chunkX, int chunkZ) {
        CompoundTag compoundTag = polarBlockEntity.data();
        if (compoundTag == null) return;
        if (polarBlockEntity.id() != null) compoundTag.putString("id", polarBlockEntity.id());

        int x = CoordConversion.chunkBlockIndexGetX(polarBlockEntity.index());
        int y = CoordConversion.chunkBlockIndexGetY(polarBlockEntity.index());
        int z = CoordConversion.chunkBlockIndexGetZ(polarBlockEntity.index());

        BlockState blockState = chunkAccess.getBlockState(x, y, z);
        BlockPos blockPos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, blockState, compoundTag, registryAccess);
        if (blockEntity == null) return;

        // chunkAccess.setBlockEntity(blockEntity);
        chunkAccess.blockEntities.put(blockPos, blockEntity);
    }

    private void loadSection(@NotNull PolarSection section, LevelChunkSection chunkAccessSection) {
        // Blocks
        long[] blockData = section.blockData();

        String[] rawBlockPalette = section.blockPalette();
        BlockState[] materialPalette = new BlockState[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                materialPalette[i] = ((CraftBlockData) Bukkit.getServer().createBlockData(rawBlockPalette[i])).getState();
            } catch (IllegalArgumentException e) {
                PolarPaper.logger().warning("Failed to parse block state: " + rawBlockPalette[i]);
                materialPalette[i] = Blocks.AIR.defaultBlockState();
            }
        }

        PalettedContainer<BlockState> states = chunkAccessSection.getStates();

        int bitsPerEntry = (int) Math.ceil(Math.log(rawBlockPalette.length) / Math.log(2));
        int longBitsPerEntry = bitsPerEntry;
        if (blockData != null) {
            longBitsPerEntry = PaletteUtil.getBitsForLongLength(blockData.length);
        }

        if (blockData == null) {
            if (materialPalette.length == 1) {
                BlockState first = materialPalette[0];
                if (first.isAir()) return;
            }
        }

//        if (blockData != null && blockData.length == 1024) {
//            return;
//        }

//        System.out.println(bitsPerEntry + ", " + longBitsPerEntry + ", " + (blockData == null ? 0 : blockData.length));

        if (blockData == null || bitsPerEntry == 0) {
            states.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCount(0),
                    new ZeroBitStorage(PolarSection.BLOCK_PALETTE_SIZE),
                    PaletteUtil.createPalette(0, Arrays.asList(materialPalette))
            );
        } else {
            if (bitsPerEntry > longBitsPerEntry) {
                int[] unpacked = new int[PolarSection.BLOCK_PALETTE_SIZE];
                PaletteUtil.unpack(unpacked, blockData, longBitsPerEntry);
                long[] newLongs = PaletteUtil.pack(unpacked, bitsPerEntry);

                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCount(bitsPerEntry),
                        new SimpleBitStorage(bitsPerEntry, PolarSection.BLOCK_PALETTE_SIZE, newLongs),
                        PaletteUtil.createPalette(bitsPerEntry, Arrays.asList(materialPalette))
                );
            } else if (4 > longBitsPerEntry) {
                int[] unpacked = new int[PolarSection.BLOCK_PALETTE_SIZE];
                PaletteUtil.unpack(unpacked, blockData, bitsPerEntry);
                long[] newLongs = PaletteUtil.pack(unpacked, 4);

                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCount(bitsPerEntry),
                        new SimpleBitStorage(4, PolarSection.BLOCK_PALETTE_SIZE, newLongs),
                        PaletteUtil.createPalette(bitsPerEntry, Arrays.asList(materialPalette))
                );
            } else {
                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCount(longBitsPerEntry),
                        new SimpleBitStorage(Math.max(4, longBitsPerEntry), PolarSection.BLOCK_PALETTE_SIZE, blockData),
                        PaletteUtil.createPalette(longBitsPerEntry, Arrays.asList(materialPalette))
                );
            }
        }

        chunkAccessSection.recalcBlockCounts();
    }

    public PolarWorld getPolarWorld() {
        return polarWorld;
    }

    public PolarWorldAccess getWorldAccess() {
        return worldAccess;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public boolean isParallelCapable() {
        return true;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        Location loc = config.spawn();
        loc.setWorld(world);
        return loc;
    }

    /**
     * Get a PolarGenerator from a Bukkit world
     * @param world The bukkit world
     * @return The PolarGenerator or null if the world is not from polar
     */
    public static @Nullable PolarGenerator fromWorld(World world) {
        ChunkGenerator generator = world.getGenerator();
        if (!(generator instanceof PolarGenerator polarGenerator)) return null;
        return polarGenerator;
    }

}
