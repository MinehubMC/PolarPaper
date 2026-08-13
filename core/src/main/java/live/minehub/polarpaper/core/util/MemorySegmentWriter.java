package live.minehub.polarpaper.core.util;

import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class MemorySegmentWriter implements AutoCloseable, DataOutput {
    private static final ValueLayout.OfShort SHORT_BE = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt INT_BE = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_BE = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfByte BYTE_BE = ValueLayout.JAVA_BYTE.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat FLOAT_BE = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble DOUBLE_BE = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final Arena arena;
    private MemorySegment segment;
    private long writeIndex = 0;

    public MemorySegmentWriter(Arena arena, long initialCapacity) {
        this.arena = arena;
        this.segment = arena.allocate(initialCapacity);
    }

    public MemorySegmentWriter(long initialCapacity) {
        this(Arena.ofConfined(), initialCapacity);
    }

    private void ensureWritable(long bytesNeeded) {
        long currentCapacity = segment.byteSize();
        if (writeIndex + bytesNeeded > currentCapacity) {
            // Double the capacity or meet the required size
            long newCapacity = Math.max(currentCapacity * 2, writeIndex + bytesNeeded);

            MemorySegment newSegment = arena.allocate(newCapacity);

            MemorySegment.copy(this.segment, 0, newSegment, 0, writeIndex);

            this.segment = newSegment;
        }
    }

    public MemorySegment getWrittenSegment() { // Return a slice of the actually written data
        return segment.asSlice(0, writeIndex);
    }

    public byte[] getWrittenBytes() {
        return getWrittenSegment().toArray(BYTE_BE);
    }

    public MemorySegment getSegment() {
        return segment;
    }

    public long getWriteIndex() {
        return writeIndex;
    }

    public void writeByte(byte b) {
        ensureWritable(BYTE_BE.byteSize());
        segment.set(BYTE_BE, writeIndex, b);
        writeIndex += BYTE_BE.byteSize();
    }

    public void writeShort(short s) {
        ensureWritable(SHORT_BE.byteSize());
        segment.set(SHORT_BE, writeIndex, s);
        writeIndex += SHORT_BE.byteSize();
    }

    @Override
    public void write(int b) {
        writeByte((byte) b);
    }

    @Override
    public void write(byte @NotNull [] b) {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) {
        MemorySegment srcSegment = MemorySegment.ofArray(b);
        ensureWritable(srcSegment.byteSize());
        MemorySegment.copy(srcSegment, off, this.segment, this.writeIndex, len);
        this.writeIndex += len;
    }

    @Override
    public void writeBoolean(boolean v) {
        writeByte((byte) (v ? 1 : 0));
    }

    @Override
    public void writeByte(int v){
        writeByte((byte) v);
    }

    @Override
    public void writeShort(int v) {
        writeShort((short) v);
    }

    @Override
    public void writeChar(int v) {
        throw new UnsupportedOperationException("aa");
    }

    public void writeInt(int i) {
        ensureWritable(INT_BE.byteSize());
        segment.set(INT_BE, writeIndex, i);
        writeIndex += INT_BE.byteSize();
    }

    public void writeLong(long l) {
        ensureWritable(LONG_BE.byteSize());
        segment.set(LONG_BE, writeIndex, l);
        writeIndex += LONG_BE.byteSize();
    }

    public void writeFloat(float f) {
        ensureWritable(FLOAT_BE.byteSize());
        segment.set(FLOAT_BE, writeIndex, f);
        writeIndex += FLOAT_BE.byteSize();
    }

    public void writeDouble(double d) {
        ensureWritable(DOUBLE_BE.byteSize());
        segment.set(DOUBLE_BE, writeIndex, d);
        writeIndex += DOUBLE_BE.byteSize();
    }

    @Override
    public void writeBytes(@NotNull String s) {
        int len = s.length();
        ensureWritable(len);
        for (int i = 0; i < len; i++) {
            segment.set(ValueLayout.JAVA_BYTE, writeIndex++, (byte) s.charAt(i));
        }
    }

    @Override
    public void writeChars(@NotNull String s) {
        int len = s.length();
        ensureWritable((long) len * ValueLayout.JAVA_CHAR.byteSize());
        for (int i = 0; i < len; i++) {
            segment.set(ValueLayout.JAVA_CHAR, writeIndex, s.charAt(i));
            writeIndex += ValueLayout.JAVA_CHAR.byteSize();
        }
    }

    @Override
    public void writeUTF(@NotNull String str) throws IOException {
        int strlen = str.length();
        int utflen = 0;
        int c;

        for (int i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }

        if (utflen > 65535) {
            throw new java.io.UTFDataFormatException("encoded string too long: " + utflen + " bytes");
        }

        byte[] bytearr = new byte[utflen + 2];
        int count = 0;
        bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        bytearr[count++] = (byte) (utflen & 0xFF);

        int i;
        for (i = 0; i < strlen; i++) {
            c = str.charAt(i);
            if (!(c >= 0x0001 && c <= 0x007F)) break;
            bytearr[count++] = (byte) c;
        }

        for (; i < strlen; i++) {
            c = str.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                bytearr[count++] = (byte) c;
            } else if (c > 0x07FF) {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | (c & 0x3F));
            } else {
                bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | (c & 0x3F));
            }
        }

        write(bytearr, 0, count);
    }

    public void writeByteArray(byte[] bytes) {
        writeVarInt(bytes.length);
        write(bytes);
    }

    public void writeSegment(MemorySegment segment) {
        ensureWritable(segment.byteSize());
        MemorySegment.copy(segment, 0, this.segment, this.writeIndex, segment.byteSize());
        this.writeIndex += segment.byteSize();
    }

    public void writeLongArray(long[] longs) {
        writeVarInt(longs.length);
        ensureWritable((long) longs.length * LONG_BE.byteSize());
        for (long l : longs) {
            segment.set(LONG_BE, writeIndex, l);
            writeIndex += LONG_BE.byteSize();
        }
    }

    public void writeString(String string) {
        writeByteArray(string.getBytes(StandardCharsets.UTF_8));
    }

    public void writeStringArray(String[] strings) {
        writeVarInt(strings.length);
        for (String string : strings) {
            writeString(string);
        }
    }

    public void writeVarInt(int v) {
        while (true) {
            int bits = v & 0x7f;
            v >>>= 7;
            if (v == 0) {
                writeByte((byte) bits);
                return;
            }
            writeByte((byte) (bits | 0x80));
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}