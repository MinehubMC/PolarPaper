package live.minehub.polarpaper.core.world;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Provides access to user world data for the polar loader to get and set user
 * specific world data such as objects, as well as provides some relevant callbacks.
 * <br/><br/>
 * Usage if world access is completely optional, dependent features will not add
 * overhead to the format if unused.
 */
@SuppressWarnings("unused")
public interface PolarWorldAccess {
    Plugin getPlugin();

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
     * @param userData The saved user data, or null if none is present
     */
    default void loadChunkData(@NotNull World world, @NotNull ChunkAccess chunk, byte @Nullable [] userData) {
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
                               @NotNull Map<BlockPos, BlockEntity> blockEntities, @NotNull Entity[] entities,
                               @NotNull ByteBuf userData) {
    }

    @ApiStatus.Experimental
    default void loadHeightmaps(@NotNull ChunkGenerator.ChunkData chunkData, int[][] heightmaps) {
    }

    @ApiStatus.Experimental
    default void saveHeightmaps(@NotNull ChunkAccess chunk, int[][] heightmaps) {
    }

}