package live.minehub.polarpaper.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jspecify.annotations.Nullable;

public class NoUnloadLevelChunk extends LevelChunk {

    public NoUnloadLevelChunk(Level level, ChunkPos pos) {
        super(level, pos);
    }

    public NoUnloadLevelChunk(Level level, ChunkPos pos, UpgradeData data, LevelChunkTicks<Block> blockTicks, LevelChunkTicks<Fluid> fluidTicks, long inhabitedTime, LevelChunkSection @Nullable [] sections, @Nullable PostLoadProcessor postLoad, @Nullable BlendingData blendingData) {
        super(level, pos, data, blockTicks, fluidTicks, inhabitedTime, sections, postLoad, blendingData);
    }

    public NoUnloadLevelChunk(ServerLevel level, ProtoChunk chunk, @Nullable PostLoadProcessor postLoad) {
        super(level, chunk, postLoad);
    }

    @Override
    public void loadCallback() {

    }

    @Override
    public void unloadCallback() {

    }
}
