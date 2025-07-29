package live.minehub.polarpaper.util;

import com.google.common.io.ByteArrayDataOutput;
import io.netty.buffer.ByteBuf;
import live.minehub.polarpaper.PolarChunk;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteArrayUtil {

    public static byte[] getByteArray(ByteBuffer bb) {
        int packedLength = getVarInt(bb);
        byte[] bytes = new byte[packedLength];
        for (int i = 0; i < packedLength; i++) {
            bytes[i] = bb.get();
        }
        return bytes;
    }

    public static byte[] getByteArray(ByteBuf bb) {
        int packedLength = getVarInt(bb);
        byte[] bytes = new byte[packedLength];
        for (int i = 0; i < packedLength; i++) {
            bytes[i] = bb.readByte();
        }
        return bytes;
    }

    public static long[] getLongArray(ByteBuf bb) {
        int packedLength = getVarInt(bb);
        long[] longs = new long[packedLength];
        for (int i = 0; i < packedLength; i++) {
            longs[i] = bb.readLong();
        }
        return longs;
    }

    // Copyright 2019 Google LLC
    public static int getVarInt(ByteBuffer bb) {
        int tmp;
        if ((tmp = bb.get()) >= 0) {
            return tmp;
        }
        int result = tmp & 0x7f;
        if ((tmp = bb.get()) >= 0) {
            result |= tmp << 7;
        } else {
            result |= (tmp & 0x7f) << 7;
            if ((tmp = bb.get()) >= 0) {
                result |= tmp << 14;
            } else {
                result |= (tmp & 0x7f) << 14;
                if ((tmp = bb.get()) >= 0) {
                    result |= tmp << 21;
                } else {
                    result |= (tmp & 0x7f) << 21;
                    result |= (tmp = bb.get()) << 28;
                    while (tmp < 0) {
                        // We get into this loop only in the case of overflow.
                        // By doing this, we can call getVarInt() instead of
                        // getVarLong() when we only need an int.
                        tmp = bb.get();
                    }
                }
            }
        }
        return result;
    }

    public static int getVarInt(ByteBuf bb) {
        int tmp;
        if ((tmp = bb.readByte()) >= 0) {
            return tmp;
        }
        int result = tmp & 0x7f;
        if ((tmp = bb.readByte()) >= 0) {
            result |= tmp << 7;
        } else {
            result |= (tmp & 0x7f) << 7;
            if ((tmp = bb.readByte()) >= 0) {
                result |= tmp << 14;
            } else {
                result |= (tmp & 0x7f) << 14;
                if ((tmp = bb.readByte()) >= 0) {
                    result |= tmp << 21;
                } else {
                    result |= (tmp & 0x7f) << 21;
                    result |= (tmp = bb.readByte()) << 28;
                    while (tmp < 0) {
                        // We get into this loop only in the case of overflow.
                        // By doing this, we can call getVarInt() instead of
                        // getVarLong() when we only need an int.
                        tmp = bb.readByte();
                    }
                }
            }
        }
        return result;
    }

    public static @NotNull String getString(ByteBuf bb) {
        int length = getVarInt(bb);
        byte[] bytes = new byte[length];
        bb.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public static @Nullable String getStringOptional(ByteBuf bb) {
        boolean present = bb.readByte() == 1;
        if (!present) return null;
        return getString(bb);
    }
    public static String[] getStringList(ByteBuf bb, int maxSize) {
        int length = getVarInt(bb);
//        assertThat(length <= maxSize, "String list too big");
        String[] strings = new String[length];
        for (int i = 0; i < length; i++) {
            strings[i] = getString(bb);
        }
        return strings;
    }
    public static byte[] getLightData(ByteBuf bb) {
        byte[] bytes = new byte[2048];
        bb.readBytes(bytes);
        return bytes;
    }



    public static void writeBlockEntity(@NotNull ByteArrayDataOutput bb, @NotNull PolarChunk.BlockEntity blockEntity) {
        bb.writeInt(blockEntity.index());
        bb.write(blockEntity.id() == null ? 0 : 1);
        if (blockEntity.id() != null) {
            writeString(blockEntity.id(), bb);
        }

        bb.write(blockEntity.data() == null ? 0 : 1);
        if (blockEntity.data() != null) {
            try {
                NbtIo.writeAnyTag(blockEntity.data(), bb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void writeVarInt(int v, ByteArrayDataOutput bb) {
        while (true) {
            int bits = v & 0x7f;
            v >>>= 7;
            if (v == 0) {
                bb.write((byte) bits);
                return;
            }
            bb.write((byte) (bits | 0x80));
        }
    }

    public static void writeString(String s, ByteArrayDataOutput bb) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length, bb);
        bb.write(bytes);
    }

    public static void writeByteArray(byte[] bytes, ByteArrayDataOutput bb) {
        writeVarInt(bytes.length, bb);
        bb.write(bytes);
    }

    public static void writeLongArray(long[] longs, ByteArrayDataOutput bb) {
        writeVarInt(longs.length, bb);
        for (long aLong : longs) {
            bb.writeLong(aLong);
        }
    }

    public static void writeStringArray(String[] strings, ByteArrayDataOutput bb) {
        writeVarInt(strings.length, bb);
        for (String aString : strings) {
            writeString(aString, bb);
        }
    }

}
