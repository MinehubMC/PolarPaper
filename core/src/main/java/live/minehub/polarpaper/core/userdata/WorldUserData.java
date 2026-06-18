package live.minehub.polarpaper.core.userdata;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.core.util.ByteArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.nio.ByteBuffer;

public class WorldUserData {
    private static final byte CURRENT_FEATURES_VERSION = 1;
    private static final byte SCHEMATIC_CENTER_VERSION = 1;

    public static @Nullable Vector3i readSchematicOffset(byte[] userData) {
        if (userData.length == 0) return null;

        final var bb = ByteBuffer.wrap(userData);

        byte version = bb.get();
        if (version < SCHEMATIC_CENTER_VERSION) return null;

        int x = bb.getInt();
        int y = bb.getInt();
        int z = bb.getInt();

        return new Vector3i(x, y, z);
    }

    public static byte[] writeSchematicOffset(Vector3i offset) {
        ByteBuf bb = Unpooled.directBuffer();
        bb.writeByte(CURRENT_FEATURES_VERSION);
        bb.writeInt(offset.x);
        bb.writeInt(offset.y);
        bb.writeInt(offset.z);
        return ByteArrayUtil.outputArray(bb);
    }
}
