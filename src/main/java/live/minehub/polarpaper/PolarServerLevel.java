package live.minehub.polarpaper;

import live.minehub.polarpaper.source.FilePolarSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

public class PolarServerLevel extends ServerLevel {
    public PolarServerLevel(MinecraftServer minecraftserver, Executor executor, LevelStorageSource.LevelStorageAccess convertable_conversionsession, PrimaryLevelData iworlddataserver, ResourceKey<Level> resourcekey, LevelStem worlddimension, ChunkProgressListener worldloadlistener, boolean flag, long i, List<CustomSpawner> list, boolean flag1, @Nullable RandomSequences randomsequences, World.Environment env, ChunkGenerator gen, BiomeProvider biomeProvider) {
        super(minecraftserver, executor, convertable_conversionsession, iworlddataserver, resourcekey, worlddimension, worldloadlistener, flag, i, list, flag1, randomsequences, env, gen, biomeProvider);
    }

    @Override
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled) {

    }

    @Override
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
        if (savingDisabled) return;
        save(close);
    }

    @Override
    public void saveIncrementally(boolean doFull) {

    }

    @Override
    public boolean noSave() {
        return true;
    }

    private void save(boolean serverStopping) {
        World world = Bukkit.getWorld(this.uuid);
        PolarWorld polarWorld = PolarWorld.fromWorld(world);
        if (polarWorld == null) return;
        PolarGenerator generator = PolarGenerator.fromWorld(world);
        long before = System.nanoTime();

        if (!serverStopping) {
            if (!generator.getConfig().autoSave()) {
                PolarPaper.getPlugin().getLogger().info(String.format("Not saving '%s' as it has autosaving disabled", world.getName()));
                return;
            }

            Polar.saveWorldConfigSource(world).thenAccept(successful -> {
                if (successful) {
                    int ms = (int) ((System.nanoTime() - before) / 1_000_000);
                    PolarPaper.getPlugin().getLogger().info(String.format("Saved '%s' in %sms", world.getName(), ms));
                } else {
                    PolarPaper.getPlugin().getLogger().warning(String.format("Something went wrong while trying to save '%s'", world.getName()));
                }
            });
        } else {
            if (!generator.getConfig().saveOnStop()) {
                PolarPaper.getPlugin().getLogger().info(String.format("Not saving '%s' as it has save on stop disabled", world.getName()));
                return;
            }

            Path pluginFolder = Path.of(PolarPaper.getPlugin().getDataFolder().getAbsolutePath());
            Path worldsFolder = pluginFolder.resolve("worlds");
            Path path = worldsFolder.resolve(world.getName() + ".polar");
            Polar.saveWorldSync(world, polarWorld, PolarWorldAccess.POLAR_PAPER_FEATURES, new FilePolarSource(path), ChunkSelector.all(), 0, 0);
            int ms = (int) ((System.nanoTime() - before) / 1_000_000);
            PolarPaper.getPlugin().getLogger().info(String.format("Saved '%s' in %sms", world.getName(), ms));
        }
    }
}
