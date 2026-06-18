package live.minehub.polarpaper.core.world;

import live.minehub.polarpaper.core.util.CoordConversion;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3i;

public interface BlockSelector {

    @NotNull BlockSelector ALL = new BlockSelector() {
        @Override
        public boolean test(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean test(int index, int chunkX, int chunkZ, int sectionY) {
            return true;
        }
    };

    static @NotNull BlockSelector circle(int radius) {
        return circle(0, 0, radius);
    }

    static @NotNull BlockSelector circle(int centerX, int centerZ, int radius) {
        return new BlockSelector() {
            @Override
            public boolean test(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean test(int index, int chunkX, int chunkZ, int sectionY) {
                return true;
            }

            @Override
            public boolean testChunk(int x, int z) {
                int dx = x - centerX;
                int dz = z - centerZ;
                return dx * dx + dz * dz <= radius * radius;
            }
        };
    }

    static @NotNull BlockSelector square(int radius) {
        return square(0, 0, radius);
    }

    static @NotNull BlockSelector square(int centerX, int centerZ, int radius) {
        return new BlockSelector() {
            @Override
            public boolean test(int x, int y, int z) {
                return true;
            }

            @Override
            public boolean test(int index, int chunkX, int chunkZ, int sectionY) {
                return true;
            }

            @Override
            public boolean testChunk(int x, int z) {
                // Chebyshev distance
                long dx = Math.abs(x - centerX);
                long dz = Math.abs(z - centerZ);
                return Math.max(dx, dz) <= radius;
            }
        };
    }

    boolean test(int x, int y, int z);

    default boolean test(int index, int chunkX, int chunkZ, int sectionY) {
        return test(
                CoordConversion.sectionBlockIndexGetX(index) + chunkX * 16,
                CoordConversion.sectionBlockIndexGetY(index) + sectionY * 16,
                CoordConversion.sectionBlockIndexGetZ(index) + chunkZ * 16
        );
    }

    default boolean testChunk(int chunkX, int chunkZ) {
        return true;
    }

    record RegionBlockSelector(Vector3i min, Vector3i max) implements BlockSelector {
        public static RegionBlockSelector fromCorners(Vector3i corner1, Vector3i corner2) {
            return new RegionBlockSelector(corner1.min(corner2, new Vector3i()), corner1.max(corner2, new Vector3i()));
        }

        @Override
        public boolean test(int x, int y, int z) {
            return min.x <= x && max.x >= x &&
                    min.y <= y && max.y >= y &&
                    min.z <= z && max.z >= z;
        }

        @Override
        public boolean testChunk(int chunkX, int chunkZ) {
            int minX = chunkX * 16;
            int maxX = minX + 16;
            int minZ = chunkZ * 16;
            int maxZ = minZ + 16;
            return min.x <= maxX && max.x >= minX &&
                    min.z <= maxZ && max.z >= minZ;
        }
    }

}