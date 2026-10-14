package live.minehub.polarpaper.core.source;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public record FilePolarSource(Path path) implements PolarSource {
    @Override
    public ReadableByteChannel read() throws IOException {
        return FileChannel.open(path);
    }

    @Override
    public void save(MemorySegment segment) throws IOException {
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".polar-save-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                if (segment.byteSize() > 0) {
                    try (var arena = Arena.ofConfined()) {
                        var fileSegment = channel.map(FileChannel.MapMode.READ_WRITE, 0, segment.byteSize(), arena);
                        MemorySegment.copy(segment, 0, fileSegment, 0, segment.byteSize());
                        fileSegment.force();
                    }
                }
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public long size() {
        return path.toFile().length();
    }

    @Override
    public void delete() throws Exception {
        Files.deleteIfExists(path);
    }
}
