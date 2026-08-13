package live.minehub.polarpaper.core.userdata;

import live.minehub.polarpaper.core.util.MemorySegmentReader;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import live.minehub.polarpaper.core.world.PolarEntity;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EntityUtil {
    private EntityUtil() {

    }

    /**
     * Spawn an entity without reapplying Pos and Rot and causing issues with hanging entities
     */
    public static boolean spawnEntity(Entity entity, World world) {
        // entity.spawnAt will setPos again unnecessarily,
        // so we rewrite the function here just without setPos and setRot
        ServerLevel level = ((CraftWorld) world).getHandle();
        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandleRaw();

        nmsEntity.setLevel(level);

        boolean spawned = !nmsEntity.valid && nmsEntity.level().addFreshEntity(nmsEntity, CreatureSpawnEvent.SpawnReason.DEFAULT);
        if (spawned) {
            nmsEntity.getIndirectPassengers().forEach(e -> e.level().addFreshEntity(e, CreatureSpawnEvent.SpawnReason.DEFAULT));
        }
        return spawned;
    }

    public static List<PolarEntity> getEntities(MemorySegmentReader reader) {
        List<PolarEntity> polarEntities = new ArrayList<>();
        int entityCount = reader.readVarInt();
        for (int i = 0; i < entityCount; i++) {
            final var x = reader.readDouble();
            final var y = reader.readDouble();
            final var z = reader.readDouble();
            final var yaw = reader.readFloat();
            final var pitch = reader.readFloat();
            final var bytes = reader.readByteArray();
            polarEntities.add(new PolarEntity(x, y, z, yaw, pitch, bytes));
        }

        return polarEntities;
    }

    public static void writeEntities(List<@NotNull PolarEntity> entities, @NotNull MemorySegmentWriter data) {
        data.writeVarInt(entities.size());
        for (@NotNull PolarEntity entity : entities) {
            data.writeDouble(entity.x());
            data.writeDouble(entity.y());
            data.writeDouble(entity.z());
            data.writeFloat(entity.yaw());
            data.writeFloat(entity.pitch());
            data.writeByteArray(entity.bytes());
        }
    }

    public static CompletableFuture<@Nullable PolarEntity> entityToPolarEntity(Entity entity, Plugin plugin, EntitySerializer entitySerializer) {
        return entitySerializer.entityToBytes(entity, plugin).thenApply(entityBytes -> {
            if (entityBytes == null) return null;
            Location entityPos = entity.getLocation();

            final var x = ((entityPos.x() % 16) + 16) % 16;
            final var z = ((entityPos.z() % 16) + 16) % 16;

            return new PolarEntity(
                    x,
                    entityPos.y(),
                    z,
                    entityPos.getYaw(),
                    entityPos.getPitch(),
                    entityBytes
            );
        });
    }

}
