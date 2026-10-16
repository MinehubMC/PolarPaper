package live.minehub.polarpaper.core.source;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;

public interface PolarSource {
    ReadableByteChannel read() throws IOException;

    void save(StreamWriter writer) throws IOException;

    interface StreamWriter {
        void write(OutputStream output) throws IOException;
    }

    long size();

    default void delete() throws Exception {
        throw new UnsupportedOperationException("This PolarSource does not support deletion");
    }

}
