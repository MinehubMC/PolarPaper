package live.minehub.polarpaper.core.userdata;

import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

public class WorldUserData {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldUserData.class);

    private static final byte CURRENT_FEATURES_VERSION = 1;
    private static final byte SCHEMATIC_CENTER_VERSION = 1;

    public static @Nullable Vector3i readSchematicOffset(byte[] userData) {
        if (userData.length == 0) return null;

        try {
            MemorySegment segment = MemorySegment.ofArray(userData);
            MemorySegmentReader reader = new MemorySegmentReader(segment);

            byte version = reader.readByte();
            if (version < SCHEMATIC_CENTER_VERSION) return null;

            return Vector3iCodec.decode(reader.getSegment(), reader.getOffset());
        } catch (Exception e) {
            LOGGER.error("Error reading schematic offset", e);
            return null;
        }
    }

    public static byte[] writeSchematicOffset(Vector3i offset) {
        try (var writer = new MemorySegmentWriter(1 + Vector3iCodec.LAYOUT.byteSize())) {
            writer.writeByte(CURRENT_FEATURES_VERSION);
            Vector3iCodec.encode(offset, writer.getSegment(), writer.getWriteIndex());
            writer.setWriteIndex(writer.getWriteIndex() + Vector3iCodec.LAYOUT.byteSize());

            return writer.getWrittenBytes();
        } catch (Exception e) {
            LOGGER.error("Error writing schematic offset", e);
            return new byte[0];
        }
    }
}
