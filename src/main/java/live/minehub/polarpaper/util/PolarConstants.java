package live.minehub.polarpaper.util;

public class PolarConstants {
    public static final int MAGIC_NUMBER = 0x506F6C72; // `Polr`
    public static final short LATEST_VERSION = 7;
    public static final short MIN_VERSION = 4;

//    public static final short VERSION_UNIFIED_LIGHT = 1;
//    public static final short VERSION_USERDATA_OPT_BLOCK_ENT_NBT = 2;
//    public static final short VERSION_MINESTOM_NBT_READ_BREAK = 3;
    public static final short VERSION_WORLD_USERDATA = 4;
    public static final short VERSION_SHORT_GRASS = 5; // >:(
    public static final short VERSION_DATA_CONVERTER = 6;
    public static final short VERSION_IMPROVED_LIGHT = 7;
    public static final short VERSION_DEPRECATED_ENTITIES = 8;

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

    public static CompressionType DEFAULT_COMPRESSION = CompressionType.ZSTD;

    public static final int BLOCK_PALETTE_SIZE = 4096;
    public static final int BIOME_PALETTE_SIZE = 64;
}
