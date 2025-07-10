package live.minehub.polarpaper.source;

import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.PolarPaper;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface PolarSource {

    byte[] readBytes();

    void saveBytes(byte[] save);

    static @Nullable PolarSource fromConfig(String worldName, Config config) {
        switch (config.source().toLowerCase()) {
            case "file" -> {
                Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
                Path worldsFolder = pluginFolder.resolve("worlds");
                Path path = worldsFolder.resolve(worldName + ".polar");
                return new FilePolarSource(path);
            }
        }

        return null;
    }

}
