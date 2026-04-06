package live.minehub.polarpaper;

import live.minehub.polarpaper.util.PaletteUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Representation of the latest version of the section format.
 * <p>
 * Marked as internal because of the use of mutable arrays. These arrays must _not_ be mutated.
 * This class should be considered immutable.
 */
@ApiStatus.Internal
public class PolarSection {
    public static final int BLOCK_PALETTE_SIZE = 4096;
    public static final int BIOME_PALETTE_SIZE = 64;

    public enum LightContent {
        MISSING, EMPTY, FULL, PRESENT;

        public static final LightContent[] VALUES = values();
    }

    private final boolean empty;

    private final String @NotNull [] blockPalette;
    private final long @Nullable [] blockData;

    private final String @NotNull [] biomePalette;
    private final long @Nullable [] biomeData;

    private final LightContent blockLightContent;
    private final byte @Nullable [] blockLight;
    private final LightContent skyLightContent;
    private final byte @Nullable [] skyLight;

    public PolarSection() {
        this.empty = true;

        this.blockPalette = new String[]{"minecraft:air"};
        this.blockData = null;
        this.biomePalette = new String[]{"minecraft:plains"};
        this.biomeData = null;

        this.blockLightContent = LightContent.MISSING;
        this.blockLight = null;
        this.skyLightContent = LightContent.MISSING;
        this.skyLight = null;
    }

    public PolarSection(
            String @NotNull [] blockPalette, long @Nullable [] blockData,
            String @NotNull [] biomePalette, long @Nullable [] biomeData,
            @NotNull LightContent blockLightContent, byte @Nullable [] blockLight,
            @NotNull LightContent skyLightContent, byte @Nullable [] skyLight
    ) {
        this.empty = false;

        this.blockPalette = blockPalette;
        this.blockData = blockData;
        this.biomePalette = biomePalette;
        this.biomeData = biomeData;

        this.blockLightContent = blockLightContent;
        this.blockLight = blockLight;
        this.skyLightContent = skyLightContent;
        this.skyLight = skyLight;
    }

    public boolean isEmpty() {
        return empty;
    }

    public @NotNull String @NotNull [] blockPalette() {
        return blockPalette;
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 4096.
     */
    public long[] blockData() {
        assert blockData != null : "must check length of blockPalette() before using blockData()";
        return blockData;
    }

    public @NotNull String @NotNull [] biomePalette() {
        return biomePalette;
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 256.
     */
    public long[] biomeData() {
        assert biomeData != null : "must check length of biomePalette() before using biomeData()";
        return biomeData;
    }

    public @NotNull LightContent blockLightContent() {
        return blockLightContent;
    }

    public byte[] blockLight() {
        assert blockLight != null : "must check hasBlockLightData() before calling blockLight()";
        return blockLight;
    }

    public @NotNull LightContent skyLightContent() {
        return skyLightContent;
    }

    public byte[] skyLight() {
        assert skyLight != null : "must check hasSkyLightData() before calling skyLight()";
        return skyLight;
    }

    public LevelChunkSection createEmptyLevelChunkSection(RegistryAccess registryAccess) {
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, null);

        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, null);
        return new LevelChunkSection(states, biomes);
    }

    public LevelChunkSection createLevelChunkSection(RegistryAccess registryAccess) {
        if (empty) return createEmptyLevelChunkSection(registryAccess);

        // Blocks
        BlockState[] materialPalette = new BlockState[blockPalette.length];
        for (int i = 0; i < blockPalette.length; i++) {
            try {
                materialPalette[i] = ((CraftBlockData) Bukkit.getServer().createBlockData(blockPalette[i])).getState();
            } catch (IllegalArgumentException e) {
                PolarPaper.logger().warning("Failed to parse block state: " + blockPalette[i]);
                materialPalette[i] = Blocks.AIR.defaultBlockState();
            }
        }

        // Biomes
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        Holder<Biome>[] biomePalette = new Holder[biomePalette().length];
        for (int i = 0; i < biomePalette.length; i++) {
            Identifier identifier = Identifier.tryParse(biomePalette()[i]);
            if (identifier == null) {
                System.out.println("Failed to parse " + biomePalette[i]);
                biomePalette[i] = orThrow;
                continue;
            }
            Holder.Reference<Biome> biome = registry.get(identifier).orElse(null);
            if (biome == null) {
                System.out.println("Failed to get " + biomePalette[i]);
                biomePalette[i] = orThrow;
                continue;
            }
            biomePalette[i] = biome;
        }

        int bitsPerBlockEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
        int longBitsPerBlockEntry = bitsPerBlockEntry;
        if (blockData != null) {
            longBitsPerBlockEntry = PaletteUtil.getBitsForLongLength(blockData.length);
        }

        int bitsPerBiomeEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
        int longBitsPerBiomeEntry = bitsPerBiomeEntry;
        if (biomeData != null) {
            longBitsPerBiomeEntry = PaletteUtil.getBitsForLongLength(biomeData.length);
        }

        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, materialPalette);

        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, biomePalette);

        if (biomeData == null || bitsPerBiomeEntry == 0 || longBitsPerBiomeEntry == 0) {
            List<Holder<Biome>> biomesList = Arrays.asList(biomePalette);
            if (biomesList.size() > 1) {
                biomesList = List.of(biomePalette[0]);
            }
            biomes.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCountBiome(0),
                    new ZeroBitStorage(PolarSection.BIOME_PALETTE_SIZE),
                    PaletteUtil.createPalette(0, biomesList)
            );
        } else {
            if (bitsPerBiomeEntry > longBitsPerBiomeEntry) {
                int[] unpacked = new int[PolarSection.BIOME_PALETTE_SIZE];
                PaletteUtil.unpack(unpacked, blockData, longBitsPerBiomeEntry);
                long[] newLongs = PaletteUtil.pack(unpacked, bitsPerBiomeEntry);

                biomes.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBlock(bitsPerBiomeEntry),
                        new SimpleBitStorage(bitsPerBiomeEntry, PolarSection.BLOCK_PALETTE_SIZE, newLongs),
                        PaletteUtil.createPalette(bitsPerBiomeEntry, Arrays.asList(biomePalette))
                );
            } else {
                biomes.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBiome(bitsPerBiomeEntry),
                        new SimpleBitStorage(bitsPerBiomeEntry, PolarSection.BIOME_PALETTE_SIZE, biomeData),
                        PaletteUtil.createPalette(bitsPerBiomeEntry, Arrays.asList(biomePalette))
                );
            }
        }


        if (blockData == null || bitsPerBlockEntry == 0 || longBitsPerBlockEntry == 0) {
//            System.out.println(bitsPerBlockEntry + ", " + longBitsPerBlockEntry + ", " + (blockData == null ? 0 : blockData.length) + ", " + Arrays.toString(materialPalette));
            List<BlockState> materialsList = Arrays.asList(materialPalette);
            if (materialsList.size() > 1) {
                materialsList = List.of(materialPalette[0]);
            }
            states.data = new PalettedContainer.Data<>(
                    PaletteUtil.getConfigurationForBitCountBlock(0),
                    new ZeroBitStorage(PolarSection.BLOCK_PALETTE_SIZE),
                    PaletteUtil.createPalette(0, materialsList)
            );
        } else {
            if (bitsPerBlockEntry > longBitsPerBlockEntry) {
                int[] unpacked = new int[PolarSection.BLOCK_PALETTE_SIZE];
                PaletteUtil.unpack(unpacked, blockData, longBitsPerBlockEntry);
                long[] newLongs = PaletteUtil.pack(unpacked, bitsPerBlockEntry);

                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBlock(bitsPerBlockEntry),
                        new SimpleBitStorage(bitsPerBlockEntry, PolarSection.BLOCK_PALETTE_SIZE, newLongs),
                        PaletteUtil.createPalette(bitsPerBlockEntry, Arrays.asList(materialPalette))
                );
            } else if (4 > longBitsPerBlockEntry) {
                int[] unpacked = new int[PolarSection.BLOCK_PALETTE_SIZE];
                PaletteUtil.unpack(unpacked, blockData, bitsPerBlockEntry);
                long[] newLongs = PaletteUtil.pack(unpacked, 4);

                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBlock(bitsPerBlockEntry),
                        new SimpleBitStorage(4, PolarSection.BLOCK_PALETTE_SIZE, newLongs),
                        PaletteUtil.createPalette(bitsPerBlockEntry, Arrays.asList(materialPalette))
                );
            } else {
                states.data = new PalettedContainer.Data<>(
                        PaletteUtil.getConfigurationForBitCountBlock(longBitsPerBlockEntry),
                        new SimpleBitStorage(Math.max(4, longBitsPerBlockEntry), PolarSection.BLOCK_PALETTE_SIZE, blockData),
                        PaletteUtil.createPalette(longBitsPerBlockEntry, Arrays.asList(materialPalette))
                );
            }
        }

        return new LevelChunkSection(states, biomes);
    }

}