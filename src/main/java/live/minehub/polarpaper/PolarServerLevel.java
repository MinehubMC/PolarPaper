package live.minehub.polarpaper;

import io.papermc.paper.world.PaperWorldLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

public class PolarServerLevel extends ServerLevel {
    public PolarServerLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess levelStorage, WorldGenSettings worldGenSettings, ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner> customSpawners, boolean tickTime, ResourceKey<LevelStem> typeKey, World.Environment env, ChunkGenerator gen, BiomeProvider biomeProvider, SavedDataStorage savedDataStorage, PaperWorldLoader.LoadedWorldData loadedWorldData) {
        super(server, executor, levelStorage, worldGenSettings, dimension, levelStem, isDebug, biomeZoomSeed, customSpawners, tickTime, typeKey, env, gen, biomeProvider, savedDataStorage, loadedWorldData);
    }

    @Override
    public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
    }

    @Override
    public void saveIncrementally(boolean doFull) {
//        System.out.println("Save incrementally: " + doFull);
    }

}
