package live.minehub.polarpaper;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

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

        @SuppressWarnings("MismatchedReadAndWriteOfArray")
        private static final byte[] CONTENT_EMPTY = new byte[2048];
        private static final byte[] CONTENT_FULLY_LIT = new byte[2048];

        static LightContent calculateLightContent(byte[] content) {
            if (content == null) return MISSING;
            if (content.length == 0 || Arrays.equals(content, CONTENT_EMPTY)) return EMPTY;
            if (Arrays.equals(content, CONTENT_FULLY_LIT)) return FULL;
            return PRESENT;
        }

        static {
            Arrays.fill(CONTENT_FULLY_LIT, (byte) -1);
        }
    }

    private final boolean empty;

    private final String @NotNull [] blockPalette;
    private final int @Nullable [] blockData;

    private final String @NotNull [] biomePalette;
    private final int @Nullable [] biomeData;

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
            String @NotNull [] blockPalette, int @Nullable [] blockData,
            String @NotNull [] biomePalette, int @Nullable [] biomeData,
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
    public int[] blockData() {
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
    public int[] biomeData() {
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
}