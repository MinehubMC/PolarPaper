package dev.emortal.paperpolar;

import com.google.common.io.ByteArrayDataOutput;
import org.bukkit.ChunkSnapshot;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides access to user world data for the polar loader to get and set user
 * specific world data such as objects, as well as provides some relevant callbacks.
 * <br/><br/>
 * Usage if world access is completely optional, dependent features will not add
 * overhead to the format if unused.
 */
@SuppressWarnings("UnstableApiUsage")
public interface PolarWorldAccess {
    PolarWorldAccess DEFAULT = new PolarWorldAccess() {
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
     * Called when a chunk is being saved.
     * <br/><br/>
     * Can be used to save user data in the chunk by writing it to the buffer.
     *
     * @param chunk The chunk being saved
     * @param userData A buffer to write user data to save
     */
    default void saveChunkData(@NotNull ChunkSnapshot chunk, @NotNull ByteArrayDataOutput userData) {
    }

    @ApiStatus.Experimental
    default void loadHeightmaps(@NotNull ChunkGenerator.ChunkData chunkData, int[][] heightmaps) {
    }

    @ApiStatus.Experimental
    default void saveHeightmaps(@NotNull ChunkSnapshot chunk, int[][] heightmaps) {
    }

}