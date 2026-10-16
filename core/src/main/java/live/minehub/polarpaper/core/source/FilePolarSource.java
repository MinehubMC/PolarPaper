package live.minehub.polarpaper.core.source;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
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
    public void save(StreamWriter writer) throws IOException {
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".polar-save-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                 var output = new BufferedOutputStream(Channels.newOutputStream(channel))) {
                writer.write(output);
                output.flush();
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
