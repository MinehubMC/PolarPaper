package live.minehub.polarpaper;

import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.generator.PolarGenerator;
import live.minehub.polarpaper.generator.PolarStreamingGenerator;
import live.minehub.polarpaper.source.PolarSource;
import live.minehub.polarpaper.util.CoordConversion;
import live.minehub.polarpaper.util.PolarConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
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
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static live.minehub.polarpaper.util.ByteArrayUtil.getVarInt;

public class PolarStreamLoader {

    public static CompletableFuture<Void> stream(PolarSource source, World world, @NotNull PolarWorldAccess worldAccess) {
        return stream(source.readBytes(), world, worldAccess);
    }

    public static CompletableFuture<Void> stream(PolarSource source, World world, @NotNull PolarDataConverter dataConverter, @NotNull PolarWorldAccess worldAccess) {
        return stream(source.readBytes(), world, dataConverter, worldAccess);
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
            futures[i] = readChunk(world, dataConverter, worldAccess, version, dataVersion, uncompressed, maxSection - minSection + 1);
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
        boolean[] skyEmptiness = new boolean[sectionCount];
        boolean[] blockEmptiness = new boolean[sectionCount];
        boolean anyPresent = false;
        LevelChunkSection[] levelChunkSections = new LevelChunkSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            PolarSection polarSection = PolarReader.readSection(dataConverter, version, dataVersion, bb);
            if (!anyPresent && (polarSection.skyLightContent() != PolarSection.LightContent.MISSING || polarSection.blockLightContent() != PolarSection.LightContent.MISSING)) anyPresent = true;
            LevelChunkSection section = polarSection.createLevelChunkSection(serverLevel.registryAccess());
            levelChunkSections[i] = section;
            boolean airSection = section.hasOnlyAir();
            skyEmptiness[i] = airSection;
            blockEmptiness[i] = airSection;
            skyNibbles[i + 1] = new SWMRNibbleArray(polarSection.skyLight());
            blockNibbles[i + 1] = new SWMRNibbleArray(polarSection.blockLight());

        }

        NoUnloadLevelChunk newLevelChunk = new NoUnloadLevelChunk(serverLevel, new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, levelChunkSections, null, null);

        if (anyPresent) {
            newLevelChunk.starlight$setBlockEmptinessMap(blockEmptiness);
            newLevelChunk.starlight$setSkyEmptinessMap(skyEmptiness);
            newLevelChunk.starlight$setSkyNibbles(skyNibbles);
            newLevelChunk.starlight$setBlockNibbles(blockNibbles);
        } else {
            lightChunk(serverLevel, newLevelChunk);
        }
        newLevelChunk.setLightCorrect(true);
        newLevelChunk.tryMarkSaved();
//        newLevelChunk.setLogUnsaved(true);

        int blockEntityCount = getVarInt(bb);
        for (int i = 0; i < blockEntityCount; i++) {
            readBlockEntity(dataConverter, newLevelChunk, dataVersion, bb);
        }

        var heightmaps = PolarReader.readHeightmaps(bb);

        // Objects
        int userDataLength = getVarInt(bb);
        byte[] userData = new byte[userDataLength];
        bb.readBytes(userData);

        return insertChunk(serverLevel, newLevelChunk).thenRun(() -> {
            worldAccess.loadChunkData(world, newLevelChunk, userData);
        });
    }

    protected static CompletableFuture<Void> insertChunk(ServerLevel serverLevel, NoUnloadLevelChunk newLevelChunk) {
        CompletableFuture<Void> future = new CompletableFuture<>();

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

            Bukkit.getGlobalRegionScheduler().run(PolarPaper.getPlugin(), t -> {
                // Cannot sync load entity data off-main
                ChunkEntitySlices slices = initializeEntityChunk(newChunkHolder, chunkX, chunkZ, chunkTaskScheduler);
                slices.updateStatus(FullChunkStatus.ENTITY_TICKING, serverLevel.moonrise$getEntityLookup());

                chunkHolderManager.ticketLockArea.unlock(lock);
                chunkTaskScheduler.schedulingLockArea.unlock(lock1);

                future.complete(null);
            });

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

            newLevelChunk.registerAllBlockEntitiesAfterLevelLoad();
            newLevelChunk.registerTickContainerInLevel(serverLevel);

            return future;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException | NoSuchFieldException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private static ChunkEntitySlices initializeEntityChunk(NewChunkHolder holder, int chunkX, int chunkZ, ChunkTaskScheduler scheduler) {
        try {
            Field pendingEntityChunkField = NewChunkHolder.class.getDeclaredField("pendingEntityChunk");
            pendingEntityChunkField.setAccessible(true);

            Method loadInEntityChunkMethod = NewChunkHolder.class.getDeclaredMethod("loadInEntityChunk", boolean.class);
            loadInEntityChunkMethod.setAccessible(true);

            ReentrantAreaLock.Node lock = scheduler.schedulingLockArea.lock(chunkX, chunkZ);
            try {
                pendingEntityChunkField.set(holder, new CompoundTag());
            } finally {
                scheduler.schedulingLockArea.unlock(lock);
            }

            return (ChunkEntitySlices) loadInEntityChunkMethod.invoke(holder, false);
        } catch (NoSuchFieldException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void lightChunk(ServerLevel level, LevelChunk chunk) {
        ThreadedLevelLightEngine threadedEngine = (ThreadedLevelLightEngine) level.getLightEngine();
        StarLightInterface starlight = threadedEngine.starlight$getLightEngine();

        LevelChunkSection[] sections = chunk.getSections();
        Boolean[] emptySections = new Boolean[sections.length];
        for (int i = 0; i < sections.length; i++) {
            emptySections[i] = sections[i].hasOnlyAir();
        }

        starlight.lightChunk(chunk, emptySections);
    }

    private static void readBlockEntity(@NotNull PolarDataConverter dataConverter, LevelChunk chunk, int dataVersion, @NotNull ByteBuf bb) {
        PolarChunk.BlockEntity polarBlockEntity = PolarReader.readBlockEntity(dataConverter, dataVersion, bb);

        int posIndex = polarBlockEntity.index();
        CompoundTag nbt = polarBlockEntity.data();

        int x = CoordConversion.chunkBlockIndexGetX(posIndex);
        int y = CoordConversion.chunkBlockIndexGetY(posIndex);
        int z = CoordConversion.chunkBlockIndexGetZ(posIndex);

        BlockState blockState = chunk.getBlockState(x, y, z);
        BlockPos blockPos = new BlockPos(chunk.getPos().x * 16 + x, y, chunk.getPos().z * 16 + z);

        if (!(blockState.getBlock() instanceof EntityBlock entityBlock)) {
            throw new IllegalArgumentException("Block " + blockState + " does not have a block entity");
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(blockPos, blockState);
        if (blockEntity == null) {
            throw new IllegalArgumentException("Block " + blockState + " returned null block entity");
        }

        // Load NBT data into the block entity
        blockEntity.loadWithComponents(
                TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        chunk.getLevel().registryAccess(),
                        nbt
                )
        );

        blockEntity.setLevel(chunk.getLevel());
        chunk.addAndRegisterBlockEntity(blockEntity);
    }

    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }



}
