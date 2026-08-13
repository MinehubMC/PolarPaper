package live.minehub.polarpaper.core.userdata;

import org.joml.Vector3i;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class Vector3iCodec {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT_UNALIGNED.withName("x"),
            ValueLayout.JAVA_INT_UNALIGNED.withName("y"),
            ValueLayout.JAVA_INT_UNALIGNED.withName("z")
    );

    private static final VarHandle X_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));
    private static final VarHandle Y_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));
    private static final VarHandle Z_HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("z"));

    public static void encode(Vector3i v, MemorySegment segment, long offset) {
        X_HANDLE.set(segment, offset, v.x());
        Y_HANDLE.set(segment, offset, v.y());
        Z_HANDLE.set(segment, offset, v.z());
    }

    public static Vector3i decode(MemorySegment segment, long offset) {
        int x = (int) X_HANDLE.get(segment, offset);
        int y = (int) Y_HANDLE.get(segment, offset);
        int z = (int) Z_HANDLE.get(segment, offset);
        return new Vector3i(x, y, z);
    }
}
