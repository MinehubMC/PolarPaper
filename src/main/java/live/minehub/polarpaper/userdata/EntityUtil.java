package live.minehub.polarpaper.userdata;

import com.google.common.io.ByteArrayDataOutput;
import com.mojang.logging.LogUtils;
import live.minehub.polarpaper.PolarEntity;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static live.minehub.polarpaper.util.ByteArrayUtil.*;

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
            nmsEntity.getIndirectPassengers().forEach((e) -> e.level().addFreshEntity(e, CreatureSpawnEvent.SpawnReason.DEFAULT));
        }
        return spawned;
    }

    public static List<PolarEntity> getEntities(ByteBuffer bb) {
        List<PolarEntity> polarEntities = new ArrayList<>();
        int entityCount = getVarInt(bb);
        for (int i = 0; i < entityCount; i++) {
            final var x = bb.getDouble();
            final var y = bb.getDouble();
            final var z = bb.getDouble();
            final var yaw = bb.getFloat();
            final var pitch = bb.getFloat();
            final var bytes = getByteArray(bb);
            polarEntities.add(new PolarEntity(x, y, z, yaw, pitch, bytes));
        }

        return polarEntities;
    }

    public static void writeEntities(List<PolarEntity> entities, @NotNull ByteArrayDataOutput data) {
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

    public static byte @Nullable [] entityToBytes(Entity entity) {
        if (entity.getType() == EntityType.PLAYER) return null;

        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandle();
        ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "serialiseEntity@" + entity.getUniqueId(), LogUtils.getLogger());
        TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter, nmsEntity.registryAccess());

        boolean successful;
        try {
            successful = ((CraftEntity) entity).getHandle().saveAsPassenger(tagValueOutput, true, false, false);
        } catch (Exception e) {
            // saveAsPassenger sometimes calls events (e.g. VillagerAcquireTradeEvent), causing errors when called async so try again synchronously
            CompletableFuture<Boolean> successfulFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(PolarPaper.getPlugin(), () -> {
                try {
                    boolean successful2 = ((CraftEntity) entity).getHandle().saveAsPassenger(tagValueOutput, true, false, false);
                    successfulFuture.complete(successful2);
                } catch (Exception e2) {
                    PolarPaper.logger().warning("Failed to serialize entity");
                    ExceptionUtil.log(e2);
                }
            });
            successful = successfulFuture.join();
        }

        CompoundTag compound = tagValueOutput.buildResult();

        Optional<String> id = compound.getString("id");
        if (id.isEmpty() || id.get().isBlank() || !successful) return null;
        compound.putInt("DataVersion", Bukkit.getUnsafe().getDataVersion());
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

        return outputStream.toByteArray();
    }

}
