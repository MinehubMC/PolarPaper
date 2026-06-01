package live.minehub.polarpaper.util;

import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Strategy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class PaletteUtil {
    private PaletteUtil() {}

    private static final Method GET_CONFIGURATION_METHOD;
    static {
        try {
            GET_CONFIGURATION_METHOD = Strategy.class.getDeclaredMethod("getConfigurationForBitCount", int.class);
            GET_CONFIGURATION_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static Configuration getConfigurationForBitCount(Strategy<?> strategy, int bits) {
        try {
            return (Configuration) GET_CONFIGURATION_METHOD.invoke(strategy, bits);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
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

    public static int getBitsForLongLength(int longLength, int stratEntryCount) {
        if (longLength == 1024) return 15;
        return (longLength * 64) / stratEntryCount;
    }

}