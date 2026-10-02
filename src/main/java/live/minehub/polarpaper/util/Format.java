package live.minehub.polarpaper.util;

import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.generator.PolarStreamLoader;
import live.minehub.polarpaper.core.generator.SlimeStreamLoader;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.world.PolarConstants;
import live.minehub.polarpaper.core.world.PolarDataConverter;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public interface Format {

    Format POLAR_FORMAT = new Format() {
        @Override
        public PolarGenerator createGenerator(@NotNull Config config, @Nullable PolarSource source, @NotNull PolarWorldAccess worldAccess, @NotNull PolarDataConverter dataConverter, @NotNull EntitySerializer entitySerializer) {
            return new PolarStreamLoader(config, source, worldAccess, dataConverter);
        }

        @Override
        public boolean checkMagic(MemorySegmentReader reader) {
            return reader.readInt() == PolarConstants.POLAR_MAGIC;
        }

        @Override
        public String getFileExtension() {
            return "polar";
        }
    };

    Format SLIME_FORMAT = new Format() {
        @Override
        public PolarGenerator createGenerator(@NotNull Config config, @Nullable PolarSource source, @NotNull PolarWorldAccess worldAccess, @NotNull PolarDataConverter dataConverter, @NotNull EntitySerializer entitySerializer) {
            return new SlimeStreamLoader(config, source, worldAccess, dataConverter, entitySerializer);
        }

        @Override
        public boolean checkMagic(MemorySegmentReader reader) {
            return reader.readUnsignedShort() == PolarConstants.SLIME_MAGIC;
        }

        @Override
        public String getFileExtension() {
            return "slime";
        }
    };

    /**
     * Tries to find a file with any supported file extension
     */
    static @Nullable Path findSupported(Path path) {
        if (Files.exists(path)) return path;

        String stripped = stripExtension(path.getFileName().toString());

        for (Format format : Format.Registry.getFormats()) {
            Path newPath = path.resolveSibling(stripped + "." + format.getFileExtension());
            boolean exists = Files.exists(newPath);
            if (exists) return newPath;
        }
        return null;
    }

    static boolean isSupported(Path path) {
        String fileName = path.getFileName().toString();
        for (Format format : Registry.getFormats()) {
            if (fileName.endsWith(format.getFileExtension())) return true;
        }
        return false;
    }

    public static String stripExtension(@NotNull String str) {
        int pos = str.lastIndexOf(".");
        if (pos == -1) return str;
        return str.substring(0, pos);
    }

    static @Nullable Format detectFormat(@NotNull PolarSource source) {
        int fileSize = 4; // only need to read the first integer/short to check the magic

        try (Arena srcArena = Arena.ofConfined();
             ReadableByteChannel channel = source.read()) {

            final MemorySegment src;
            if (channel instanceof FileChannel fileChannel) {
                src = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileSize, srcArena);
            } else {
                final MemorySegment segment = srcArena.allocate(fileSize);
                long offset = 0L; // readFully, but for large files
                while (offset < fileSize) {
                    long n = channel.read(segment.asSlice(offset, fileSize - offset).asByteBuffer());
                    if (n < 0) {
                        throw new EOFException("Unexpected EOF: expected " + fileSize + " bytes, got " + offset);
                    }
                    offset += n;
                }
                src = segment.asReadOnly();
            }

            MemorySegmentReader reader = new MemorySegmentReader(src);

            for (Format format : Format.Registry.getFormats()) {
                reader.setOffset(0);
                boolean correctMagic = format.checkMagic(reader);
                if (correctMagic) return format;
            }

            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    PolarGenerator createGenerator(@NotNull Config config, @Nullable PolarSource source,
                                   @NotNull PolarWorldAccess worldAccess,
                                   @NotNull PolarDataConverter dataConverter,
                                   @NotNull EntitySerializer entitySerializer);

    boolean checkMagic(MemorySegmentReader reader);

    String getFileExtension();

    class Registry {
        private static final Set<Format> FORMATS = new HashSet<>();

        static {
            registerFormat(POLAR_FORMAT);
            registerFormat(SLIME_FORMAT);
        }

        public static void registerFormat(Format format) {
            FORMATS.add(format);
        }

        public static Set<Format> getFormats() {
            return FORMATS;
        }
    }

}
