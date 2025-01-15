package live.minehub.polarpaper;

import org.jetbrains.annotations.NotNull;

/**
 * A {@link ChunkSelector} can be used to select some chunks from a world. This is useful for
 * saving or loading only a select portion of a world, ignoring the rest.
 * <p>
 * Polar supports {@link ChunkSelector}s in most loading/saving APIs.
 */
public interface ChunkSelector {

    static @NotNull ChunkSelector all() {
        return (x, z) -> true;
    }

    static @NotNull ChunkSelector circle(int radius) {
        return circle(0, 0, radius);
    }

    static @NotNull ChunkSelector circle(int centerX, int centerZ, int radius) {
        return (x, z) -> {
            int dx = x - centerX;
            int dz = z - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        };
    }

    static @NotNull ChunkSelector square(int radius) {
        return square(0, 0, radius);
    }

    static @NotNull ChunkSelector square(int centerX, int centerZ, int radius) {
        return (x, z) -> {
            // Chebyshev distance
            long dx = Math.abs(x - centerX);
            long dz = Math.abs(z - centerZ);
            return Math.max(dx, dz) <= radius;
        };
    }

    boolean test(int x, int z);

}