package live.minehub.polarpaper.core.source;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

@SuppressWarnings("unused")
public final class BytesPolarSource implements PolarSource {
    private static final ValueLayout.OfByte BYTE_BE = ValueLayout.JAVA_BYTE.withOrder(ByteOrder.BIG_ENDIAN);

    private byte[] bytes;

    public BytesPolarSource() {
        this.bytes = new byte[0];
    }

    public BytesPolarSource(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] bytes() {
        return bytes;
    }

    public void bytes(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public ReadableByteChannel read() throws IOException {
        return Channels.newChannel(new ByteArrayInputStream(bytes));
    }

    @Override
    public void save(MemorySegment segment) throws IOException {
        this.bytes = segment.toArray(BYTE_BE);
    }

    @Override
    public long size() {
        return this.bytes.length;
    }
}
