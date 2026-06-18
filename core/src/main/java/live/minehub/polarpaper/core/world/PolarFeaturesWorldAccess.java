package live.minehub.polarpaper.core.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import live.minehub.polarpaper.core.event.PolarEntitySpawnEvent;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.userdata.EntityUtil;
import live.minehub.polarpaper.core.util.ByteArrayUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Provides features implemented by polar paper specifically not available in the standard polar format. Currently
 * entities and the chunk persistent data container.
 */
public class PolarFeaturesWorldAccess implements PolarWorldAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarFeaturesWorldAccess.class);

    // Current version of the features chunk data
    private static final byte CURRENT_FEATURES_VERSION = 2;
    private static final byte ENTITIES_VERSION = 1;
    private static final byte PERSISTENT_DATA_CONTAINER_VERSION = 2;

    private final Plugin plugin;
    private final EntitySerializer entitySerializer;
    public PolarFeaturesWorldAccess(Plugin plugin, EntitySerializer entitySerializer) {
       this.plugin = plugin;
       this.entitySerializer = entitySerializer;
    }

    @Override
    public void loadChunkData(@NotNull World world, @NotNull ChunkAccess chunk, final byte @Nullable [] userData) {
        if (userData == null || userData.length == 0) return;

        ServerLevel level = ((CraftWorld) world).getHandle();

        final var bb = Unpooled.wrappedBuffer(userData);

        byte version = bb.readByte();

        List<PolarEntity> entities = EntityUtil.getEntities(bb);
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
                byte[] bytes = ByteArrayUtil.getByteArray(bb);
                persistentDataContainer.readFromBytes(bytes);
            } catch (IOException e) {
                LOGGER.error("Failed to deserialize persistent data container", e);
            }
        }
    }

    @Override
    public void saveChunkData(@NotNull ChunkAccess chunk,
                              @NotNull Set<Map.Entry<BlockPos, BlockEntity>> blockEntities,
                              @NotNull Entity[] entities, @NotNull ByteBuf userData) {
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

        userData.writeByte(CURRENT_FEATURES_VERSION);
        EntityUtil.writeEntities(polarEntities, userData);

        DirtyCraftPersistentDataContainer persistentDataContainer = chunk.persistentDataContainer;
        try {
            byte[] bytes = persistentDataContainer.serializeToBytes();
            ByteArrayUtil.writeByteArray(bytes, userData);
        } catch (IOException e) {
            LOGGER.error("Failed to serialize persistent data container", e);
            ByteArrayUtil.writeByteArray(new byte[0], userData);
        }
    }

}
