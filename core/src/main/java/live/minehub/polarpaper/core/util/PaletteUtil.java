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
            return (Configuration) GET_CONFIGURATION_HANDLE.invokeExact(strategy, bits);
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
                value |= ((long) ints[baseIndex + j] & mask)
                        << (j * bitsPerEntry);
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

    /**
     * Repacks entries from one bits per entry to another without unpacking into an intermediate array
     */
    public static long[] repack(long[] in, int fromBits, int toBits, int entryCount) {
        final int fromPerLong = 64 / fromBits;
        final int toPerLong = 64 / toBits;
        final long mask = (1L << fromBits) - 1L;
        final long[] out = new long[packedLength(entryCount, toBits)];

        int outIndex = 0;
        int outSub = 0;
        long outValue = 0L;
        int remaining = entryCount;
        for (int longIndex = 0; longIndex < in.length && remaining > 0; longIndex++) {
            long value = in[longIndex];
            int entries = Math.min(fromPerLong, remaining);
            for (int i = 0; i < entries; i++) {
                outValue |= (value & mask) << (outSub * toBits);
                value >>>= fromBits;
                if (++outSub == toPerLong) {
                    out[outIndex++] = outValue;
                    outValue = 0L;
                    outSub = 0;
                }
            }
            remaining -= entries;
        }
        if (outSub != 0) out[outIndex] = outValue;
        return out;
    }

    /**
     * @return the number of longs needed to pack the entries, with entries not spanning longs
     */
    public static int packedLength(int entryCount, int bitsPerEntry) {
        final int perLong = 64 / bitsPerEntry;
        return (entryCount + perLong - 1) / perLong;
    }

    /**
     * Works out the bits per entry palette data was packed with. Several widths can pack into the same number of
     * longs (e.g. 3 and 4 bits for the 64 biomes in a section), so the narrowest width that can index the palette
     * and matches the data length is used
     */
    public static int getBitsForData(int paletteSize, int longLength, int stratEntryCount) {
        if (longLength == 1024 && stratEntryCount == 4096) return 15; // see getBitsForLongLength
        int minBits = paletteSize > 1 ? bitsToRepresent(paletteSize - 1) : 1;
        for (int bits = minBits; bits <= Integer.SIZE; bits++) {
            if (packedLength(stratEntryCount, bits) == longLength) return bits;
        }
        return getBitsForLongLength(longLength, stratEntryCount);
    }

    public static int getBitsForLongLength(int longLength, int stratEntryCount) {
        if (longLength == 1024) return 15;
        return (longLength * 64) / stratEntryCount;
    }

}