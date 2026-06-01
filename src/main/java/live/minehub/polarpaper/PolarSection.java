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
import net.minecraft.world.level.chunk.Configuration;
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

    public PolarSection(
            @NotNull LightContent blockLightContent, byte @Nullable [] blockLight,
            @NotNull LightContent skyLightContent, byte @Nullable [] skyLight
    ) {
        this.empty = false;

        this.blockPalette = new String[]{"minecraft:air"};
        this.blockData = null;
        this.biomePalette = new String[]{"minecraft:plains"};
        this.biomeData = null;

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
        Holder<Biome>[] biomeHolderPalette = new Holder[biomePalette().length];
        for (int i = 0; i < biomePalette().length; i++) {
            Identifier identifier = Identifier.tryParse(biomePalette()[i]);
            if (identifier == null) {
                PolarPaper.logger().severe("Failed to parse " + biomeHolderPalette[i]);
                biomeHolderPalette[i] = orThrow;
                continue;
            }
            Holder.Reference<Biome> biome = registry.get(identifier).orElse(null);
            if (biome == null) {
                PolarPaper.logger().severe("Failed to get " + biomeHolderPalette[i]);
                biomeHolderPalette[i] = orThrow;
                continue;
            }
            biomeHolderPalette[i] = biome;
        }

        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), blockStrategy, materialPalette);

        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(registry.asHolderIdMap());
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(orThrow, biomeStrategy, biomeHolderPalette);

        int bitsPerBlockEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
        int longBitsPerBlockEntry = bitsPerBlockEntry;
        if (blockData != null) {
            longBitsPerBlockEntry = PaletteUtil.getBitsForLongLength(blockData.length, blockStrategy.entryCount());
        }

        int bitsPerBiomeEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
        int longBitsPerBiomeEntry = bitsPerBiomeEntry;
        if (biomeData != null) {
            longBitsPerBiomeEntry = PaletteUtil.getBitsForLongLength(biomeData.length, biomeStrategy.entryCount());
        }

        biomes.data = getPalettedContainer(biomeHolderPalette, biomeData, bitsPerBiomeEntry, longBitsPerBiomeEntry, biomeStrategy);
        states.data = getPalettedContainer(materialPalette, blockData, bitsPerBlockEntry, longBitsPerBlockEntry, blockStrategy);

        try {
            return new LevelChunkSection(states, biomes);
        } catch (Exception e) {
            PolarPaper.logger().info("Biome Bits: " + bitsPerBiomeEntry);
            PolarPaper.logger().info("Biome Long Bits: " + longBitsPerBiomeEntry);
            if (biomeData != null) PolarPaper.logger().info("Biome Data Length: " + biomeData.length);
            PolarPaper.logger().info("Biome Palette Length: " + biomePalette.length);
            PolarPaper.logger().info("----");
            PolarPaper.logger().info("Block Bits: " + bitsPerBlockEntry);
            PolarPaper.logger().info("Block Long Bits: " + longBitsPerBlockEntry);
            if (blockData != null) PolarPaper.logger().info("Block Data Length: " + blockData.length);
            PolarPaper.logger().info("Block Palette Length: " + materialPalette.length);
            throw new RuntimeException(e);
        }
    }

     private static <T> PalettedContainer.Data<T> getPalettedContainer(T[] palette, long[] data, int bits, int longBits, Strategy<T> strategy) {
         if (data == null || bits == 0 || longBits == 0) {
             Configuration configuration = PaletteUtil.getConfigurationForBitCount(strategy, 0);
             return new PalettedContainer.Data<>(
                     configuration,
                     new ZeroBitStorage(strategy.entryCount()),
                     configuration.createPalette(strategy, List.of(palette[0]))
             );
         } else {
             int valuesPerLong = (char) (64 / bits);
             int expectedDataLength = (strategy.entryCount() + valuesPerLong - 1) / valuesPerLong;

             Configuration configuration = PaletteUtil.getConfigurationForBitCount(strategy, bits);
             long[] packed;
             if (configuration.alwaysRepack() || configuration.bitsInMemory() != bits || data.length != expectedDataLength || bits != longBits) {
                 int[] unpacked = new int[strategy.entryCount()];
                 PaletteUtil.unpack(unpacked, data, longBits);
                 packed = PaletteUtil.pack(unpacked, configuration.bitsInMemory());
             } else {
                 packed = data.clone(); // only clone if not repacked
             }

             try {
                 return new PalettedContainer.Data<>(
                         configuration,
                         new SimpleBitStorage(configuration.bitsInMemory(), strategy.entryCount(), packed),
                         configuration.createPalette(strategy, Arrays.asList(palette))
                 );
             } catch (SimpleBitStorage.InitializationException e) {
                 PolarPaper.logger().info("Bits in memory: " + configuration.bitsInMemory());
                 PolarPaper.logger().info("Bits in storage: " + configuration.bitsInStorage());
                 PolarPaper.logger().info("Bits: " + bits);
                 PolarPaper.logger().info("Data Length: " + data.length);
                 PolarPaper.logger().info("Packed Length: " + packed.length);
                 PolarPaper.logger().info("Palette Length: " + palette.length);
                 PolarPaper.logger().info("Strategy Entry Count: " + strategy.entryCount());
                 throw new RuntimeException(e);
             }
         }
     }

}