package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.PolarChunk;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.PolarSection;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.userdata.WorldUserData;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.joml.Vector3i;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Schematic {

    public static final NamespacedKey POS_1_KEY = new NamespacedKey("polarpaper", "pos1");
    public static final NamespacedKey POS_2_KEY = new NamespacedKey("polarpaper", "pos2");

    public static void paste(PolarWorld polarWorld, World world, BlockModifier blockModifier, IgnoreAir ignoreAir) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();

        byte[] userData = polarWorld.userData();
        Vector3i offset = WorldUserData.readSchematicOffset(userData);
        if (offset == null) offset = new Vector3i();

        int minSection = craftWorld.getMinHeight() / 16;
        for (PolarChunk chunk : polarWorld.chunks()) {
            int i = 0;
            for (PolarSection section : chunk.sections()) {
                Vector3i blockOffset = new Vector3i(chunk.x() * 16, (i + minSection) * 16, chunk.z() * 16)
                        .sub(offset);
                pasteSection(section, world, blockModifier, blockOffset, ignoreAir);
                i++;
            }

            handleUserData(world, chunk, blockModifier, offset);
        }

        Set<ChunkPos> chunksToRefresh = new HashSet<>();
        for (PolarChunk chunk : polarWorld.chunks()) {
            Vector3i chunkOffset = new Vector3i(chunk.x() * 16, 0, chunk.z() * 16)
                    .sub(offset);
            blockModifier.modify(chunkOffset);

            int cX = (int)Math.floor(chunkOffset.x / 16.0);
            int cZ = (int)Math.floor(chunkOffset.z / 16.0);

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    chunksToRefresh.add(new ChunkPos(cX + x, cZ + z));
                }
            }
        }

        // refresh blocks and light
        for (ChunkPos c : chunksToRefresh) {
            world.refreshChunk(c.x, c.z);
        }
        serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunksToRefresh, a -> {}, a -> {});
    }

    private static void handleUserData(World world, PolarChunk chunk, BlockModifier blockModifier, Vector3i offset) {
        if (chunk.userData() == null || chunk.userData().length == 0) return;

        final var bb = ByteBuffer.wrap(chunk.userData());
        byte version = bb.get();
        List<PolarChunk.Entity> entities = EntityUtil.getEntities(bb);

        for (PolarChunk.Entity polarEntity : entities) {
            var x = polarEntity.x();
            var y = polarEntity.y();
            var z = polarEntity.z();
            var yaw = polarEntity.yaw();
            var pitch = polarEntity.pitch();
            var bytes = polarEntity.bytes();

            Entity entity;
            try {
                entity = EntityUtil.bytesToEntity(world, bytes);
                if (entity == null) continue;
            } catch (Exception e) {
                continue;
            }

            if (entity instanceof Painting painting) {
                if (painting.getArt().getBlockHeight() % 2 == 0) { // strange spigot bug
                    y--;
                }
            }

            Location loc = new Location(world, x + chunk.x() * 16, y, z + chunk.z() * 16, yaw, pitch).subtract(offset.x, offset.y, offset.z);
            blockModifier.modifyEntity(loc);
            entity.spawnAt(loc);
        }
    }

    private static void pasteSection(PolarSection polarSection, World world, BlockModifier blockModifier, Vector3i offset, IgnoreAir ignoreAir) {
        // Blocks
        int[] blockData = polarSection.blockData();

        String[] rawBlockPalette = polarSection.blockPalette();
        BlockState[] materialPalette = new BlockState[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                materialPalette[i] = ((CraftBlockData) Bukkit.getServer().createBlockData(rawBlockPalette[i])).getState();
            } catch (IllegalArgumentException e) {
                PolarPaper.logger().warning("Failed to parse block state: " + rawBlockPalette[i]);
                materialPalette[i] = Blocks.AIR.defaultBlockState();
            }
        }

        if (rawBlockPalette.length <= 1) {
            BlockState blockState = materialPalette[0];
            if (blockState.isAir() && (ignoreAir == IgnoreAir.ALL || ignoreAir == IgnoreAir.EMPTY_SECTION)) return;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Vector3i blockPos = new Vector3i(x, y, z);
                        blockPos.add(offset);
                        BlockState newBlockState = blockModifier.modify(blockPos, blockState);

                        BlockUtil.setBlockFast(world, blockPos.x, blockPos.y, blockPos.z, newBlockState);
                    }
                }
            }
        } else {
            int blockIndex = 0;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState blockState = materialPalette[blockData[blockIndex++]];
                        if (ignoreAir == IgnoreAir.ALL && blockState.isAir()) continue;

                        Vector3i blockPos = new Vector3i(x, y, z);
                        blockPos.add(offset);
                        BlockState newBlockState = blockModifier.modify(blockPos, blockState);

                        BlockUtil.setBlockFast(world, blockPos.x, blockPos.y, blockPos.z, newBlockState);
                    }
                }
            }
        }
    }

    public enum IgnoreAir {
        /**
         * Ignore all air blocks
         */
        ALL,
        /**
         * Only ignore empty sections (default)
         */
        EMPTY_SECTION,
        /**
         * Do not ignore air
         */
        NONE
    }

}
