package live.minehub.polarpaper.core.source;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

public class SlimeFilePolarSource implements PolarSource {

    private FilePolarSource realSource;
    private final FilePolarSource polarSource;
    public SlimeFilePolarSource(FilePolarSource realSource) {
        this.realSource = realSource;
        this.polarSource = new FilePolarSource(realSource.path().resolveSibling(realSource.path().getFileName().toString().replaceAll(".slime$", ".polar")));
    }

    @Override
    public ReadableByteChannel read() throws IOException {
        return this.realSource.read();
    }

    @Override
    public void save(MemorySegment segment) throws IOException {
        if (realSource != polarSource) Files.move(realSource.path(), realSource.path().resolveSibling(realSource.path().getFileName().toString() + ".bak"));

        this.realSource = polarSource;
        polarSource.save(segment);
    }

    @Override
    public long size() {
        return this.realSource.size();
    }

    @Override
    public void delete() throws Exception {
        this.realSource.delete();
    }
}
