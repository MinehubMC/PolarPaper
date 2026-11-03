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

    /**
     * Whether the load events were fired and the unload events were not fired since, only accessed on the chunk's thread
     */
    private boolean loadEventsFired;

    public NoUnloadLevelChunk(Level level, ChunkPos pos) {
        super(level, pos);
    }

    public NoUnloadLevelChunk(Level level, ChunkPos pos, UpgradeData data, LevelChunkTicks<Block> blockTicks, LevelChunkTicks<Fluid> fluidTicks, long inhabitedTime, LevelChunkSection @Nullable [] sections, @Nullable PostLoadProcessor postLoad, @Nullable BlendingData blendingData) {
        super(level, pos, data, blockTicks, fluidTicks, inhabitedTime, sections, postLoad, blendingData);
    }

    public NoUnloadLevelChunk(ServerLevel level, ProtoChunk chunk, @Nullable PostLoadProcessor postLoad) {
        super(level, chunk, postLoad);
    }

    /**
     * Fires ChunkLoadEvent and EntitiesLoadEvent like for any other chunk, called by the chunk system when the chunk
     * becomes accessible
     */
    @Override
    public void loadCallback() {
        if (this.loadEventsFired) return;
        this.loadEventsFired = true;
        super.loadCallback();
    }

    /**
     * Fires EntitiesUnloadEvent and ChunkUnloadEvent like for any other chunk. The chunk is never saved by the chunk
     * system, whatever the event's save flag is
     */
    @Override
    public void unloadCallback() {
        if (!this.loadEventsFired) return;
        this.loadEventsFired = false;
        boolean mustNotSave = this.mustNotSave;
        super.unloadCallback();
        this.mustNotSave |= mustNotSave;
    }
}
