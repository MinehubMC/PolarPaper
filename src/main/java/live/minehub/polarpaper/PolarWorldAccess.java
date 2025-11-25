package live.minehub.polarpaper;

import com.google.common.io.ByteArrayDataOutput;
import live.minehub.polarpaper.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.userdata.EntityUtil;
import live.minehub.polarpaper.util.ByteArrayUtil;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.craftbukkit.persistence.DirtyCraftPersistentDataContainer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Painting;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to user world data for the polar loader to get and set user
 * specific world data such as objects, as well as provides some relevant callbacks.
 * <br/><br/>
 * Usage if world access is completely optional, dependent features will not add
 * overhead to the format if unused.
 */
@SuppressWarnings("unused")
public interface PolarWorldAccess {
    PolarWorldAccess NOOP = new PolarWorldAccess() {
    };

    /**
     * Provides features implemented by polar paper specifically not available in the standard polar format. Currently
     * entities and the chunk persistent data container.
     */
    PolarWorldAccess POLAR_PAPER_FEATURES = new PolarWorldAccess() {
        // Current version of the features chunk data
        private static final byte CURRENT_FEATURES_VERSION = 2;
        private static final byte ENTITIES_VERSION = 1;
        private static final byte PERSISTENT_DATA_CONTAINER_VERSION = 2;

        @Override
        public void populateChunkData(@NotNull final Chunk chunk, final byte @Nullable [] userData) {
            if (userData == null) return;

            final var bb = ByteBuffer.wrap(userData);

            byte version = bb.get();

            List<PolarChunk.Entity> entities = EntityUtil.getEntities(bb);

            for (PolarChunk.Entity polarEntity : entities) {
                var x = polarEntity.x();
                var y = polarEntity.y();
                var z = polarEntity.z();
                var yaw = polarEntity.yaw();
                var pitch = polarEntity.pitch();
                var bytes = polarEntity.bytes();

                // fix for previous version and also sanity check :)
                if (x < 0) x += 16;
                if (z < 0) z += 16;

                Entity entity;
                try {
                    entity = EntityUtil.bytesToEntity(chunk.getWorld(), bytes);
                    if (entity == null) continue;
                } catch (Exception e) {
                    continue;
                }

                if (entity instanceof Painting painting) {
                    if (painting.getArt().getBlockHeight() % 2 == 0) { // strange spigot bug
                        y--;
                    }
                }

                Location spawnLocation = new Location(chunk.getWorld(), x + chunk.getX() * 16, y, z + chunk.getZ() * 16, yaw, pitch);
                PolarEntitySpawnEvent event = new PolarEntitySpawnEvent(polarEntity, entity, spawnLocation, false);
                event.callEvent();
                if (!event.isCancelled()) {
                    entity.spawnAt(event.getSpawnLocation());
                }
            }

            if (version >= PERSISTENT_DATA_CONTAINER_VERSION) {
                PersistentDataContainer persistentDataContainer = chunk.getPersistentDataContainer();
                try {
                    byte[] bytes = ByteArrayUtil.getByteArray(bb);
                    persistentDataContainer.readFromBytes(bytes);
                } catch (IOException e) {
                    PolarPaper.logger().warning("Failed to deserialize persistent data container");
                    ExceptionUtil.log(e);
                }
            }
        }

        @Override
        public void saveChunkData(@NotNull ChunkAccess chunk,
                                  @NotNull Set<Map.Entry<BlockPos, BlockEntity>> blockEntities,
                                  @NotNull Entity[] entities, @NotNull ByteArrayDataOutput userData) {
            List<PolarChunk.Entity> polarEntities = new ArrayList<>();

            for (@NotNull Entity entity : entities) {
                if (entity.getType() == EntityType.PLAYER) continue;
                byte[] entityBytes = EntityUtil.entityToBytes(entity);
                if (entityBytes == null) continue;
                Location entityPos = entity.getLocation();

                final var x = ((entityPos.x() % 16) + 16) % 16;
                final var z = ((entityPos.z() % 16) + 16) % 16;

                polarEntities.add(new PolarChunk.Entity(
                        x,
                        entityPos.y(),
                        z,
                        entityPos.getYaw(),
                        entityPos.getPitch(),
                        entityBytes
                ));
            }

            userData.writeByte(CURRENT_FEATURES_VERSION);
            EntityUtil.writeEntities(polarEntities, userData);

            DirtyCraftPersistentDataContainer persistentDataContainer = chunk.persistentDataContainer;
            try {
                byte[] bytes = persistentDataContainer.serializeToBytes();
                ByteArrayUtil.writeByteArray(bytes, userData);
            } catch (IOException e) {
                PolarPaper.logger().warning("Failed to deserialize persistent data container");
                ExceptionUtil.log(e);
            }
        }

    };

    // TODO: these
//    /**
//     * Called when an instance is created from this chunk loader.
//     * <br/><br/>
//     * Can be used to initialize the world based on saved user data in the world.
//     *
//     * @param instance The Minestom instance being created
//     * @param userData The saved user data, or null if none is present.
//     */
//    default void loadWorldData(@NotNull Instance instance, @Nullable NetworkBuffer userData) {
//    }
//
//    /**
//     * Called when an instance is being saved.
//     * <br/><br/>
//     * Can be used to save user data in the world by writing it to the buffer.
//     *
//     * @param instance The Minestom instance being saved
//     * @param userData A buffer to write user data to save
//     */
//    default void saveWorldData(@NotNull Instance instance, @NotNull NetworkBuffer userData) {
//    }

    /**
     * Called when a chunk is created, just before it is added to the world.
     * <br/><br/>
     * Can be used to initialize the chunk based on saved user data in the world.
     *
     * @param chunkData The ChunkData being created
     * @param userData The saved user data, or null if none is present
     */
    default void loadChunkData(@NotNull ChunkGenerator.ChunkData chunkData, byte @Nullable [] userData) {
    }

    /**
     * Called when a chunk is being populated, after it's been added to the world.
     * <br/><br/>
     * Can be used to access user data after the chunk has been loaded.
     *
     * @param chunk The Bukkit chunk being populated
     * @param userData The saved user data, or null if none is present
     */
    default void populateChunkData(@NotNull Chunk chunk, byte @Nullable [] userData) {
    }

    /**
     * Called when a chunk is being saved.
     * <br/><br/>
     * Can be used to save user data in the chunk by writing it to the buffer.
     *
     * @param chunk The chunk being saved
     * @param blockEntities Block entities in the chunk being saved
     * @param entities Entities in the chunk being saved
     * @param userData A buffer to write user data to save
     */
    default void saveChunkData(@NotNull ChunkAccess chunk,
                               @NotNull Set<Map.Entry<BlockPos, BlockEntity>> blockEntities, @NotNull Entity[] entities,
                               @NotNull ByteArrayDataOutput userData) {
    }

    @ApiStatus.Experimental
    default void loadHeightmaps(@NotNull ChunkGenerator.ChunkData chunkData, int[][] heightmaps) {
    }

    @ApiStatus.Experimental
    default void saveHeightmaps(@NotNull ChunkAccess chunk, int[][] heightmaps) {
    }

}