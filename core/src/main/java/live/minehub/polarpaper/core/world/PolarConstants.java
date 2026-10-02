package live.minehub.polarpaper.core.world;

import java.io.IOException;

public class PolarConstants {
    public static final int POLAR_MAGIC = 0x506F6C72; // `Polr`
    public static final int SLIME_MAGIC = 0xB10B;
    public static final short LATEST_VERSION = 7;
    public static final short MIN_VERSION = 4;

    public static final short LATEST_SLIME_VERSION = 13;
    public static final short MIN_SLIME_VERSION = 13;

    public static final int CHUNK_SECTION_SIZE = 16;

//    public static final int HEIGHTMAP_NONE = 0b0;
//    public static final int HEIGHTMAP_MOTION_BLOCKING = 0b1;
//    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0b10;
//    public static final int HEIGHTMAP_OCEAN_FLOOR = 0b100;
//    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0b1000;
//    public static final int HEIGHTMAP_WORLD_SURFACE = 0b10000;
//    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0b100000;
//    static final int[] HEIGHTMAPS = new int[]{
//            HEIGHTMAP_NONE,
//            HEIGHTMAP_MOTION_BLOCKING,
//            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
//            HEIGHTMAP_OCEAN_FLOOR,
//            HEIGHTMAP_OCEAN_FLOOR_WG,
//            HEIGHTMAP_WORLD_SURFACE,
//            HEIGHTMAP_WORLD_SURFACE_WG,
//    };
    public static final int HEIGHTMAP_SIZE = 16 * 16; // Chunk Size X * Chunk Size Z
    public static final int MAX_HEIGHTMAPS = 32;

    public static PolarWorld.CompressionType DEFAULT_COMPRESSION = PolarWorld.CompressionType.ZSTD;
    public static int DEFAULT_COMPRESSION_LEVEL = 7;

    public static final int BLOCK_PALETTE_SIZE = 4096;
    public static final int BIOME_PALETTE_SIZE = 64;

    public static void validatePolarVersion(int version) throws IOException {
        var invalidVersionError = String.format("Unsupported Polar version. Versions %d - %d are supported, found %d.",
                PolarConstants.LATEST_VERSION, PolarConstants.MIN_VERSION, version);
        if ((version <= PolarConstants.LATEST_VERSION && version >= PolarConstants.MIN_VERSION)) return;
        throw new IOException(invalidVersionError);
    }

}
