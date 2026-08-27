package live.minehub.polarpaper.core.source;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.channels.ReadableByteChannel;

public interface PolarSource {
    ReadableByteChannel read() throws IOException;

    void save(MemorySegment segment) throws IOException;

    long size();

    default void delete() throws Exception {
        throw new UnsupportedOperationException("This PolarSource does not support deletion");
    }

    default void rename() throws Exception {
        throw new UnsupportedOperationException("This PolarSource does not support renaming");
    }
}
