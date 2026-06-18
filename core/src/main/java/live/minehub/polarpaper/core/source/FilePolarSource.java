package live.minehub.polarpaper.core.source;

import java.nio.file.Files;
import java.nio.file.Path;

public record FilePolarSource(Path path) implements PolarSource {
    @Override
    public byte[] readBytes() throws Exception {
        return Files.readAllBytes(this.path);
    }

    @Override
    public void saveBytes(byte[] data) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, data);
    }

    @Override
    public void delete() throws Exception {
        Files.deleteIfExists(path);
    }
}
