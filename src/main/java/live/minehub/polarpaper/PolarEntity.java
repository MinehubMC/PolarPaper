package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import com.mojang.logging.LogUtils;
import io.papermc.paper.registry.RegistryKey;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Locale;

public record PolarEntity(double x, double y, double z, float yaw, float pitch, byte[] bytes) {

    public @Nullable Entity toNMSEntity(World world, Location spawnLocation, boolean randomUUID) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        DataInputStream dataInput = new DataInputStream(inputStream);
        CompoundTag compound;
        try {
            compound = NbtIo.read(dataInput, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
//                ExceptionUtil.log(e);
            return null;
        }
        Integer dataVersion = compound.getInt("DataVersion").orElse(null);
        if (dataVersion == null) return null;
        compound = PlatformHooks.get().convertNBT(References.ENTITY, MinecraftServer.getServer().fixerUpper, compound, dataVersion, Bukkit.getUnsafe().getDataVersion());

        if (randomUUID) compound.remove("UUID"); // do not read UUID from bytes, instead use the default random uuid
        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(spawnLocation.x()));
        posTag.add(DoubleTag.valueOf(spawnLocation.y()));
        posTag.add(DoubleTag.valueOf(spawnLocation.z()));
        compound.put("Pos", posTag);
        ListTag rotationTag = new ListTag();
        rotationTag.add(FloatTag.valueOf(spawnLocation.getYaw()));
        rotationTag.add(FloatTag.valueOf(spawnLocation.getPitch()));
        compound.put("Rotation", rotationTag);

        String entityId = compound.getStringOr("id", "").replace("minecraft:", "");

        // Rotate painting
        String paintingVariant = compound.getString("variant").orElse(null);
        if (entityId.equals("painting") && paintingVariant != null) {
            int facingIndex = (int) Math.floor(spawnLocation.getYaw() / 90) % 4;
            Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
            compound.put("facing", IntTag.valueOf(facingIndex));

            Direction paintingDir = directions[facingIndex];
            Direction counterClockWise = paintingDir.getCounterClockWise();
            Vec3i unitVec3i = counterClockWise.getUnitVec3i();
            Art art = Bukkit.getUnsafe().get(RegistryKey.PAINTING_VARIANT, NamespacedKey.fromString(paintingVariant.toLowerCase(Locale.ROOT)));
            if (art != null && art.getBlockHeight() % 2 == 0) spawnLocation.subtract(0, 1, 0);
            if (art != null && art.getBlockWidth() % 2 == 0) {
                spawnLocation.subtract(unitVec3i.getX() * 0.5, 0, unitVec3i.getZ() * 0.5);
            }

            IntArrayTag blockPosTag = new IntArrayTag(new int[]{spawnLocation.blockX(), spawnLocation.blockY(), spawnLocation.blockZ()});
            compound.put("block_pos", blockPosTag);
        }

        // Rotate item frame
        // 3-south, 4-west, 2-north, 5-east, 1-top, and 0-bottom
        Integer facingValue = compound.getInt("Facing").orElse(null);
        if ((entityId.equals("item_frame") || entityId.equals("glow_item_frame")) && facingValue != null && facingValue != 1 && facingValue != 0) {
            int facingIndex = (int) Math.floor(spawnLocation.getYaw() / 90) % 4;
            Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
            int newFacingValue = directions[facingIndex].get3DDataValue();
            compound.put("Facing", IntTag.valueOf(newFacingValue));

            IntArrayTag blockPosTag = new IntArrayTag(new int[]{spawnLocation.blockX(), spawnLocation.blockY(), spawnLocation.blockZ()});
            compound.put("block_pos", blockPosTag);
        }


        ProblemReporter.ScopedCollector problemReporter = new ProblemReporter.ScopedCollector(() -> "deserialiseEntity", LogUtils.getLogger());
        ValueInput tagValueInput = TagValueInput.create(problemReporter, ((CraftWorld) world).getHandle().registryAccess(), compound);

        Entity nmsEntity = EntityType
                .create(tagValueInput, ((CraftWorld) world).getHandle(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false))
                .orElse(null);
        if (nmsEntity == null) return null;

        return nmsEntity;
    }

    public Location getLocation(Chunk chunk) {
        return getLocation(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public Location getLocation(World world, int chunkX, int chunkZ) {
        // fix for previous version and also sanity check :)
        double realX = x;
        double realZ = z;
        if (x < 0) realX += 16;
        if (z < 0) realZ += 16;

        return new Location(world, realX + chunkX * 16, y, realZ + chunkZ * 16, yaw, pitch);
    }


}