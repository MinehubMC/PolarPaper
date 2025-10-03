package live.minehub.polarpaper;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

public class PolarServerLevel extends ServerLevel {
    public PolarServerLevel(MinecraftServer server, Executor dispatcher, LevelStorageSource.LevelStorageAccess storageSource, PrimaryLevelData levelData, ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner> customSpawners, boolean tickTime, @Nullable RandomSequences randomSequences, World.Environment env, ChunkGenerator gen, BiomeProvider biomeProvider) {
        super(server, dispatcher, storageSource, levelData, dimension, levelStem, isDebug, biomeZoomSeed, customSpawners, tickTime, randomSequences, env, gen, biomeProvider);
    }

    @Override
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled) {
    }

    @Override
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
    }

    @Override
    public void saveIncrementally(boolean doFull) {
//        System.out.println("Save incrementally: " + doFull);
    }

    @Override
    public boolean noSave() {
        return true;
    }

}
