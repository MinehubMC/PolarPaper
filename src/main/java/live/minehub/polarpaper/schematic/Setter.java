package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.*;
import live.minehub.polarpaper.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.joml.Vector3i;

import java.util.Set;

public interface Setter {
    void setBlock(int x, int y, int z, BlockState newBlockState);

    void setBlockEntity(int x, int y, int z, PolarChunk.BlockEntity blockEntity);

    void spawnEntity(PolarEntity polarEntity, Location spawnLocation);

    default boolean shouldPaste(PolarChunk polarChunk, PolarSection section, int sectionY, Vector3i cornerPos) {
        return true;
    }

    class World implements Setter {
        private final org.bukkit.World world;
        private final BlockSelector selector;

        public World(org.bukkit.World world) {
            this.world = world;
            this.selector = BlockSelector.ALL;
        }

        public World(org.bukkit.World world, BlockSelector selector) {
            this.world = world;
            this.selector = selector;
        }

        public org.bukkit.World getWorld() {
            return world;
        }

        public BlockSelector getBlockSelector() {
            return selector;
        }

        @Override
        public boolean shouldPaste(PolarChunk polarChunk, PolarSection section, int sectionY, Vector3i cornerPos) {
            return selector.testChunk(polarChunk.x(), polarChunk.z());
        }

        @Override
        public void setBlock(int x, int y, int z, BlockState newBlockState) {
            if (!selector.test(x, y, z)) return;

            BlockUtil.setBlockFast(world, x, y, z, newBlockState);
        }

        @Override
        public void setBlockEntity(int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
            if (!selector.test(x, y, z)) return;

            BlockUtil.setBlockEntity(world, x, y, z, blockEntity);
        }

        @Override
        public void spawnEntity(PolarEntity polarEntity, Location spawnLocation) {
            if (!selector.test(spawnLocation.blockX(), spawnLocation.blockY(), spawnLocation.blockZ())) return;

            net.minecraft.world.entity.Entity nmsEntity = polarEntity.toNMSEntity(world, spawnLocation, true);
            if (nmsEntity == null) return;

            CraftEntity entity = nmsEntity.getBukkitEntity();

            Bukkit.getGlobalRegionScheduler().run(PolarPaper.getPlugin(), _ -> {
                PolarEntitySpawnEvent event = new PolarEntitySpawnEvent(polarEntity, entity, spawnLocation, true);
                event.callEvent();
                if (!event.isCancelled()) {
                    EntityUtil.spawnEntity(entity, world);
                }
            });
        }

        public void refreshChunks(Set<ChunkPos> chunksToRefresh) {
            CraftWorld craftWorld = (CraftWorld) world;
            ServerLevel serverLevel = craftWorld.getHandle();

            // relight chunks and resend blocks to client
            serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunksToRefresh, _ -> {}, _ -> {});
            for (ChunkPos c : chunksToRefresh) {
                world.refreshChunk(c.x(), c.z());
            }
        }
    }

}
