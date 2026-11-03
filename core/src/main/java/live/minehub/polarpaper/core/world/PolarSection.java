package live.minehub.polarpaper.core.world;

import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import io.papermc.paper.antixray.ChunkPacketBlockController;
import live.minehub.polarpaper.core.util.BlockCodec;
import live.minehub.polarpaper.core.util.PaletteUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Representation of the latest version of the section format.
 * <p>
 * Marked as internal because of the use of mutable arrays. These arrays must _not_ be mutated.
 * This class should be considered immutable.
 */
@ApiStatus.Internal
public record PolarSection(boolean empty, String[] blockPalette, long @Nullable [] blockData, String[] biomePalette,
                           long @Nullable [] biomeData, LightContent blockLightContent,
                           @Nullable SWMRNibbleArray blockLight, LightContent skyLightContent,
                           @Nullable SWMRNibbleArray skyLight) {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolarSection.class);

    public enum LightContent {
        MISSING, EMPTY, FULL, PRESENT;

        public static final LightContent[] VALUES = values();
    }

    public PolarSection() {
        this(true, new String[]{"minecraft:air"}, null, new String[]{"minecraft:plains"}, null, LightContent.MISSING, null, LightContent.MISSING, null);
    }

    public PolarSection(
            String[] blockPalette, long @Nullable [] blockData,
            String[] biomePalette, long @Nullable [] biomeData,
            LightContent blockLightContent, @Nullable SWMRNibbleArray blockLight,
            LightContent skyLightContent, @Nullable SWMRNibbleArray skyLight
    ) {
        this(false, blockPalette, blockData, biomePalette, biomeData, blockLightContent, blockLight, skyLightContent, skyLight);
    }

    public PolarSection(
            LightContent blockLightContent, @Nullable SWMRNibbleArray blockLight,
            LightContent skyLightContent, @Nullable SWMRNibbleArray skyLight
    ) {
        this(false, new String[]{"minecraft:air"}, null, new String[]{"minecraft:plains"}, null, blockLightContent, blockLight, skyLightContent, skyLight);
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 4096.
     */
    @Override
    public long[] blockData() {
        assert blockData != null : "must check length of blockPalette() before using blockData()";
        return blockData;
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 256.
     */
    @Override
    public long[] biomeData() {
        assert biomeData != null : "must check length of biomePalette() before using biomeData()";
        return biomeData;
    }

    @Override
    public SWMRNibbleArray blockLight() {
        assert blockLight != null : "must check hasBlockLightData() before calling blockLight()";
        return blockLight;
    }

    @Override
    public SWMRNibbleArray skyLight() {
        assert skyLight != null : "must check hasSkyLightData() before calling skyLight()";
        return skyLight;
    }

    /**
     * Returns the anti-xray preset block states Paper expects in the block palette of a section, null when anti-xray is
     * disabled for the level. Sections created for a level must carry exactly this array.
     */
    public static BlockState @Nullable [] getPresetBlockStates(Level level, ChunkPos chunkPos, int sectionY) {
        ChunkPacketBlockController controller = level.chunkPacketBlockController;
        return controller == null ? null : controller.getPresetBlockStates(level, chunkPos, sectionY);
    }

    public LevelChunkSection createEmptyLevelChunkSection(RegistryAccess registryAccess) {
        return createEmptyLevelChunkSection(registryAccess, null);
    }

    public LevelChunkSection createEmptyLevelChunkSection(RegistryAccess registryAccess, BlockState @Nullable [] presets) {
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, presets);

        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, null);
        return new LevelChunkSection(states, biomes);
    }

    public LevelChunkSection createLevelChunkSection(RegistryAccess registryAccess) {
        return createLevelChunkSection(registryAccess, null);
    }

    /**
     * @param presets the level's anti-xray presets for this section, from {@link #getPresetBlockStates}
     */
    public LevelChunkSection createLevelChunkSection(RegistryAccess registryAccess, BlockState @Nullable [] presets) {
        if (empty) return createEmptyLevelChunkSection(registryAccess, presets);

        // Blocks
        BlockState[] materialPalette = new BlockState[blockPalette.length];
        for (int i = 0; i < blockPalette.length; i++) {
            materialPalette[i] = BlockCodec.blockFromString(blockPalette[i]);
        }

        // Biomes
        Registry<Biome> registry = registryAccess.lookupOrThrow(Registries.BIOME);
        Holder.Reference<Biome> orThrow = registry.getOrThrow(Biomes.PLAINS);
        Holder<Biome>[] biomeHolderPalette = new Holder[biomePalette().length];
        for (int i = 0; i < biomePalette().length; i++) {
            Identifier identifier = Identifier.tryParse(biomePalette()[i]);
            if (identifier == null) {
                LOGGER.warn("Failed to parse biome key: {}", biomePalette()[i]);
                biomeHolderPalette[i] = orThrow;
                continue;
            }
            Holder.Reference<Biome> biome = registry.get(identifier).orElse(null);
            if (biome == null) {
                LOGGER.warn("Failed to get biome: {}", biomePalette()[i]);
                biomeHolderPalette[i] = orThrow;
                continue;
            }
            biomeHolderPalette[i] = biome;
        }

        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        // Paper's anti-xray reads the preset values with the length of the level's own preset list, so they must be
        // exactly the level's presets and never the section palette
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, presets);

        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, null);

        int bitsPerBlockEntry = Mth.ceillog2(blockPalette.length);
        int longBitsPerBlockEntry = bitsPerBlockEntry;
        if (blockData != null) {
            longBitsPerBlockEntry = PaletteUtil.getBitsForData(blockPalette.length, blockData.length, blockStrategy.entryCount());
        }

        int bitsPerBiomeEntry = Mth.ceillog2(biomePalette.length);
        int longBitsPerBiomeEntry = bitsPerBiomeEntry;
        if (biomeData != null) {
            longBitsPerBiomeEntry = PaletteUtil.getBitsForData(biomePalette.length, biomeData.length, biomeStrategy.entryCount());
        }

        biomes.data = getPalettedContainer(biomeHolderPalette, biomeData, bitsPerBiomeEntry, longBitsPerBiomeEntry, biomeStrategy, registry.asHolderIdMap()::getId);
        states.data = getPalettedContainer(materialPalette, blockData, bitsPerBlockEntry, longBitsPerBlockEntry, blockStrategy, Block.BLOCK_STATE_REGISTRY::getId);
        if (presets != null) addPresets(states, presets);

        try {
            return new LevelChunkSection(states, biomes);
        } catch (Exception e) {
            LOGGER.info("Biome Bits: {}", bitsPerBiomeEntry);
            if (biomeData != null) LOGGER.info("Biome Data Length: {}", biomeData.length);
            LOGGER.info("Biome Palette Length: {}", biomePalette.length);
            LOGGER.info("----");
            LOGGER.info("Block Bits: {}", bitsPerBlockEntry);
            if (blockData != null) LOGGER.info("Block Data Length: {}", blockData.length);
            LOGGER.info("Block Palette Length: {}", materialPalette.length);
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds the anti-xray presets to the palette of a container whose data was set directly, the same way Paper does when
     * it reads a section from disk. The chunk packet looks the presets up in the palette without resizing it.
     */
    private static void addPresets(PalettedContainer<BlockState> states, BlockState[] presets) {
        Configuration configuration = states.data.configuration();
        boolean singleValue = configuration instanceof Configuration.Simple simple && simple.factory() == Strategy.SINGLE_VALUE_PALETTE_FACTORY;
        if (singleValue ? states.data.palette().valueFor(0) == Blocks.AIR.defaultBlockState() : configuration instanceof Configuration.Global) {
            return;
        }

        int maxSize = 1 << configuration.bitsInMemory();
        for (int i = 0; i < presets.length; i++) {
            Palette<BlockState> palette = states.data.palette();
            if (palette.getSize() >= maxSize) {
                Set<BlockState> allValues = new HashSet<>();
                for (int j = 0; j < palette.getSize(); j++) allValues.add(palette.valueFor(j));
                allValues.addAll(Arrays.asList(presets));
                int newBits = Mth.ceillog2(allValues.size());
                if (newBits > configuration.bitsInMemory()) {
                    states.onResize(newBits, null); // copies into a bigger palette and adds all presets
                } else {
                    for (int j = i; j < presets.length; j++) states.data.palette().idFor(presets[j], states);
                }
                return;
            }
            palette.idFor(presets[i], states);
        }
    }

    private static <T> PalettedContainer.Data<T> getPalettedContainer(T[] palette, long @Nullable [] data, int bits, int longBits, Strategy<T> strategy, ToIntFunction<T> globalId) {
        if (data == null || data.length == 0 || bits == 0) {
            Configuration configuration = PaletteUtil.getConfigurationForBitCount(strategy, 0);
            return new PalettedContainer.Data<>(
                    configuration,
                    new ZeroBitStorage(strategy.entryCount()),
                    configuration.createPalette(strategy, List.of(palette[0]))
            );
        }

        Configuration configuration = PaletteUtil.getConfigurationForBitCount(strategy, bits);
        int valuesPerLong = (char) (64 / configuration.bitsInMemory());
        int requiredLength = (strategy.entryCount() + valuesPerLong - 1) / valuesPerLong;
        Palette<T> created = configuration.createPalette(strategy, Arrays.asList(palette));
        boolean global = created instanceof GlobalPalette;

        long[] packed;
        if (!global && !configuration.alwaysRepack() && configuration.bitsInMemory() == longBits && data.length == requiredLength) {
            packed = data.clone();
        } else if (!global && !configuration.alwaysRepack()) {
            // e.g. a block palette with 2-8 entries is stored with 1-3 bits but vanilla always uses 4 in memory
            packed = PaletteUtil.repack(data, longBits, configuration.bitsInMemory(), strategy.entryCount());
        } else {
            int[] unpacked = new int[strategy.entryCount()];
            PaletteUtil.unpack(unpacked, data, longBits);
            if (global) {
                // local palette index -> current registry ID
                int[] ids = new int[palette.length];
                for (int i = 0; i < palette.length; i++) ids[i] = globalId.applyAsInt(palette[i]);
                for (int i = 0; i < unpacked.length; i++) unpacked[i] = ids[unpacked[i]];
            }
            packed = PaletteUtil.pack(unpacked, configuration.bitsInMemory());
        }

        try {
            return new PalettedContainer.Data<>(
                    configuration,
                    new SimpleBitStorage(configuration.bitsInMemory(), strategy.entryCount(), packed),
                    created
            );
        } catch (SimpleBitStorage.InitializationException e) {
            LOGGER.info("Bits: {}, {}, {}", configuration.bitsInMemory(), configuration.bitsInStorage(), bits);
            LOGGER.info("Strategy Entry Count: {}", strategy.entryCount());
            LOGGER.info("Data: {}", Arrays.toString(data));
            LOGGER.info("Packed: {}", Arrays.toString(packed));
            LOGGER.info("Palette: {}", Arrays.toString(palette));
            throw new RuntimeException(e);
        }
    }

}