package live.minehub.polarpaper.paper_1_21_11;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.papermc.paper.world.PaperWorldLoader;
import live.minehub.polarpaper.core.NoSaveLevelCreator;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.util.TaskFutures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftGameRule;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.generator.CraftWorldInfo;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class NoSaveLevelCreatorImpl implements NoSaveLevelCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoSaveLevelCreatorImpl.class);

    @Override
    public CompletableFuture<@Nullable World> createLevel(Plugin plugin, WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();

        // Check if already existing
        World worldByKey = craftServer.getWorld(creator.key());
        if (worldByKey != null) {
            return null;
        }

        Preconditions.checkState(craftServer.getServer().getAllLevels().iterator().hasNext(), "Cannot create additional worlds on STARTUP");
        //Preconditions.checkState(!this.console.isIteratingOverLevels, "Cannot create a world while worlds are being ticked"); // Paper - Cat - Temp disable. We'll see how this goes.

        String name = creator.name();
        ChunkGenerator chunkGenerator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        File folder = new File(craftServer.getWorldContainer(), name);
        World world = craftServer.getWorld(name);

        // Paper start

        if (world != null) {
            throw new IllegalArgumentException("Cannot create a world with key " + creator.key() + " and name " + name + " one (or both) already match a world that exists");
        }
        // Paper end

        if (folder.exists()) {
            Preconditions.checkArgument(folder.isDirectory(), "File (%s) exists and isn't a folder", name);
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

        LevelStorageSource.LevelStorageAccess levelStorageAccess;
        try {
            Path pluginFolder = plugin.getDataPath();
            Path tempFolder = pluginFolder.resolve("temp");

            levelStorageAccess = LevelStorageSource.createDefault(tempFolder).validateAndCreateAccess(name, actualDimension);
        } catch (IOException | ContentValidationException ex) {
            throw new RuntimeException(ex);
        }

        boolean hardcore = creator.hardcore();

        PrimaryLevelData primaryLevelData;
        WorldLoader.DataLoadContext context = craftServer.getServer().worldLoaderContext;
        RegistryAccess.Frozen registryAccess = context.datapackDimensions();
        Registry<LevelStem> contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        Dynamic<?> dataTag = PaperWorldLoader.getLevelData(levelStorageAccess).dataTag();
        if (dataTag != null) {
            LevelDataAndDimensions levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dataTag, context.dataConfiguration(), contextLevelStemRegistry, context.datapackWorldgen()
            );
            primaryLevelData = (PrimaryLevelData) levelDataAndDimensions.worldData();
            registryAccess = levelDataAndDimensions.dimensions().dimensionsRegistryAccess();
        } else {
            LevelSettings levelSettings;
            WorldOptions worldOptions = new WorldOptions(creator.seed(), creator.generateStructures(), creator.bonusChest());
            WorldDimensions worldDimensions;

            net.minecraft.world.Difficulty minecraftDifficulty;

            // Can be used the id but method byId is deprecated
            try {
                minecraftDifficulty = net.minecraft.world.Difficulty.valueOf(difficulty.name());
            } catch (IllegalArgumentException e) { // This error should never happen
                LOGGER.warn("Difficulty {} not found, defaulting to NORMAL", difficulty.name());
                minecraftDifficulty = net.minecraft.world.Difficulty.NORMAL;
            }

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

            // fix for log "No key layers in MapLike[{}]"
            JsonObject defaultGenSettings = new JsonObject();
            defaultGenSettings.add("layers", new JsonArray());
            defaultGenSettings.add("biome", new JsonPrimitive("minecraft:plains"));
            DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(creator.generatorSettings().isEmpty() ? defaultGenSettings : GsonHelper.parse(creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));
            levelSettings = new LevelSettings(
                    name,
                    GameType.byId(craftServer.getDefaultGameMode().getValue()),
                    hardcore, minecraftDifficulty,
                    false,
                    nmsGameRules,
                    context.dataConfiguration()
            );
            worldDimensions = properties.create(context.datapackWorldgen());

            WorldDimensions.Complete complete = worldDimensions.bake(contextLevelStemRegistry);
            Lifecycle lifecycle = complete.lifecycle().add(context.datapackWorldgen().allRegistriesLifecycle());

            primaryLevelData = new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), lifecycle);
            registryAccess = complete.dimensionsRegistryAccess();
        }

        contextLevelStemRegistry = registryAccess.lookupOrThrow(Registries.LEVEL_STEM);
        primaryLevelData.customDimensions = contextLevelStemRegistry;
        primaryLevelData.checkName(name);
        primaryLevelData.setModdedInfo(craftServer.getServer().getServerModName(), craftServer.getServer().getModdedStatus().shouldReportAsModified());

        if (craftServer.getServer().options.has("forceUpgrade")) {
            Main.forceUpgrade(levelStorageAccess, primaryLevelData, DataFixers.getDataFixer(), craftServer.getServer().options.has("eraseCache"), () -> true, registryAccess, craftServer.getServer().options.has("recreateRegionFiles"));
        }

        long i = BiomeManager.obfuscateSeed(primaryLevelData.worldGenOptions().seed());
        List<CustomSpawner> list = ImmutableList.of(
//                new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(primaryLevelData)
        );
        LevelStem customStem = contextLevelStemRegistry.getValue(actualDimension);

        WorldInfo worldInfo = new CraftWorldInfo(primaryLevelData, levelStorageAccess, creator.environment(), customStem.type().value(), customStem.generator(), craftServer.getHandle().getServer().registryAccess()); // Paper - Expose vanilla BiomeProvider from WorldInfo
        if (biomeProvider == null && chunkGenerator != null) {
            biomeProvider = chunkGenerator.getDefaultBiomeProvider(worldInfo);
        }

        ResourceKey<Level> dimensionKey;
        String levelName = craftServer.getServer().getProperties().levelName;
        if (name.equals(levelName + "_nether")) {
            dimensionKey = Level.NETHER;
        } else if (name.equals(levelName + "_the_end")) {
            dimensionKey = Level.END;
        } else {
            dimensionKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(creator.key().namespace(), creator.key().value()));
        }

//        if (!(craftServer.getWorlds().containsKey(name.toLowerCase(Locale.ROOT)))) {
//            return null;
//        }

        ChunkGenerator finalChunkGenerator = chunkGenerator;
        BiomeProvider finalBiomeProvider = biomeProvider;
        Supplier<World> initSupplier = () -> {
            ServerLevel serverLevel = new NoSaveLevel(
                    craftServer.getServer(),
                    craftServer.getServer().executor,
                    levelStorageAccess,
                    primaryLevelData,
                    dimensionKey,
                    customStem,
                    primaryLevelData.isDebugWorld(),
                    i,
                    creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(),
                    true,
                    craftServer.getServer().overworld().getRandomSequences(),
                    creator.environment(),
                    finalChunkGenerator, finalBiomeProvider
            );

            serverLevel.setDayTime(time);

            craftServer.getServer().addLevel(serverLevel); // Paper - Put world into worldlist before initing the world; move up
            craftServer.getServer().initWorld(serverLevel, primaryLevelData, primaryLevelData.worldGenOptions());
            // Paper - Put world into worldlist before initing the world; move up

            serverLevel.setSpawnSettings(serverLevel.isSpawningMonsters());

            serverLevel.serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), new BlockPos(spawnPos.getBlockX(), spawnPos.getBlockY(), spawnPos.getBlockZ()), spawnPos.getYaw(), spawnPos.getPitch()));

            craftServer.getServer().updateEffectiveRespawnData();

            new WorldLoadEvent(serverLevel.getWorld()).callEvent();

            return serverLevel.getWorld();
        };

        boolean async = !craftServer.isPrimaryThread();
        if (async) {
            return TaskFutures.runSync(plugin, initSupplier);
        } else {
            return CompletableFuture.completedFuture(initSupplier.get());
        }
    }

    private static class NoSaveLevel extends ServerLevel {
        public NoSaveLevel(MinecraftServer server, Executor dispatcher, LevelStorageSource.LevelStorageAccess levelStorageAccess, PrimaryLevelData serverLevelData, ResourceKey<Level> dimension, LevelStem levelStem, boolean isDebug, long biomeZoomSeed, List<CustomSpawner> customSpawners, boolean tickTime, @org.jspecify.annotations.Nullable RandomSequences randomSequences, World.Environment env, ChunkGenerator gen, BiomeProvider biomeProvider) {
            super(server, dispatcher, levelStorageAccess, serverLevelData, dimension, levelStem, isDebug, biomeZoomSeed, customSpawners, tickTime, randomSequences, env, gen, biomeProvider);
        }

        @Override
        public void save(@Nullable ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) {
        }

        @Override
        public void saveIncrementally(boolean doFull) {
        }
    }

}
