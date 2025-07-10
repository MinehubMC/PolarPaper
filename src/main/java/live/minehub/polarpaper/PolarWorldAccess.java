package live.minehub.polarpaper;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import live.minehub.polarpaper.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static live.minehub.polarpaper.util.ByteArrayUtil.writeByteArray;
import static live.minehub.polarpaper.util.ByteArrayUtil.writeVarInt;

/**
 * Provides access to user world data for the polar loader to get and set user
 * specific world data such as objects, as well as provides some relevant callbacks.
 * <br/><br/>
 * Usage if world access is completely optional, dependent features will not add
 * overhead to the format if unused.
 */
public interface PolarWorldAccess {
    PolarWorldAccess NOOP = new PolarWorldAccess() {
    };

    /**
     * Provides features implemented by polar paper specifically not available in the standard polar format. Currently
     * just entities.
     */
    PolarWorldAccess POLAR_PAPER_FEATURES = new PolarWorldAccess() {
        // Current version of the features chunk data
        private static final byte CURRENT_FEATURES_VERSION = 1;

        @Override
        public void populateChunkData(@NotNull final Chunk chunk, final byte @Nullable [] userData) {
            List<PolarChunk.Entity> entities = EntityUtil.getEntities(userData);

            for (PolarChunk.Entity polarEntity : entities) {
                final var x = polarEntity.x();
                final var y = polarEntity.y();
                final var z = polarEntity.z();
                final var yaw = polarEntity.yaw();
                final var pitch = polarEntity.pitch();
                final var bytes = polarEntity.bytes();

                Entity entity;
                try {
                    entity = EntityUtil.bytesToEntity(chunk.getWorld(), bytes);
                    if (entity == null) continue;
                } catch (Exception e) {
                    continue;
                }
                entity.spawnAt(new Location(chunk.getWorld(), x + chunk.getX() * 16, y, z + chunk.getZ() * 16, yaw, pitch));
            }
        }

        @Override
        public void saveChunkData(@NotNull ChunkSnapshot chunk,
                                  @NotNull Set<Map.Entry<BlockPos, BlockEntity>> blockEntities,
                                  @NotNull Entity[] entities, @NotNull ByteArrayDataOutput userData) {
            userData.writeByte(CURRENT_FEATURES_VERSION);

            ByteArrayDataOutput entityData = ByteStreams.newDataOutput();
            int entityCount = 0;
            for (@NotNull Entity entity : entities) {
                byte[] entityBytes = EntityUtil.entityToBytes(entity);
                if (entityBytes == null) continue;
                Location entityPos = entity.getLocation();

                final var x = ((entityPos.x() % 16) + 16) % 16;
                final var z = ((entityPos.z() % 16) + 16) % 16;

                entityData.writeDouble(x);
                entityData.writeDouble(entityPos.y());
                entityData.writeDouble(z);
                entityData.writeFloat(entityPos.getYaw());
                entityData.writeFloat(entityPos.getPitch());
                writeByteArray(entityBytes, entityData);

                entityCount++;
            }
            writeVarInt(entityCount, userData);
            userData.write(entityData.toByteArray());
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
    default void saveChunkData(@NotNull ChunkSnapshot chunk,
                               @NotNull Set<Map.Entry<BlockPos, BlockEntity>> blockEntities, @NotNull Entity[] entities,
                               @NotNull ByteArrayDataOutput userData) {
    }

    @ApiStatus.Experimental
    default void loadHeightmaps(@NotNull ChunkGenerator.ChunkData chunkData, int[][] heightmaps) {
    }

    @ApiStatus.Experimental
    default void saveHeightmaps(@NotNull ChunkSnapshot chunk, int[][] heightmaps) {
    }

}