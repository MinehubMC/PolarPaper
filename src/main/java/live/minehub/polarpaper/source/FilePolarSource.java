package live.minehub.polarpaper.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilePolarSource implements PolarSource {

    private final Path path;

    public FilePolarSource(Path path) {
        this.path = path;
    }

    @Override
    public byte[] readBytes() {
        try {
            return Files.readAllBytes(this.path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveBytes(byte[] data) {
        try {
            Files.write(path, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
