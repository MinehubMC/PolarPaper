package live.minehub.polarpaper.util;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import live.minehub.polarpaper.PolarChunk;
import live.minehub.polarpaper.schematic.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3i;

public class BlockUtil {

    public static void setBlockFast(World world, int x, int y, int z, BlockState blockState) {
        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        int chunkX = (int)Math.floor(x / 16.0);
        int chunkZ = (int)Math.floor(z / 16.0);
        int section = (int)Math.floor(y / 16.0);

        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return;

        int sectionI = section - chunkAccess.getMinSectionY();
        if (sectionI >= chunkAccess.getSections().length) return;
        if (sectionI < 0) return;

        LevelChunkSection levelChunkSection = chunkAccess.getSection(sectionI);
        int newBlockX = x % 16;
        if (newBlockX < 0) newBlockX = 16 + newBlockX;
        int newBlockY = y % 16;
        if (newBlockY < 0) newBlockY = 16 + newBlockY;
        int newBlockZ = z % 16;
        if (newBlockZ < 0) newBlockZ = 16 + newBlockZ;

        levelChunkSection.setBlockState(newBlockX, newBlockY, newBlockZ, blockState);
    }

    public static void setBlockEntity(World world, int x, int y, int z, PolarChunk.BlockEntity blockEntity) {
        if (blockEntity.data() == null) return;

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();
        ChunkHolderManager chunkHolderManager = serverLevel.moonrise$getChunkTaskScheduler().chunkHolderManager;

        int chunkX = (int)Math.floor(x / 16.0);
        int chunkZ = (int)Math.floor(z / 16.0);

        NewChunkHolder chunkHolder = chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (chunkHolder == null) return;
        ChunkAccess chunkAccess = chunkHolder.getCurrentChunk();
        if (chunkAccess == null) return;

        blockEntity.data().putInt("x", x);
        blockEntity.data().putInt("y", y);
        blockEntity.data().putInt("z", z);

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
        BlockEntity nmsBlockEntity = BlockEntity.loadStatic(new BlockPos(x, y, z), chunkAccess.getBlockState(x, y, z), blockEntity.data(), registryAccess);
        if (nmsBlockEntity == null) return;
        serverLevel.getChunk(chunkX, chunkZ).addAndRegisterBlockEntity(nmsBlockEntity);
    }

    public static void rotateLoc(@NotNull Location loc, @NotNull Rotation rotation) {
        Vector3d vec = new Vector3d(loc.x(), loc.y(), loc.z());
        rotatePos(vec, rotation);
        loc.set(vec.x, vec.y, vec.z);
        loc.setYaw(loc.getYaw() + rotation.toDegrees());
    }

    public static void rotatePos(@NotNull Vector3i point, @NotNull Rotation rotation) {
        Vector3d vec = new Vector3d(point);
        rotatePos(vec, rotation);
        point.x = (int) vec.x;
        point.y = (int) vec.y;
        point.z = (int) vec.z;
    }

    public static void rotatePos(@NotNull Vector3d point, @NotNull Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90 -> {
                double x = -point.z;
                double z = point.x;
                point.x = x;
                point.z = z;
            }
            case CLOCKWISE_180 -> {
                double x = -point.x;
                double z = -point.z;
                point.x = x;
                point.z = z;
            }
            case CLOCKWISE_270 -> {
                double x = point.z;
                double z = -point.x;
                point.x = x;
                point.z = z;
            }
        }
    }

}
