package live.minehub.polarpaper.core.util;

import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Strategy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class PaletteUtil {
    private PaletteUtil() {}

    private static final MethodHandle GET_CONFIGURATION_HANDLE;
    static {
        try {
            GET_CONFIGURATION_HANDLE = MethodHandles
                    .privateLookupIn(Strategy.class, MethodHandles.lookup())
                    .findVirtual(Strategy.class, "getConfigurationForBitCount", MethodType.methodType(Configuration.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Configuration getConfigurationForBitCount(Strategy<?> strategy, int bits) {
        try {
            return (Configuration) GET_CONFIGURATION_HANDLE.invoke(strategy, bits);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static int bitsToRepresent(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be greater than zero");
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
                value |= (ints[baseIndex + j] & mask) << (j * bitsPerEntry);
            }

            longs[i] = value;
            baseIndex += entries;
        }

        return longs;
    }

    public static void unpack(int[] out, long[] in, int bitsPerEntry) {
        if (in.length == 0) throw new IllegalArgumentException("unpack input array is zero");

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