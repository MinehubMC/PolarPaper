package dev.emortal.paperpolar;

import dev.emortal.paperpolar.util.CoordConversion;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public class PolarBiomeProvider extends BiomeProvider {

    private static final Logger LOGGER = Logger.getLogger(PolarBiomeProvider.class.getName());

    private static final int CHUNK_SECTION_SIZE = 16;

    private final @NotNull PolarWorld polarWorld;
    public PolarBiomeProvider(@NotNull PolarWorld polarWorld) {
        this.polarWorld = polarWorld;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        int chunkX = CoordConversion.globalToChunk(x);
        int chunkZ = CoordConversion.globalToChunk(z);

        PolarChunk chunk = polarWorld.chunkAt(chunkX, chunkZ); // TODO: double check thread safety
        if (chunk == null) return Biome.PLAINS;

        int sectionIndex = ((int)Math.floor((double)y / CHUNK_SECTION_SIZE)) + 4; // -4 = min section

        PolarSection section = chunk.sections()[sectionIndex];

        // Biomes
        String[] rawBiomePalette = section.biomePalette();

        if (rawBiomePalette.length == 1) {
            return parseBiome(rawBiomePalette[0]);
        }

        int localX = CoordConversion.globalToSectionRelative(x);
        int localY = CoordConversion.globalToSectionRelative(y);
        int localZ = CoordConversion.globalToSectionRelative(z);

        int index = localX / 4 + (localZ / 4) * 4 + (localY / 4) * 16;
        int[] biomeDataArray = section.biomeData();

        int biomeData = biomeDataArray[index];
        return parseBiome(rawBiomePalette[biomeData]);
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        return registry.stream().toList();
    }

    private Biome parseBiome(String s) {
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        try {
            return registry.get(Key.key(s));
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Failed to parse biome " + s);
            return Biome.PLAINS;
        }
    }
}
