package live.minehub.polarpaper;

import live.minehub.polarpaper.util.CoordConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.logging.Logger;

public class PolarGenerator extends ChunkGenerator {

    private static final Logger LOGGER = Logger.getLogger(PolarGenerator.class.getName());

    private static final int CHUNK_SECTION_SIZE = 16;

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
        if (chunk == null) {
            if (!config.allowWorldExpansion()) return;

            int worldHeight = worldInfo.getMaxHeight() - worldInfo.getMinHeight() + 1;
            int sectionCount = worldHeight / 16;

            PolarChunk newChunk = new PolarChunk(chunkX, chunkZ, sectionCount);
            polarWorld.updateChunkAt(chunkX, chunkZ, newChunk);
            return;
        }

        int i = 0;
        for (PolarSection section : chunk.sections()) {
            int yLevel = chunkData.getMinHeight() + (i++) * CHUNK_SECTION_SIZE;
            loadSection(section, yLevel, chunkData);
        }

        // TODO: load light

        for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
            loadBlockEntity(blockEntity, chunkData, chunkX, chunkZ);
        }

        this.worldAccess.loadHeightmaps(chunkData, chunk.heightmaps());

        if (chunk.userData().length > 0) {
            this.worldAccess.loadChunkData(chunkData, chunk.userData());
        }
    }

    private void loadBlockEntity(@NotNull PolarChunk.BlockEntity polarBlockEntity, @NotNull ChunkData chunkData, int chunkX, int chunkZ) {
        CompoundTag compoundTag = polarBlockEntity.data();
        if (compoundTag == null) return;

        int x = CoordConversion.chunkBlockIndexGetX(polarBlockEntity.index());
        int y = CoordConversion.chunkBlockIndexGetY(polarBlockEntity.index());
        int z = CoordConversion.chunkBlockIndexGetZ(polarBlockEntity.index());
        BlockData blockData = chunkData.getBlockData(x, y, z);

        if (polarBlockEntity.id() != null) compoundTag.putString("id", polarBlockEntity.id());

        BlockState blockState = ((CraftBlockState) blockData.createBlockState()).getHandle();
        BlockPos blockPos = new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z);

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, blockState, compoundTag, registryAccess);
        if (blockEntity == null) return;

        // ((CraftChunkData)chunkData).getHandle().setBlockEntity(blockEntity);
        ((CraftChunkData)chunkData).getHandle().blockEntities.put(blockPos, blockEntity);
    }

    private void loadSection(@NotNull PolarSection section, int yLevel, @NotNull ChunkData chunkData) {
        // Blocks
        String[] rawBlockPalette = section.blockPalette();
        BlockData[] materialPalette = new BlockData[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                materialPalette[i] = Bukkit.getServer().createBlockData(rawBlockPalette[i]);
            } catch (IllegalArgumentException e) {
                LOGGER.warning("Failed to parse block state: " + rawBlockPalette[i]);
                materialPalette[i] = Bukkit.getServer().createBlockData("minecraft:air");
            }
        }

        if (materialPalette.length == 1) {
            BlockData material = materialPalette[0];
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        chunkData.setBlock(x, yLevel + y, z, material);
                    }
                }
            }
        } else {
            int[] blockData = section.blockData();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int index = y * CHUNK_SECTION_SIZE * CHUNK_SECTION_SIZE + z * CHUNK_SECTION_SIZE + x;
                        BlockData material = materialPalette[blockData[index]];
                        chunkData.setBlock(x, yLevel + y, z, material);
                    }
                }
            }
        }
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
