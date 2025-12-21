package live.minehub.polarpaper.util;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import live.minehub.polarpaper.PolarChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
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

    public static void setBlockEntity(World world, PolarChunk.BlockEntity blockEntity, Vector3i blockOffset) {
        if (blockEntity.data() == null) return;

        int x = blockOffset.x;
        int y = blockOffset.y;
        int z = blockOffset.z;

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

}
