package live.minehub.polarpaper.core.generator;

import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.PolarSource;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public abstract class PolarGenerator extends ChunkGenerator {
    private Config config;
    private @Nullable PolarSource source;
    private final PolarWorldAccess worldAccess;

    public PolarGenerator(Config config, @Nullable PolarSource source, PolarWorldAccess worldAccess) {
        this.config = config;
        this.source = source;
        this.worldAccess = worldAccess;
    }

    public Config getConfig() {
        return this.config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public @Nullable PolarSource getSource() {
        return source;
    }

    public void setSource(@Nullable PolarSource source) {
        this.source = source;
    }

    public PolarWorldAccess getWorldAccess() {
        return this.worldAccess;
    }

    public abstract @Nullable PolarWorld getPolarWorld();

    public abstract Component getInfoComponent(World world);

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        Location loc = getConfig().spawn();
        loc.setWorld(world);
        return loc;
    }

    public static @Nullable PolarGenerator fromWorld(World world) {
        if (world == null) return null;
        ChunkGenerator generator = world.getGenerator();
        if (generator instanceof PolarGenerator polarGenerator) return polarGenerator;
        return null;
    }
}
