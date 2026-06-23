package live.minehub.polarpaper.core.world;

import live.minehub.polarpaper.core.util.CoordConversion;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.util.function.Consumer;

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

        @Override
        public void forEachChunk(Consumer<Vector2i> chunkConsumer) {
            // ALL block selector cannot loop through every chunk
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

            @Override
            public void forEachChunk(Consumer<Vector2i> chunkConsumer) {
                int minX = (centerX - radius) - 1;
                int minZ = (centerZ - radius) - 1;
                int maxX = (centerX + radius) + 1;
                int maxZ = (centerZ + radius) + 1;

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!testChunk(x, z)) continue;
                        chunkConsumer.accept(new Vector2i(x, z));
                    }
                }
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

            @Override
            public void forEachChunk(Consumer<Vector2i> chunkConsumer) {
                int minX = (centerX - radius) - 1;
                int minZ = (centerZ - radius) - 1;
                int maxX = (centerX + radius) + 1;
                int maxZ = (centerZ + radius) + 1;

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!testChunk(x, z)) continue;
                        chunkConsumer.accept(new Vector2i(x, z));
                    }
                }
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

    /**
     * Loop through every chunk that this block selector contains. Does not return anything with ALL block selector.
     * Used to add additional chunks to consider while saving the world.
     */
    void forEachChunk(Consumer<Vector2i> chunkConsumer);

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

        @Override
        public void forEachChunk(Consumer<Vector2i> chunkConsumer) {
            int minX = (min().x / 16) - 1;
            int minZ = (min().z / 16) - 1;
            int maxX = (max().x / 16) + 1;
            int maxZ = (max().z / 16) + 1;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!testChunk(x, z)) continue;
                    chunkConsumer.accept(new Vector2i(x, z));
                }
            }
        }
    }

}