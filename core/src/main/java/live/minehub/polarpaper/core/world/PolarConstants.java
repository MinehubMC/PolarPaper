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

    public static PolarWorld.CompressionType DEFAULT_COMPRESSION = PolarWorld.CompressionType.ZSTD;
    public static int DEFAULT_COMPRESSION_LEVEL = 7;

    public static void validatePolarVersion(int version) throws IOException {
        var invalidVersionError = String.format("Unsupported Polar version. Versions %d - %d are supported, found %d.",
                PolarConstants.LATEST_VERSION, PolarConstants.MIN_VERSION, version);
        if ((version <= PolarConstants.LATEST_VERSION && version >= PolarConstants.MIN_VERSION)) return;
        throw new IOException(invalidVersionError);
    }

}
