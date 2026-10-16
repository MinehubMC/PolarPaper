package live.minehub.polarpaper.core.source;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

@SuppressWarnings("unused")
public final class BytesPolarSource implements PolarSource {
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
    public void save(StreamWriter writer) throws IOException {
        var output = new ByteArrayOutputStream();
        writer.write(output);
        this.bytes = output.toByteArray();
    }

    @Override
    public long size() {
        return this.bytes.length;
    }
}
