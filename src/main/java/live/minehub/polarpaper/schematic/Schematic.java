package live.minehub.polarpaper.schematic;

import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.userdata.WorldUserData;
import live.minehub.polarpaper.core.util.CoordConversion;
import live.minehub.polarpaper.core.util.PaletteUtil;
import live.minehub.polarpaper.core.world.PolarChunk;
import live.minehub.polarpaper.core.world.PolarEntity;
import live.minehub.polarpaper.core.world.PolarSection;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.util.*;

public class Schematic {

    public static final NamespacedKey POS_1_KEY = new NamespacedKey("polarpaper", "pos1");
    public static final NamespacedKey POS_2_KEY = new NamespacedKey("polarpaper", "pos2");

    public static void paste(PolarWorld polarWorld, Setter setter, Vector3i pasteOffset, Rotation rotation, IgnoreAir ignoreAir) {
        byte[] userData = polarWorld.userData();
        Vector3i offset = WorldUserData.readSchematicOffset(userData);
        if (offset == null) offset = new Vector3i();

        Map<Vector3i, PolarChunk.BlockEntity> blockEntityMap = new HashMap<>();

        for (PolarChunk chunk : polarWorld.chunks()) {
            int i = 0;
            for (PolarSection section : chunk.sections()) {
                Vector3i blockOffset = new Vector3i(chunk.x() * 16, (i + polarWorld.minSection()) * 16, chunk.z() * 16)
                        .sub(offset);
                boolean shouldPaste = setter.shouldPaste(chunk, section, i + polarWorld.minSection(), blockOffset);
                i++;
                if (!shouldPaste) continue;

                pasteSection(section, setter, blockOffset, pasteOffset, rotation, ignoreAir);
            }

            handleUserData(setter, pasteOffset, rotation, chunk, offset);

            for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
                int x = CoordConversion.chunkBlockIndexGetX(blockEntity.index());
                int y = CoordConversion.chunkBlockIndexGetY(blockEntity.index());
                int z = CoordConversion.chunkBlockIndexGetZ(blockEntity.index());

                Vector3i blockOffset = new Vector3i(chunk.x() * 16, 0, chunk.z() * 16).sub(offset).add(x, y, z);
                BlockUtil.rotatePos(blockOffset, rotation);
                blockOffset.add(pasteOffset);

                blockEntityMap.put(blockOffset, blockEntity);
            }
        }

        Vector3i finalOffset = offset;
        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            for (Map.Entry<Vector3i, live.minehub.polarpaper.core.world.PolarChunk.BlockEntity> entry : blockEntityMap.entrySet()) {
                Vector3i blockOffset = entry.getKey();
                live.minehub.polarpaper.core.world.PolarChunk.BlockEntity blockEntity = entry.getValue();
                setter.setBlockEntity(blockOffset.x, blockOffset.y, blockOffset.z, blockEntity);
            }

            Set<Vector2i> chunksToRefresh = new HashSet<>();
            for (live.minehub.polarpaper.core.world.PolarChunk chunk : polarWorld.chunks()) {
                Vector3i chunkOffset = new Vector3i(chunk.x() * 16, 0, chunk.z() * 16)
                        .sub(finalOffset);
                BlockUtil.rotatePos(chunkOffset, rotation);
                chunkOffset.add(pasteOffset.x, 0, pasteOffset.z);

                int cX = (int)Math.floor(chunkOffset.x / 16.0);
                int cZ = (int)Math.floor(chunkOffset.z / 16.0);

                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        chunksToRefresh.add(new Vector2i(cX + x, cZ + z));
                    }
                }
            }

            if (setter instanceof Setter.World worldSetter) worldSetter.refreshChunks(chunksToRefresh);
        });
    }

    private static void handleUserData(Setter setter, Vector3i pasteOffset, Rotation rotation, live.minehub.polarpaper.core.world.PolarChunk chunk, Vector3i offset) {
        if (chunk.userData() == null || chunk.userData().length == 0) return;

        final var bb = Unpooled.wrappedBuffer(chunk.userData());
        byte version = bb.readByte();
        List<PolarEntity> entities = EntityUtil.getEntities(bb);

        for (PolarEntity polarEntity : entities) {
            Location spawnLocation = polarEntity.getLocation(null, chunk.x(), chunk.z());
            spawnLocation.subtract(offset.x, offset.y, offset.z);
            BlockUtil.rotateLoc(spawnLocation, rotation);
            spawnLocation.add(pasteOffset.x, pasteOffset.y, pasteOffset.z);

            switch (rotation) {
                case CLOCKWISE_90 -> spawnLocation.add(1, 0, 0);
                case CLOCKWISE_180 -> spawnLocation.add(1, 0, 1);
                case CLOCKWISE_270 -> spawnLocation.add(0, 0, 1);
            }

            setter.spawnEntity(polarEntity, spawnLocation);
        }
    }

    private static void pasteSection(live.minehub.polarpaper.core.world.PolarSection polarSection, Setter setter, Vector3i offset, Vector3i pasteOffset, Rotation rotation, IgnoreAir ignoreAir) {
        // Blocks
        long[] blockDataLongs = polarSection.blockData();
        int blockDataBits = blockDataLongs == null ? 0 : PaletteUtil.getBitsForLongLength(blockDataLongs.length, live.minehub.polarpaper.core.world.PolarSection.BLOCK_PALETTE_SIZE);
        int[] blockData = new int[live.minehub.polarpaper.core.world.PolarSection.BLOCK_PALETTE_SIZE];
        if (blockDataBits > 0) {
            PaletteUtil.unpack(blockData, blockDataLongs, blockDataBits);
        }

        String[] rawBlockPalette = polarSection.blockPalette();
        BlockState[] materialPalette = new BlockState[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                materialPalette[i] = ((CraftBlockData) Bukkit.getServer().createBlockData(rawBlockPalette[i])).getState();
            } catch (IllegalArgumentException _) {
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
                        blockState = blockState.rotate(rotation.getMcRot());
                        BlockUtil.rotatePos(blockPos, rotation);
                        blockPos.add(pasteOffset);

                        setter.setBlock(blockPos.x, blockPos.y, blockPos.z, blockState);
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
                        blockState = blockState.rotate(rotation.getMcRot());
                        BlockUtil.rotatePos(blockPos, rotation);
                        blockPos.add(pasteOffset);

                        setter.setBlock(blockPos.x, blockPos.y, blockPos.z, blockState);
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
