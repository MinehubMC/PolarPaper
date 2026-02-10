package live.minehub.polarpaper;

import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;

public class ImposterNoBiomeChunk extends ImposterProtoChunk {

    public ImposterNoBiomeChunk(LevelChunk wrapped, boolean allowWrites) {
        super(wrapped, allowWrites);
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler) {
//        System.out.println("no biomes");
    }
}
