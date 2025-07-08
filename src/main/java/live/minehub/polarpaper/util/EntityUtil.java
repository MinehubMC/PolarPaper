package live.minehub.polarpaper.util;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import com.mojang.logging.LogUtils;
import live.minehub.polarpaper.PolarChunk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static live.minehub.polarpaper.util.ByteArrayUtil.getByteArray;
import static live.minehub.polarpaper.util.ByteArrayUtil.getVarInt;

public class EntityUtil {

    private EntityUtil() {

    }

    public static List<PolarChunk.Entity> getEntities(byte @Nullable [] userData) {
        if (userData == null) return List.of();

        final var bb = ByteBuffer.wrap(userData);

        // Skip the version
        bb.get();

        List<PolarChunk.Entity> polarEntities = new ArrayList<>();
        int entityCount = getVarInt(bb);
        for (int i = 0; i < entityCount; i++) {
            final var x = bb.getDouble();
            final var y = bb.getDouble();
            final var z = bb.getDouble();
            final var yaw = bb.getFloat();
            final var pitch = bb.getFloat();
            final var bytes = getByteArray(bb);
            polarEntities.add(new PolarChunk.Entity(x, y, z, yaw, pitch, bytes));
        }

        return polarEntities;
    }

    public static @Nullable Entity bytesToEntity(World world, byte[] bytes) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        DataInputStream dataInput = new DataInputStream(inputStream);
        CompoundTag compound = NbtIo.read(dataInput, NbtAccounter.unlimitedHeap());
        Optional<Integer> dataVersion = compound.getInt("DataVersion");
        compound = PlatformHooks.get().convertNBT(References.ENTITY, MinecraftServer.getServer().fixerUpper, compound, dataVersion.get(), Bukkit.getUnsafe().getDataVersion());

        ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "deserialiseEntity", LogUtils.getLogger());
        ValueInput tagValueInput = TagValueInput.create(problemReporter, ((CraftWorld) world).getHandle().registryAccess(), compound);

        Optional<net.minecraft.world.entity.Entity> entityOptional = net.minecraft.world.entity.EntityType
                .create(tagValueInput, ((CraftWorld) world).getHandle(), EntitySpawnReason.LOAD);
        if (entityOptional.isEmpty()) return null;

        return entityOptional.get().getBukkitEntity();
    }

    public static byte @Nullable [] entityToBytes(Entity entity) {
        if (entity.getType() == EntityType.PLAYER) return null;

        net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandle();
        ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "serialiseEntity@" + entity.getUniqueId(), LogUtils.getLogger());
        TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter, nmsEntity.registryAccess());

        boolean successful = ((CraftEntity) entity).getHandle().saveAsPassenger(tagValueOutput, true, false, false);

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
