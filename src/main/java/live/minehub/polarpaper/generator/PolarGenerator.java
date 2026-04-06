package live.minehub.polarpaper.generator;

import live.minehub.polarpaper.Config;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.PolarWorldAccess;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public abstract class PolarGenerator extends ChunkGenerator {
    private Config config;
    private final PolarWorldAccess worldAccess;

    public PolarGenerator(Config config, PolarWorldAccess worldAccess) {
        this.config = config;
        this.worldAccess = worldAccess;
    }

    public Config getConfig() {
        return this.config;
    }

    public void setConfig(Config config) {
        this.config = config;
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
        if (generator instanceof PolarStreamingGenerator voidGen) return voidGen;
        return null;
    }
}
