package live.minehub.polarpaper;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class PolarGenerator extends ChunkGenerator {

    private Config config;
    private final PolarWorldAccess worldAccess;
    private Short version = null;
    private Integer dataVersion = null;
    private byte[] userData = new byte[0];
    public PolarGenerator(Config config, PolarWorldAccess worldAccess) {
        this.config = config;
        this.worldAccess = worldAccess;
    }

    public PolarGenerator(Config config) {
        this(config, PolarWorldAccess.POLAR_PAPER_FEATURES);
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public byte[] getUserData() {
        return userData;
    }

    public void setUserData(byte[] userData) {
        this.userData = userData;
    }

    public void setVersion(Short version) {
        this.version = version;
    }

    public Short getVersion() {
        return version;
    }

    public Integer getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(Integer dataVersion) {
        this.dataVersion = dataVersion;
    }

    public PolarWorldAccess getWorldAccess() {
        return worldAccess;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        Location loc = config.spawn();
        loc.setWorld(world);
        return loc;
    }

    public static @Nullable PolarGenerator fromWorld(World world) {
        ChunkGenerator generator = world.getGenerator();
        if (!(generator instanceof PolarGenerator polarGenerator)) return null;
        return polarGenerator;
    }

}
