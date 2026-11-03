package live.minehub.polarpaper.util;

import live.minehub.polarpaper.core.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.util.FoliaUtil;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import live.minehub.polarpaper.core.world.PolarEntity;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.persistence.DirtyCraftPersistentDataContainer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Provides features not available in the standard polar format.
 * Currently entities and the chunk's persistent data.
 */
public class EntitiesWorldAccess implements PolarWorldAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntitiesWorldAccess.class);

    // Current version of the features chunk data
    private static final byte CURRENT_FEATURES_VERSION = 2;
    private static final byte ENTITIES_VERSION = 1;
    private static final byte PERSISTENT_DATA_CONTAINER_VERSION = 2;

    private final Plugin plugin;
    private final EntitySerializer entitySerializer;
    public EntitiesWorldAccess(Plugin plugin, EntitySerializer entitySerializer) {
       this.plugin = plugin;
       this.entitySerializer = entitySerializer;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public void loadChunkData(World world, ChunkAccess chunk, final byte @Nullable [] userData) {
        if (userData == null || userData.length == 0) return;

        ServerLevel level = ((CraftWorld) world).getHandle();

        MemorySegment segment = MemorySegment.ofArray(userData);
        MemorySegmentReader reader = new MemorySegmentReader(segment);

        byte version = reader.readByte();

        List<PolarEntity> entities = EntityUtil.getEntities(reader);
        List<Map.Entry<PolarEntity, net.minecraft.world.entity.Entity>> loadedEntities = new ArrayList<>();
        Set<UUID> passengers = new HashSet<>();

        for (PolarEntity polarEntity : entities) {
            net.minecraft.world.entity.Entity entity = polarEntity.toNMSEntity(entitySerializer, world, polarEntity.getLocation(world, chunk.locX, chunk.locZ));
            if (entity == null) continue;

            loadedEntities.add(Map.entry(polarEntity, entity));
            for (net.minecraft.world.entity.Entity passenger : entity.getIndirectPassengers()) {
                passengers.add(passenger.getUUID());
            }
        }

        List<net.minecraft.world.entity.Entity> successEntities = new ArrayList<>();
        for (Map.Entry<PolarEntity, net.minecraft.world.entity.Entity> entry : loadedEntities) {
            PolarEntity polarEntity = entry.getKey();
            net.minecraft.world.entity.Entity entity = entry.getValue();

            // Older versions also stored passengers on their own, they are already loaded riding their vehicle
            if (passengers.contains(entity.getUUID())) continue;

            CraftEntity bukkitEntity = entity.getBukkitEntity();
            Location spawnLocation = bukkitEntity.getLocation();

            PolarEntitySpawnEvent event = new PolarEntitySpawnEvent(polarEntity, bukkitEntity, spawnLocation, false);
            event.callEvent();
            if (!event.isCancelled()) {
                successEntities.add(entity);
            }
        }

        level.moonrise$getEntityLookup().addEntityChunkEntities(successEntities, chunk.getPos());

        if (version >= PERSISTENT_DATA_CONTAINER_VERSION) {
            PersistentDataContainer persistentDataContainer = chunk.persistentDataContainer;
            try {
                byte[] bytes = reader.readByteArray();
                persistentDataContainer.readFromBytes(bytes);
            } catch (IOException e) {
                LOGGER.error("Failed to deserialize persistent data container", e);
            }
        }
    }

    @Override
    public void saveChunkData(ChunkAccess chunk,
                              Map<BlockPos, BlockEntity> blockEntities,
                              Entity[] entities, MemorySegmentWriter writer) {
        List<CompletableFuture<@Nullable SavedEntity>> entityFutures = new ArrayList<>();

        for (@NotNull Entity entity : entities) {
            if (entity.getType() == EntityType.PLAYER) continue;
            if (!entity.isPersistent()) continue;
            CompletableFuture<@Nullable SavedEntity> future = new CompletableFuture<>();
            FoliaUtil.scheduleOnEntityIfFolia(plugin, entity, () -> {
                boolean vehicle = !entity.getPassengers().isEmpty();
                PolarEntity polarEntity = EntityUtil.entityToPolarEntity(entity, plugin, entitySerializer);
                if (polarEntity == null) {
                    future.complete(null);
                    return;
                }
                // The passengers in the record are the ones actually saved with the vehicle
                Set<UUID> passengers = vehicle ? readPassengerUUIDs(polarEntity.bytes()) : Set.of();
                future.complete(new SavedEntity(entity.getUniqueId(), polarEntity, passengers));
            }, () -> future.complete(null));
            entityFutures.add(future);
        }

        List<SavedEntity> savedEntities = new ArrayList<>();
        Set<UUID> savedPassengers = new HashSet<>();
        for (CompletableFuture<@Nullable SavedEntity> entityFuture : entityFutures) {
            SavedEntity savedEntity = entityFuture.join();
            if (savedEntity == null) continue;
            savedEntities.add(savedEntity);
            savedPassengers.addAll(savedEntity.passengers());
        }

        List<PolarEntity> polarEntities = new ArrayList<>();
        for (SavedEntity savedEntity : savedEntities) {
            // Already saved inside its vehicle's record
            if (savedPassengers.contains(savedEntity.uuid())) continue;
            polarEntities.add(savedEntity.polarEntity());
        }

        writer.writeByte(CURRENT_FEATURES_VERSION);
        EntityUtil.writeEntities(polarEntities, writer);

        DirtyCraftPersistentDataContainer persistentDataContainer = chunk.persistentDataContainer;
        try {
            byte[] bytes = persistentDataContainer.serializeToBytes();
            writer.writeByteArray(bytes);
        } catch (IOException e) {
            LOGGER.error("Failed to serialize persistent data container", e);
            writer.writeByteArray(new byte[0]);
        }
    }

    private record SavedEntity(UUID uuid, PolarEntity polarEntity, Set<UUID> passengers) {
    }

    /**
     * Returns the UUIDs of all passengers stored in the Passengers list of a serialized entity, recursively
     */
    private static Set<UUID> readPassengerUUIDs(byte[] bytes) {
        Set<UUID> uuids = new HashSet<>();
        try {
            CompoundTag compound = NbtIo.read(new MemorySegmentReader(MemorySegment.ofArray(bytes)), NbtAccounter.uncompressedQuota());
            addPassengerUUIDs(compound, uuids);
        } catch (IOException e) {
            LOGGER.error("Failed to read passengers of saved entity", e);
        }
        return uuids;
    }

    private static void addPassengerUUIDs(CompoundTag compound, Set<UUID> uuids) {
        for (Tag tag : compound.getListOrEmpty("Passengers")) {
            if (!(tag instanceof CompoundTag passenger)) continue;
            passenger.getIntArray("UUID").filter(ints -> ints.length == 4).ifPresent(ints -> uuids.add(UUIDUtil.uuidFromIntArray(ints)));
            addPassengerUUIDs(passenger, uuids);
        }
    }

}
