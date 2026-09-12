package live.minehub.polarpaper.core.util;

import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class MemorySegmentReader implements DataInput {

    private static final ValueLayout.OfShort SHORT_BE = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt INT_BE = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_BE = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfByte BYTE_BE = ValueLayout.JAVA_BYTE.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfChar CHAR_BE = ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat FLOAT_BE = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble DOUBLE_BE = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final MemorySegment segment;
    private long offset = 0;
    public MemorySegmentReader(MemorySegment segment) {
        this.segment = segment;
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public byte readByte() {
        byte b = segment.get(BYTE_BE, offset);
        offset += BYTE_BE.byteSize();
        return b;
    }

    public int readUnsignedByte() {
        return readByte() & 0xFF;
    }

    public short readShort() {
        short s = segment.get(SHORT_BE, offset);
        offset += SHORT_BE.byteSize();
        return s;
    }

    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    public char readChar() {
        char c = segment.get(CHAR_BE, offset);
        offset += CHAR_BE.byteSize();
        return c;
    }

    public int readInt() {
        int i = segment.get(INT_BE, offset);
        offset += INT_BE.byteSize();
        return i;
    }

    public long readLong() {
        long l = segment.get(LONG_BE, offset);
        offset += LONG_BE.byteSize();
        return l;
    }

    public float readFloat() {
        float f = segment.get(FLOAT_BE, offset);
        offset += FLOAT_BE.byteSize();
        return f;
    }

    public double readDouble() {
        double d = segment.get(DOUBLE_BE, offset);
        offset += DOUBLE_BE.byteSize();
        return d;
    }

    public String readLine() {
        throw new UnsupportedOperationException("readLine is deprecated");
    }

    public @NotNull String readUTF() throws IOException {
        return java.io.DataInputStream.readUTF(this); // modified UTF-8
    }

    public String readString() {
        return new String(readByteArray(), StandardCharsets.UTF_8);
    }

    public String readOptionalString() {
        boolean present = readByte() == 1;
        if (!present) return null;
        return readString();
    }

    public String[] readStringArray() {
        int strings = readVarInt();
        String[] array = new String[strings];
        for (int i = 0; i < strings; i++) {
            byte[] bytes = readByteArray();
            array[i] = new String(bytes, StandardCharsets.UTF_8);
        }
        return array;
    }

    public byte[] readByteArray() {
        int bytes = readVarInt();
        return readByteArray(bytes);
    }

    public byte[] readByteArray(int bytes) {
        long size = bytes * BYTE_BE.byteSize();
        byte[] array = segment.asSlice(offset, size).toArray(BYTE_BE);
        offset += size;
        return array;
    }

    public long[] readLongArray() {
        int longs = readVarInt();
        long size = longs * LONG_BE.byteSize();
        long[] array = segment.asSlice(offset, size).toArray(LONG_BE);
        offset += size;
        return array;
    }

    public int readVarInt() {
        int result = 0;
        int shift = 0;

        while (shift < 32) {
            // Read 1 single byte from the current native memory offset
            byte b = segment.get(BYTE_BE, offset++);

            // Extract the lower 7 bits and merge into the result
            result |= (b & 0x7F) << shift;

            // If the Most Significant Bit (MSB) is 0, we have reached the end
            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalArgumentException("Malformed Varint: Exceeded 5 bytes for 32-bit integer.");
    }

    public long getOffset() {
        return offset;
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public void readFully(byte @NotNull [] b) {
        readFully(b, 0, b.length);
    }

    public void readFully(byte @NotNull [] b, int off, int len) {
        MemorySegment.copy(segment, offset, MemorySegment.ofArray(b), off, len);
        offset += len;
    }

    public int skipBytes(int n) {
        long available = segment.byteSize() - offset;
        int skipped = (int) Math.min(n, available);
        offset += skipped;
        return skipped;
    }

}
