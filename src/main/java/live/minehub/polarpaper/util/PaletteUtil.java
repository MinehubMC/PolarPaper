package live.minehub.polarpaper.util;

import net.minecraft.world.level.chunk.*;

import java.util.List;

public final class PaletteUtil {
    private PaletteUtil() {}

    private static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
    private static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
    private static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
    static final Configuration ZERO_BITS = new Configuration.Simple(SINGLE_VALUE_PALETTE_FACTORY, 0);
    //    static final Configuration ONE_BIT_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 1);
//    static final Configuration TWO_BITS_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 2);
//    static final Configuration THREE_BITS_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 3);
    static final Configuration FOUR_BITS_LINEAR = new Configuration.Simple(LINEAR_PALETTE_FACTORY, 4);
    static final Configuration FIVE_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 5);
    static final Configuration SIX_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 6);
    static final Configuration SEVEN_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 7);
    static final Configuration EIGHT_BITS_HASHMAP = new Configuration.Simple(HASHMAP_PALETTE_FACTORY, 8);

    public static Configuration getConfigurationForBitCount(int bits) {
        return switch (bits) {
            case 0 -> ZERO_BITS;
            case 1, 2, 3, 4 -> FOUR_BITS_LINEAR;
            case 5 -> FIVE_BITS_HASHMAP;
            case 6 -> SIX_BITS_HASHMAP;
            case 7 -> SEVEN_BITS_HASHMAP;
            case 8 -> EIGHT_BITS_HASHMAP;
            default -> new Configuration.Simple(HASHMAP_PALETTE_FACTORY, bits);
        };
    }

    public static <T> Palette<T> createPalette(int bits, List<T> values) {
        return switch (bits) {
            case 0 -> SINGLE_VALUE_PALETTE_FACTORY.create(bits, values);
            case 1, 2, 3, 4 -> LINEAR_PALETTE_FACTORY.create(bits, values);
            default -> HASHMAP_PALETTE_FACTORY.create(bits, values);
        };
    }

    public static int bitsToRepresent(int n) {
        assert n > 0;
        return Integer.SIZE - Integer.numberOfLeadingZeros(n);
    }

    public static long[] pack(int[] ints, int bitsPerEntry) {
        final int intsPerLong = 64 / bitsPerEntry;
        final int intCount = ints.length;
        final int longCount = (intCount + intsPerLong - 1) / intsPerLong;

        final long[] longs = new long[longCount];
        final long mask = (1L << bitsPerEntry) - 1L;

        int baseIndex = 0;

        for (int i = 0; i < longCount; i++) {
            long value = 0L;

            int remaining = intCount - baseIndex;
            int entries = Math.min(intsPerLong, remaining);

            for (int j = 0; j < entries; j++) {
                value |= ((long) ints[baseIndex + j] & mask)
                        << (j * bitsPerEntry);
            }

            longs[i] = value;
            baseIndex += entries;
        }

        return longs;
    }

    public static void unpack(int[] out, long[] in, int bitsPerEntry) {
        assert in.length != 0 : "unpack input array is zero";

        final int intsPerLong = 64 / bitsPerEntry;
        final long mask = (1L << bitsPerEntry) - 1L;

        int outIndex = 0;

        for (int longIndex = 0; longIndex < in.length && outIndex < out.length; longIndex++) {
            long value = in[longIndex];

            for (int subIndex = 0; subIndex < intsPerLong && outIndex < out.length; subIndex++) {
                out[outIndex++] = (int) (value & mask);
                value >>>= bitsPerEntry;
            }
        }
    }
}