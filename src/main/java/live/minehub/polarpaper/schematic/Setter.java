package live.minehub.polarpaper.schematic;

import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.world.PolarEntity;
import live.minehub.polarpaper.nms.VersionUtil;
import live.minehub.polarpaper.util.BlockUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface Setter {
    void setBlock(int x, int y, int z, BlockState newBlockState);

    void setBlockEntity(int x, int y, int z, live.minehub.polarpaper.core.world.PolarChunk.BlockEntity blockEntity);

    void spawnEntity(PolarEntity polarEntity, Location spawnLocation);

    default boolean shouldPaste(live.minehub.polarpaper.core.world.PolarChunk polarChunk, live.minehub.polarpaper.core.world.PolarSection section, int sectionY, Vector3i cornerPos) {
        return true;
    }

    class World implements Setter {
        private final org.bukkit.World world;
        private final live.minehub.polarpaper.core.world.BlockSelector selector;

        public World(org.bukkit.World world) {
            this.world = world;
            this.selector = live.minehub.polarpaper.core.world.BlockSelector.ALL;
        }

        public World(org.bukkit.World world, live.minehub.polarpaper.core.world.BlockSelector selector) {
            this.world = world;
            this.selector = selector;
        }

        public org.bukkit.World getWorld() {
            return world;
        }

        public live.minehub.polarpaper.core.world.BlockSelector getBlockSelector() {
            return selector;
        }

        @Override
        public boolean shouldPaste(live.minehub.polarpaper.core.world.PolarChunk polarChunk, live.minehub.polarpaper.core.world.PolarSection section, int sectionY, Vector3i cornerPos) {
            return selector.testChunk(polarChunk.x(), polarChunk.z());
        }

        @Override
        public void setBlock(int x, int y, int z, BlockState newBlockState) {
            if (!selector.test(x, y, z)) return;

            BlockUtil.setBlockFast(world, x, y, z, newBlockState);
        }

        @Override
        public void setBlockEntity(int x, int y, int z, live.minehub.polarpaper.core.world.PolarChunk.BlockEntity blockEntity) {
            if (!selector.test(x, y, z)) return;

            BlockUtil.setBlockEntity(world, x, y, z, blockEntity);
        }

        @Override
        public void spawnEntity(PolarEntity polarEntity, Location spawnLocation) {
            if (!selector.test(spawnLocation.blockX(), spawnLocation.blockY(), spawnLocation.blockZ())) return;

            spawnLocation.setWorld(world);

            EntitySerializer entitySerializer = VersionUtil.getEntitySerializer();
            net.minecraft.world.entity.Entity nmsEntity = polarEntity.toNMSEntity(entitySerializer, world, spawnLocation);
            if (nmsEntity == null) return;

            CraftEntity entity = nmsEntity.getBukkitEntity();

            Bukkit.getRegionScheduler().run(PolarPaper.getPlugin(), spawnLocation, _ -> {
                PolarEntitySpawnEvent event = new PolarEntitySpawnEvent(polarEntity, entity, spawnLocation, true);
                event.callEvent();
                if (!event.isCancelled()) {
                    EntityUtil.spawnEntity(entity, world);
                }
            });
        }

        public void refreshChunks(Set<Vector2i> chunksToRefresh) {
            CraftWorld craftWorld = (CraftWorld) world;
            ServerLevel serverLevel = craftWorld.getHandle();

            // relight chunks and resend blocks to client
            serverLevel.getChunkSource().getLightEngine().starlight$serverRelightChunks(vecsToChunkPos(chunksToRefresh), _ -> {}, _ -> {});
            for (Vector2i c : chunksToRefresh) {
                Bukkit.getRegionScheduler().execute(PolarPaper.getPlugin(), world, c.x(), c.y(), () -> {
                    world.refreshChunk(c.x(), c.y());
                });
            }
        }

        private List<ChunkPos> vecsToChunkPos(Set<Vector2i> vecs) {
            List<ChunkPos> chunkPos = new ArrayList<>(vecs.size());
            for (Vector2i vec : vecs) {
                chunkPos.add(new ChunkPos(vec.x(), vec.y()));
            }
            return chunkPos;
        }
    }

}
