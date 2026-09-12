package live.minehub.polarpaper.core.source;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public record FilePolarSource(Path path) implements PolarSource {
    @Override
    public ReadableByteChannel read() throws IOException {
        return FileChannel.open(path);
    }

    @Override
    public void save(MemorySegment segment) throws IOException {
        try (var file = new RandomAccessFile(path.toFile(), "rw");
             var channel = file.getChannel();
             var arena = Arena.ofConfined()) {
            channel.truncate(segment.byteSize());
            var fileSegment = channel.map(FileChannel.MapMode.READ_WRITE, 0, segment.byteSize(), arena);
            MemorySegment.copy(segment, 0, fileSegment, 0, segment.byteSize());
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
