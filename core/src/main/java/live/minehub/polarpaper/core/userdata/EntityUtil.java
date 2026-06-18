package live.minehub.polarpaper.core.userdata;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import live.minehub.polarpaper.core.util.FoliaUtil;
import live.minehub.polarpaper.core.world.PolarEntity;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static live.minehub.polarpaper.core.util.ByteArrayUtil.*;

public class EntityUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityUtil.class);

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
            nmsEntity.getIndirectPassengers().forEach((e) -> e.level().addFreshEntity(e, CreatureSpawnEvent.SpawnReason.DEFAULT));
        }
        return spawned;
    }

    public static List<PolarEntity> getEntities(ByteBuf bb) {
        List<PolarEntity> polarEntities = new ArrayList<>();
        int entityCount = getVarInt(bb);
        for (int i = 0; i < entityCount; i++) {
            final var x = bb.readDouble();
            final var y = bb.readDouble();
            final var z = bb.readDouble();
            final var yaw = bb.readFloat();
            final var pitch = bb.readFloat();
            final var bytes = getByteArray(bb);
            polarEntities.add(new PolarEntity(x, y, z, yaw, pitch, bytes));
        }

        return polarEntities;
    }

    public static void writeEntities(List<@NotNull PolarEntity> entities, @NotNull ByteBuf data) {
        writeVarInt(entities.size(), data);
        for (@NotNull PolarEntity entity : entities) {
            data.writeDouble(entity.x());
            data.writeDouble(entity.y());
            data.writeDouble(entity.z());
            data.writeFloat(entity.yaw());
            data.writeFloat(entity.pitch());
            writeByteArray(entity.bytes(), data);
        }
    }

    public static CompletableFuture<@Nullable PolarEntity> entityToPolarEntity(Entity entity, Plugin plugin) {
        return entityToBytes(entity, plugin).thenApply(entityBytes -> {
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

    public static CompletableFuture<byte @Nullable []> entityToBytes(Entity entity, Plugin plugin) {
        CompletableFuture<byte @Nullable []> byteArrayFuture = new CompletableFuture<>();
        FoliaUtil.scheduleOnEntityIfFolia(entity, plugin, () -> {
            if (entity.getType() == EntityType.PLAYER) {
                byteArrayFuture.complete(null);
                return;
            }
            if (!entity.isPersistent()) {
                byteArrayFuture.complete(null);
                return;
            }

            net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandle();
            ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "serialiseEntity@" + entity.getUniqueId(), LogUtils.getLogger());
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter, nmsEntity.registryAccess());

            boolean successful;
            try {
                successful = ((CraftEntity) entity).getHandle().saveAsPassenger(tagValueOutput, true, false, false);
            } catch (Exception e) {
                // saveAsPassenger sometimes calls events (e.g. VillagerAcquireTradeEvent), causing errors when called async so try again synchronously
                CompletableFuture<Boolean> successfulFuture = new CompletableFuture<>();

                entity.getScheduler().run(plugin, (t) -> {
                    try {
                        boolean successful2 = ((CraftEntity) entity).getHandle().saveAsPassenger(tagValueOutput, true, false, false);
                        successfulFuture.complete(successful2);
                    } catch (Exception e2) {
                        LOGGER.error("Failed to serialize entity", e2);
                    }
                }, null);
                successful = successfulFuture.join();
            }

            CompoundTag compound = tagValueOutput.buildResult();

            Optional<String> id = compound.getString("id");
            if (id.isEmpty() || id.get().isBlank() || !successful) {
                byteArrayFuture.complete(null);
                return;
            }
            compound.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DataOutputStream dataOutput = new DataOutputStream(outputStream);
            try {
                NbtIo.write(
                        compound,
                        dataOutput
                );
                outputStream.flush();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            byteArrayFuture.complete(outputStream.toByteArray());
        });

        return byteArrayFuture;
    }

}
