package live.minehub.polarpaper.core.source;

@SuppressWarnings("unused")
public final class BytesPolarSource implements PolarSource {
    private byte[] bytes;

    public BytesPolarSource() {
        this.bytes = new byte[0];
    }

    public BytesPolarSource(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] readBytes() {
        return this.bytes();
    }

    @Override
    public void saveBytes(byte[] data) {
        this.bytes = data;
    }

    public byte[] bytes() {
        return bytes;
    }

    public void bytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
