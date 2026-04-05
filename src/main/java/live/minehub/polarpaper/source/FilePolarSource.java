package live.minehub.polarpaper.source;

import live.minehub.polarpaper.PolarPaper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record FilePolarSource(Path path) implements PolarSource {
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

    public static FilePolarSource defaultFolder(String worldName) {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");
        return new FilePolarSource(path);
    }
}
