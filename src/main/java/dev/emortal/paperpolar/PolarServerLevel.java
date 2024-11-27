package dev.emortal.paperpolar;

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
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

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

    }

    @Override
    public void saveIncrementally(boolean doFull) {

    }

    @Override
    public boolean noSave() {
        return true;
    }
}
