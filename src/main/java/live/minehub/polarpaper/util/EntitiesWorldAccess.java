package live.minehub.polarpaper.util;

import live.minehub.polarpaper.core.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import live.minehub.polarpaper.core.world.PolarEntity;
import live.minehub.polarpaper.core.world.PolarWorldAccess;
import net.minecraft.core.BlockPos;
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
import java.util.List;
import java.util.Map;
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
    public void loadChunkData(@NotNull World world, @NotNull ChunkAccess chunk, final byte @Nullable [] userData) {
        if (userData == null || userData.length == 0) return;

        ServerLevel level = ((CraftWorld) world).getHandle();

        MemorySegment segment = MemorySegment.ofArray(userData);
        MemorySegmentReader reader = new MemorySegmentReader(segment);

        byte version = reader.readByte();

        List<PolarEntity> entities = EntityUtil.getEntities(reader);
        List<net.minecraft.world.entity.Entity> successEntities = new ArrayList<>();

        for (PolarEntity polarEntity : entities) {
            net.minecraft.world.entity.Entity entity = polarEntity.toNMSEntity(entitySerializer, world, polarEntity.getLocation(world, chunk.locX, chunk.locZ), true);
            if (entity == null) continue;

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
    public void saveChunkData(@NotNull ChunkAccess chunk,
                              @NotNull Map<BlockPos, BlockEntity> blockEntities,
                              @NotNull Entity[] entities, @NotNull MemorySegmentWriter writer) {
        List<CompletableFuture<@Nullable PolarEntity>> entityFutures = new ArrayList<>();
        List<@NotNull PolarEntity> polarEntities = new ArrayList<>();

        for (@NotNull Entity entity : entities) {
            if (entity.getType() == EntityType.PLAYER) continue;
            CompletableFuture<@Nullable PolarEntity> entityFuture = EntityUtil.entityToPolarEntity(entity, plugin, entitySerializer);
            entityFutures.add(entityFuture);
        }

        for (CompletableFuture<@Nullable PolarEntity> entityFuture : entityFutures) {
            PolarEntity polarEntity = entityFuture.join();
            if (polarEntity == null) continue;
            polarEntities.add(polarEntity);
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

}
