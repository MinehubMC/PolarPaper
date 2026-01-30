package live.minehub.polarpaper.util;

import org.jetbrains.annotations.Nullable;

public enum CompressionType {
    NONE,
    ZSTD;

    private static final CompressionType[] VALUES = values();

    public static @Nullable CompressionType fromId(int id) {
        if (id < 0 || id >= VALUES.length) return null;
        return VALUES[id];
    }
}