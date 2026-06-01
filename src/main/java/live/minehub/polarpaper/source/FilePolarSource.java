package live.minehub.polarpaper.source;

import live.minehub.polarpaper.PolarPaper;

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

    public static FilePolarSource defaultFolder(String worldName) {
        Path pluginFolder = PolarPaper.getPlugin().getDataPath();
        Path worldsFolder = pluginFolder.resolve("worlds");
        Path path = worldsFolder.resolve(worldName + ".polar");
        return new FilePolarSource(path);
    }
}
