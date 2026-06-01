package live.minehub.polarpaper;

import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.generator.PolarStreamingGenerator;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.util.CoordConversion;
import live.minehub.polarpaper.util.PolarConstants;
import live.minehub.polarpaper.util.TaskFutures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

import static live.minehub.polarpaper.util.ByteArrayUtil.getVarInt;

public class PolarStreamLoader {

    public static CompletableFuture<Void> stream(PolarSource source, World world, @NotNull PolarWorldAccess worldAccess) throws IOException {
        try {
            return stream(source.readBytes(), world, worldAccess);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static CompletableFuture<Void> stream(PolarSource source, World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess) throws IOException {
        try {
            return stream(source.readBytes(), world, dataConverter, worldAccess);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static CompletableFuture<Void> stream(byte @NotNull [] data, World world, @NotNull PolarWorldAccess worldAccess) {
        return stream(data, world, PolarDataConverter.DEFAULT, worldAccess);
    }

    public static CompletableFuture<Void> stream(byte @NotNull [] data, World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess) {
        ByteBuf bb = Unpooled.wrappedBuffer(data);

        int magic = bb.readInt();
        assertThat(magic == PolarConstants.MAGIC_NUMBER, "Invalid magic number");

        short version = bb.readShort();
        PolarReader.validateVersion(version);

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
        if (polarGenerator == null) return CompletableFuture.completedFuture(null);
        if (!(polarGenerator instanceof PolarStreamingGenerator voidGenerator)) return CompletableFuture.completedFuture(null);
        voidGenerator.setVersion(version);

        int dataVersion = version >= PolarConstants.VERSION_DATA_CONVERTER
                ? getVarInt(bb)
                : dataConverter.defaultDataVersion();

        voidGenerator.setDataVersion(dataVersion);

        byte compressionByte = bb.readByte();
        PolarWorld.CompressionType compression = PolarWorld.CompressionType.fromId(compressionByte);
        assertThat(compression != null, "Invalid compression type");

        int compressedDataLength = getVarInt(bb);

        // Replace the buffer with a "decompressed" version.
        ByteBuf uncompressed = PolarReader.decompressBuffer(bb, compression, compressedDataLength);

        byte minSection = uncompressed.readByte();
        byte maxSection = uncompressed.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        // User (world) data
        byte[] userData = new byte[0];
        if (version > PolarConstants.VERSION_WORLD_USERDATA) {
            int userDataLength = getVarInt(uncompressed);
            byte[] bytes = new byte[userDataLength];
            uncompressed.readBytes(bytes);
            userData = bytes;
        }

        voidGenerator.setUserData(userData);

        int chunkCount = getVarInt(uncompressed);
        CompletableFuture<Void>[] futures = new CompletableFuture[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            try {
                futures[i] = readChunk(world, dataConverter, worldAccess, version, dataVersion, uncompressed, maxSection - minSection + 1);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        return CompletableFuture.allOf(futures);
    }

    private static CompletableFuture<Void> readChunk(World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess, short version, int dataVersion, @NotNull ByteBuf bb, int sectionCount) {
        var chunkX = getVarInt(bb);
        var chunkZ = getVarInt(bb);

        CraftWorld craftWorld = (CraftWorld) world;
        ServerLevel serverLevel = craftWorld.getHandle();

        SWMRNibbleArray[] blockNibbles = new SWMRNibbleArray[sectionCount + 2]; // light includes extra top and bottom section
        SWMRNibbleArray[] skyNibbles = new SWMRNibbleArray[sectionCount + 2];
        blockNibbles[0] = new SWMRNibbleArray();
        blockNibbles[sectionCount + 1] = new SWMRNibbleArray();
        skyNibbles[0] = new SWMRNibbleArray();
        skyNibbles[sectionCount + 1] = new SWMRNibbleArray();
        boolean lightPresent = false;
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            PolarSection polarSection = PolarReader.readSection(dataConverter, version, dataVersion, bb);
            if (!lightPresent && (polarSection.skyLightContent() != PolarSection.LightContent.MISSING || polarSection.blockLightContent() != PolarSection.LightContent.MISSING)) lightPresent = true;

            try {
                LevelChunkSection section = polarSection.createLevelChunkSection(serverLevel.registryAccess());
                levelChunkSections[i] = section;
                skyNibbles[i + 1] = new SWMRNibbleArray(polarSection.skyLight());
                blockNibbles[i + 1] = new SWMRNibbleArray(polarSection.blockLight());
            } catch (Exception e) {
                PolarPaper.logger().severe("Failed to load chunk at " + chunkX + " " + chunkZ + " in " + world.getKey());
                throw e;
            }

        }

        NoUnloadLevelChunk newLevelChunk = new NoUnloadLevelChunk(serverLevel, new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, levelChunkSections, null, null);

        newLevelChunk.tryMarkSaved();
//        newLevelChunk.setLogUnsaved(true);

        int blockEntityCount = getVarInt(bb);
        for (int i = 0; i < blockEntityCount; i++) {
            PolarChunk.BlockEntity polarBlockEntity = PolarReader.readBlockEntity(dataConverter, dataVersion, bb);
            addBlockEntity(polarBlockEntity, newLevelChunk);
        }

        var heightmaps = PolarReader.readHeightmaps(bb);

        // Objects
        int userDataLength = getVarInt(bb);
        byte[] userData = new byte[userDataLength];
        bb.readBytes(userData);

        boolean finalLightPresent = lightPresent;

        return TaskFutures.run(() -> {
            insertChunk(serverLevel, newLevelChunk);
            worldAccess.loadChunkData(world, newLevelChunk, userData);

            if (finalLightPresent) {
                Boolean[] emptinessMap = StarLightEngine.getEmptySectionsForChunk(newLevelChunk);
                boolean[] emptinessMapPrim = new boolean[emptinessMap.length];
                for (int i = 0; i < emptinessMap.length; i++) {
                    Boolean bool = emptinessMap[i];
                    emptinessMapPrim[i] = bool != null && bool;
                }
                newLevelChunk.starlight$setBlockEmptinessMap(emptinessMapPrim);
                newLevelChunk.starlight$setSkyEmptinessMap(emptinessMapPrim);
                newLevelChunk.starlight$setSkyNibbles(skyNibbles);
                newLevelChunk.starlight$setBlockNibbles(blockNibbles);
            } else {
                lightChunk(serverLevel, newLevelChunk);
            }
            newLevelChunk.setLightCorrect(true);
            return null;
        });
    }

    protected static void insertChunk(ServerLevel serverLevel, NoUnloadLevelChunk newLevelChunk) {

        int chunkX = newLevelChunk.locX;
        int chunkZ = newLevelChunk.locZ;
        ChunkTaskScheduler chunkTaskScheduler = serverLevel.moonrise$getChunkTaskScheduler();
        ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        // Begin reflection hell :D
        try {
            ReentrantAreaLock.Node lock1 = chunkTaskScheduler.schedulingLockArea.lock(chunkX, chunkZ);
            ReentrantAreaLock.Node lock = chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ);
            Method getOrCreateChunkHolderMethod = chunkHolderManager.getClass().getDeclaredMethod("getOrCreateChunkHolder", int.class, int.class);
            getOrCreateChunkHolderMethod.setAccessible(true);
            NewChunkHolder newChunkHolder = (NewChunkHolder) getOrCreateChunkHolderMethod.invoke(chunkHolderManager, chunkX, chunkZ);
            chunkHolderManager.ticketLockArea.unlock(lock);
            chunkTaskScheduler.schedulingLockArea.unlock(lock1);

            newLevelChunk.needsDecoration = false;
            Field currentChunkField = newChunkHolder.getClass().getDeclaredField("currentChunk");
            currentChunkField.setAccessible(true);
            currentChunkField.set(newChunkHolder, newLevelChunk);
            newLevelChunk.moonrise$setChunkHolder(newChunkHolder);

            Field currentGenStatusField = NewChunkHolder.class.getDeclaredField("currentGenStatus");
            currentGenStatusField.setAccessible(true);
            currentGenStatusField.set(newChunkHolder, ChunkStatus.FULL);

            Field vhField = NewChunkHolder.class.getDeclaredField("CHUNK_COMPLETION_ARRAY_HANDLE");
            vhField.setAccessible(true);
            VarHandle vh = (VarHandle) vhField.get(null);

            Field allStatusesField = NewChunkHolder.class.getDeclaredField("ALL_STATUSES");
            allStatusesField.setAccessible(true);
            ChunkStatus[] allStatuses = (ChunkStatus[]) allStatusesField.get(null);

            Field chunkCompletionsField = NewChunkHolder.class.getDeclaredField("chunkCompletions");
            chunkCompletionsField.setAccessible(true);
            Object[] chunkCompletions = (Object[]) chunkCompletionsField.get(newChunkHolder);

            Field loadedTicketLevelField = LevelChunk.class.getDeclaredField("loadedTicketLevel");
            loadedTicketLevelField.setAccessible(true);
            loadedTicketLevelField.set(newLevelChunk, true);

            // Populate every status up to and including FULL
            // This mirrors what replaceProtoChunk() does, but for all statuses including FULL
            Constructor<?> completionCtor = NewChunkHolder.ChunkCompletion.class
                    .getDeclaredConstructor(ChunkAccess.class, ChunkStatus.class);
            completionCtor.setAccessible(true);

            for (ChunkStatus status : allStatuses) {
                Object completion = completionCtor.newInstance(newLevelChunk, status);
                vh.setVolatile(chunkCompletions, status.getIndex(), completion);
                if (status == ChunkStatus.FULL) break;
            }

            Object fullCompletion = completionCtor.newInstance(newLevelChunk, ChunkStatus.FULL);
            Field lastChunkCompletionField = NewChunkHolder.class.getDeclaredField("lastChunkCompletion");
            lastChunkCompletionField.setAccessible(true);
            lastChunkCompletionField.set(newChunkHolder, fullCompletion);

            Heightmap.primeHeightmaps(newLevelChunk, EnumSet.allOf(Heightmap.Types.class));

            newLevelChunk.registerAllBlockEntitiesAfterLevelLoad();
            newLevelChunk.registerTickContainerInLevel(serverLevel);

            initializeEntityChunk(newChunkHolder);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | NoSuchFieldException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private static ChunkEntitySlices initializeEntityChunk(NewChunkHolder holder) {
        try {
            Field entityChunkField = NewChunkHolder.class.getDeclaredField("entityChunk");
            entityChunkField.setAccessible(true);

            ChunkEntitySlices slices = new ChunkEntitySlices(
                    holder.world, holder.chunkX, holder.chunkZ, holder.getChunkStatus(),
                    holder.holderData, WorldUtil.getMinSection(holder.world), WorldUtil.getMaxSection(holder.world)
            );
            slices.setTransient(false);

            entityChunkField.set(holder, slices);

            holder.world.moonrise$getEntityLookup().entitySectionLoad(holder.chunkX, holder.chunkZ, slices);

            return slices;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void lightChunk(ServerLevel level, LevelChunk chunk) {
        ThreadedLevelLightEngine threadedEngine = (ThreadedLevelLightEngine) level.getLightEngine();
        StarLightInterface starlight = threadedEngine.starlight$getLightEngine();
        starlight.lightChunk(chunk, StarLightEngine.getEmptySectionsForChunk(chunk));
    }

    public static void addBlockEntity(PolarChunk.BlockEntity polarBlockEntity, ChunkAccess chunk) {
        int posIndex = polarBlockEntity.index();
        CompoundTag nbt = polarBlockEntity.data();

        int x = CoordConversion.chunkBlockIndexGetX(posIndex);
        int y = CoordConversion.chunkBlockIndexGetY(posIndex);
        int z = CoordConversion.chunkBlockIndexGetZ(posIndex);

        BlockState blockState = chunk.getBlockState(x, y, z);
        BlockPos blockPos = new BlockPos(chunk.getPos().x() * 16 + x, y, chunk.getPos().z() * 16 + z);

        if (!(blockState.getBlock() instanceof EntityBlock entityBlock)) {
//            PolarPaper.logger().warning("Block " + blockState + " does not have a block entity");
//            throw new IllegalArgumentException("Block " + blockState + " does not have a block entity");
            return;
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(blockPos, blockState);
        if (blockEntity == null) {
//            PolarPaper.logger().warning("Block " + blockState + " returned null block entity");
//            throw new IllegalArgumentException("Block " + blockState + " returned null block entity");
            return;
        }

        var registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();

        // Load NBT data into the block entity
        blockEntity.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, nbt)
        );

        if (chunk instanceof LevelChunk levelChunk) {
            blockEntity.setLevel(levelChunk.getLevel());
            levelChunk.addAndRegisterBlockEntity(blockEntity);
        } else {
            chunk.blockEntities.put(blockPos, blockEntity);
        }

    }

    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }



}
