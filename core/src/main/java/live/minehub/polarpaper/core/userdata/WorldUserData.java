package live.minehub.polarpaper.core.userdata;

import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.lang.foreign.MemorySegment;

public class WorldUserData {
    private static final byte CURRENT_FEATURES_VERSION = 1;
    private static final byte SCHEMATIC_CENTER_VERSION = 1;

    public static @Nullable Vector3i readSchematicOffset(byte[] userData) {
        if (userData.length == 0) return null;

        MemorySegment segment = MemorySegment.ofArray(userData);
        MemorySegmentReader reader = new MemorySegmentReader(segment);

        byte version = reader.readByte();
        if (version < SCHEMATIC_CENTER_VERSION) return null;

        return Vector3iCodec.decode(reader.getSegment(), reader.getOffset());
    }

    public static byte[] writeSchematicOffset(Vector3i offset) {
        try (var writer = new MemorySegmentWriter(1 + Vector3iCodec.LAYOUT.byteSize())) {
            writer.writeByte(CURRENT_FEATURES_VERSION);
            Vector3iCodec.encode(offset, writer.getSegment(), writer.getWriteIndex());

            return writer.getWrittenBytes();
        }
    }
}
