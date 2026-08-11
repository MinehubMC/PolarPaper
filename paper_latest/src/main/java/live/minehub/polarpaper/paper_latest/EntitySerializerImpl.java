package live.minehub.polarpaper.paper_latest;

import com.mojang.logging.LogUtils;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.util.FoliaUtil;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class EntitySerializerImpl implements EntitySerializer {

    @Override
    public net.minecraft.world.entity.Entity compoundToEntity(World world, CompoundTag compound) {
        ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "deserialiseEntity", LogUtils.getLogger());
        ValueInput tagValueInput = TagValueInput.create(problemReporter, ((CraftWorld) world).getHandle().registryAccess(), compound);

        return net.minecraft.world.entity.EntityType
                .create(tagValueInput, ((CraftWorld) world).getHandle(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false))
                .orElse(null);
    }

    @Override
    public CompletableFuture<byte @Nullable []> entityToBytes(Entity entity, Plugin plugin) {
        CompletableFuture<byte @Nullable []> byteArrayFuture = new CompletableFuture<>();
        FoliaUtil.scheduleOnEntityIfFolia(plugin, entity, () -> {
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

                FoliaUtil.scheduleOnEntityIfFolia(plugin, entity, () -> {
                    try {
                        boolean successful2 = ((CraftEntity) entity).getHandle().saveAsPassenger(tagValueOutput, true, false, false);
                        successfulFuture.complete(successful2);
                    } catch (Exception e2) {
                        LOGGER.error("Failed to serialize entity", e2);
                        successfulFuture.complete(false);
                    }
                }, () -> successfulFuture.complete(false));
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
        }, () -> byteArrayFuture.complete(null));

        return byteArrayFuture;
    }
}
