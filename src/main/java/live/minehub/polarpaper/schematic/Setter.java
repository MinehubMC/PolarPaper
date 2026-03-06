package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.PolarChunk;
import live.minehub.polarpaper.PolarEntity;
import live.minehub.polarpaper.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;

import java.util.Set;

public interface Setter {
    void setBlock(int x, int y, int z, BlockState newBlockState);

    void setBlockEntity(int x, int y, int z, PolarChunk.BlockEntity blockEntity);

    void spawnEntity(PolarEntity polarEntity, Location spawnLocation);

    class World implements Setter {
        private final org.bukkit.World world;

        public World(org.bukkit.World world) {
            this.world = world;
        }

        @Override
        public void setBlock(int x, int y, int z, BlockState newBlockState) {
            BlockUtil.setBlockFast(world, x, y, z, newBlockState);
        }

        @Override
        public void setBlockEntity(int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
            BlockUtil.setBlockEntity(world, x, y, z, blockEntity);
        }

        @Override
        public void spawnEntity(PolarEntity polarEntity, Location spawnLocation) {
            Entity entity = polarEntity.toBukkitEntity(world, spawnLocation, true);
            if (entity == null) return;

            PolarEntitySpawnEvent event = new PolarEntitySpawnEvent(polarEntity, entity, spawnLocation, true);
            event.callEvent();
            if (!event.isCancelled()) {
                EntityUtil.spawnEntity(entity, world);
            }
        }

        public void refreshChunks(Set<ChunkPos> chunksToRefresh) {
            CraftWorld craftWorld = (CraftWorld) world;
            ServerLevel serverLevel = craftWorld.getHandle();

            // relight chunks and resend blocks to client
            serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunksToRefresh, a -> {}, a -> {});
            for (ChunkPos c : chunksToRefresh) {
                world.refreshChunk(c.x, c.z);
            }
        }
    }

}
