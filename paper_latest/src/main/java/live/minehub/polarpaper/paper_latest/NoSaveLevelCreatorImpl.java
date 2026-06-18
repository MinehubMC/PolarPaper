package live.minehub.polarpaper.paper_latest;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.papermc.paper.world.PaperWorldLoader;
import live.minehub.polarpaper.core.NoSaveLevelCreator;
import live.minehub.polarpaper.core.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.*;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftGameRule;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class NoSaveLevelCreatorImpl implements NoSaveLevelCreator {
    @Override
    public CompletableFuture<@Nullable World> createLevel(Plugin plugin, WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();

        // Check if already existing
        if (craftServer.getWorld(creator.key()) != null) {
            return CompletableFuture.completedFuture(null);
        }

        Preconditions.checkState(craftServer.getServer().getAllLevels().iterator().hasNext(), "Cannot create additional worlds on STARTUP");
        //Preconditions.checkState(!this.console.isIteratingOverLevels, "Cannot create a world while worlds are being ticked"); // Paper - Cat - Temp disable. We'll see how this goes.

        String name = creator.name();
        ChunkGenerator chunkGenerator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        World world = craftServer.getWorld(name);

        // Paper start
        World worldByKey = craftServer.getWorld(creator.key());
        if (world != null || worldByKey != null) {
            if (world == worldByKey) {
                return CompletableFuture.completedFuture(world);
            }
            throw new IllegalArgumentException("Cannot create a world with key " + creator.key() + " and name " + name + " one (or both) already match a world that exists");
        }

        if (chunkGenerator == null) {
            chunkGenerator = craftServer.getGenerator(name);
        }

        if (biomeProvider == null) {
            biomeProvider = craftServer.getBiomeProvider(name);
        }

        ResourceKey<LevelStem> actualDimension = switch (creator.environment()) {
            case NORMAL -> LevelStem.OVERWORLD;
            case NETHER -> LevelStem.NETHER;
            case THE_END -> LevelStem.END;
            default -> throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        };

        final ResourceKey<net.minecraft.world.level.Level> dimensionKey = CraftNamespacedKey.toResourceKey(Registries.DIMENSION, creator.key());
        WorldLoader.DataLoadContext context = craftServer.getServer().worldLoaderContext;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        net.minecraft.core.Registry<LevelStem> contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        final LevelStem configuredStem = craftServer.getServer().registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(actualDimension);
        if (configuredStem == null) {
            throw new IllegalStateException("Missing configured level stem " + actualDimension);
        }

        PaperWorldLoader.LoadedWorldData loadedWorldData = PaperWorldLoader.loadWorldData(
                craftServer.getServer(),
                dimensionKey,
                name
        );
        final PrimaryLevelData primaryLevelData = (PrimaryLevelData) craftServer.getServer().getWorldData();

        WorldOptions worldOptions = new WorldOptions(creator.seed(), creator.generateStructures(), creator.bonusChest());

        // fix for log "No key layers in MapLike[{}]"
        JsonObject defaultGenSettings = new JsonObject();
        defaultGenSettings.add("layers", new JsonArray());
        defaultGenSettings.add("biome", new JsonPrimitive("minecraft:plains"));
        DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(creator.generatorSettings().isEmpty() ? defaultGenSettings : GsonHelper.parse(creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));
        WorldDimensions worldDimensions = properties.create(context.datapackWorldgen());

        WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
        if (complete.dimensions().getValue(actualDimension) == null) {
            throw new IllegalStateException("Missing generated level stem " + actualDimension + " for world " + name);
        }

        net.minecraft.world.Difficulty minecraftDifficulty;
        try {
            minecraftDifficulty = net.minecraft.world.Difficulty.valueOf(difficulty.name());
        } catch (IllegalArgumentException _) { // This error should never happen
            minecraftDifficulty = net.minecraft.world.Difficulty.NORMAL;
        }

        WorldGenSettings worldGenSettings = new WorldGenSettings(worldOptions, worldDimensions);
        registryAccess = complete.dimensionsRegistryAccess();
        loadedWorldData.levelOverrides().setHardcore(creator.hardcore());
        loadedWorldData.levelOverrides().setDifficulty(minecraftDifficulty);
        loadedWorldData = new PaperWorldLoader.LoadedWorldData(
                loadedWorldData.bukkitName(),
                loadedWorldData.uuid(),
                loadedWorldData.pdc(),
                loadedWorldData.levelOverrides()
        );

        contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);

        net.minecraft.world.level.gamerules.GameRules nmsGameRules = new net.minecraft.world.level.gamerules.GameRules(context.dataConfiguration().enabledFeatures());

        for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            if (key == null) {
                if (Config.getDefaultGamerules().containsKey(entry.getKey())) continue; // is a custom gamerule, ignore
                LOGGER.warn("Invalid gamerule: {}", entry.getKey());
                continue;
            }
            GameRule<?> rule = org.bukkit.Registry.GAME_RULE.get(key);
            if (rule == null) {
                LOGGER.warn("Invalid gamerule: {}", key.asMinimalString());
                continue;
            }
            net.minecraft.world.level.gamerules.GameRule<Object> nmsRule = ((CraftGameRule<Object>)rule).getHandle();

            nmsGameRules.set(nmsRule, entry.getValue(), null);
        }


        long biomeZoomSeed = BiomeManager.obfuscateSeed(worldGenSettings.options().seed());
        LevelStem customStem = worldGenSettings.dimensions().get(actualDimension).orElse(null);
        if (customStem == null) {
            customStem = contextLevelStemRegistry.getValue(actualDimension);
        }
        if (customStem == null) {
            throw new IllegalStateException("Missing level stem for world " + name + " using key " + actualDimension);
        }

        final SavedDataStorage savedDataStorage = new SavedDataStorage(craftServer.getServer().storageSource.getDimensionPath(dimensionKey).resolve(LevelResource.DATA.id()), craftServer.getServer().getFixerUpper(), craftServer.getServer().registryAccess());
        savedDataStorage.set(WorldGenSettings.TYPE, new WorldGenSettings(worldGenSettings.options(), worldGenSettings.dimensions()));
        savedDataStorage.set(GameRuleMap.TYPE, nmsGameRules.rules);
        List<CustomSpawner> list = ImmutableList.of(
                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(savedDataStorage)
        );

        LevelStem finalCustomStem = customStem;
        ChunkGenerator finalChunkGenerator = chunkGenerator;
        BiomeProvider finalBiomeProvider = biomeProvider;
        PaperWorldLoader.LoadedWorldData finalLoadedWorldData = loadedWorldData;
        Supplier<World> initSupplier = () -> {
            ServerLevel serverLevel = new NoSaveLevel(
                    craftServer.getServer(),
                    Util.backgroundExecutor(),
                    craftServer.getServer().storageSource,
                    worldGenSettings,
                    dimensionKey,
                    finalCustomStem,
                    primaryLevelData.isDebugWorld(),
                    biomeZoomSeed,
                    creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(),
                    true,
                    actualDimension,
                    creator.environment(),
                    finalChunkGenerator,
                    finalBiomeProvider,
                    savedDataStorage,
                    finalLoadedWorldData
            );

            serverLevel.dimensionType().defaultClock().ifPresent(clock -> {
                serverLevel.clockManager().setTotalTicks(clock, time);
            });

            craftServer.getServer().addLevel(serverLevel); // Paper - Put world into worldlist before initing the world; move up
            craftServer.getServer().initWorld(serverLevel, null);
            // Paper - Put world into worldlist before initing the world; move up

            craftServer.getServer().prepareLevel(serverLevel);

            serverLevel.serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), new BlockPos(spawnPos.getBlockX(), spawnPos.getBlockY(), spawnPos.getBlockZ()), spawnPos.getYaw(), spawnPos.getPitch()));

            craftServer.getServer().updateEffectiveRespawnData();

            return serverLevel.getWorld();
        };

        boolean async = !craftServer.isPrimaryThread();
        if (async) {
            return live.minehub.polarpaper.core.util.TaskFutures.run(plugin, initSupplier);
        } else {
            return CompletableFuture.completedFuture(initSupplier.get());
        }
    }

    private class NoSaveLevel extends ServerLevel {
        protected NoSaveLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess levelStorage, WorldGenSettings worldGenSettings, ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner> customSpawners, boolean tickTime, ResourceKey<LevelStem> typeKey, World.Environment env, ChunkGenerator gen, BiomeProvider biomeProvider, SavedDataStorage savedDataStorage, PaperWorldLoader.LoadedWorldData loadedWorldData) {
            super(server, executor, levelStorage, worldGenSettings, dimension, levelStem, isDebug, biomeZoomSeed, customSpawners, tickTime, typeKey, env, gen, biomeProvider, savedDataStorage, loadedWorldData);
        }

        @Override
        public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
        }

        @Override
        public void saveIncrementally(boolean doFull) {
        }
    }

}
