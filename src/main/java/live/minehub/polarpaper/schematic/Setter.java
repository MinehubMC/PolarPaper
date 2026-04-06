package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.PolarChunk;
import live.minehub.polarpaper.PolarEntity;
import live.minehub.polarpaper.PolarPaper;
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
            net.minecraft.world.entity.Entity nmsEntity = polarEntity.toNMSEntity(world, spawnLocation, true);
            if (nmsEntity == null) return;

            CraftEntity entity = nmsEntity.getBukkitEntity();

            Bukkit.getGlobalRegionScheduler().run(PolarPaper.getPlugin(), t -> {
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
            serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunksToRefresh, a -> {}, a -> {});
            for (ChunkPos c : chunksToRefresh) {
                world.refreshChunk(c.x(), c.z());
            }
        }
    }

}
