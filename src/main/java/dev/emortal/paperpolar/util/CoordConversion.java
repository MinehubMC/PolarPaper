package dev.emortal.paperpolar.util;

public class CoordConversion {

    public static int globalToChunk(int xz) {
        // Assume chunk/section size being 16 (4 bits)
        return xz >> 4;
    }

    public static long chunkIndex(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
    }

    public static int chunkBlockIndex(int x, int y, int z) {
        x = globalToSectionRelative(x);
        z = globalToSectionRelative(z);

        int index = x & 0xF; // 4 bits
        if (y > 0) {
            index |= (y << 4) & 0x07FFFFF0; // 23 bits (24th bit is always 0 because y is positive)
        } else {
            index |= ((-y) << 4) & 0x7FFFFF0; // Make positive and use 23 bits
            index |= 1 << 27; // Set negative sign at 24th bit
        }
        index |= (z << 28) & 0xF0000000; // 4 bits
        return index;
    }

    public static int chunkBlockIndexGetX(int index) {
        return index & 0xF; // 0-4 bits
    }

    public static int chunkBlockIndexGetY(int index) {
        int y = (index & 0x07FFFFF0) >>> 4;
        if (((index >>> 27) & 1) == 1) y = -y; // Sign bit set, invert sign
        return y; // 4-28 bits
    }

    public static int chunkBlockIndexGetZ(int index) {
        return (index >> 28) & 0xF; // 28-32 bits
    }

    public static int globalToSectionRelative(int xyz) {
        return xyz & 0xF;
    }

}
