package live.minehub.polarpaper;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Optional;
import java.util.logging.Logger;

public class PolarListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(PolarListener.class.getName());

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        PolarWorld polarWorld = PolarWorld.fromWorld(event.getWorld());
        if (polarWorld == null) return;

        PolarChunk chunk = polarWorld.chunkAt(event.getChunk().getX(), event.getChunk().getZ());
        if (chunk == null) return;

        ChunkGenerator generator = event.getWorld().getGenerator();
        if (!(generator instanceof PolarGenerator polarGenerator)) return;
        PolarWorldAccess worldAccess = polarGenerator.getWorldAccess();
        if (chunk.userData().length > 0) {
            worldAccess.populateChunkData(event.getChunk(), chunk.userData());
        }

        if (chunk.entities() != null) {
            for (PolarChunk.Entity polarEntity : chunk.entities()) {
                Entity entity;
                try {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(polarEntity.bytes());
                    DataInputStream dataInput = new DataInputStream(inputStream);
                    CompoundTag compound = NbtIo.read(dataInput, NbtAccounter.unlimitedHeap());
                    Optional<Integer> dataVersion = compound.getInt("DataVersion");
                    compound = PlatformHooks.get().convertNBT(References.ENTITY, MinecraftServer.getServer().fixerUpper, compound, dataVersion.get(), Bukkit.getUnsafe().getDataVersion());

                    Optional<net.minecraft.world.entity.Entity> entityOptional = EntityType.create(compound, ((CraftWorld) event.getWorld()).getHandle(), EntitySpawnReason.LOAD);
                    if (entityOptional.isEmpty()) {
                        LOGGER.warning("Failed to deserialize entity");
                        continue;
                    }

                    entity = entityOptional.get().getBukkitEntity();
                } catch (Exception e) {
                    LOGGER.warning("Failed to deserialize entity");
                    LOGGER.warning(e.toString());
                    continue;
                }
                entity.spawnAt(new Location(event.getWorld(), polarEntity.x() + chunk.x() * 16, polarEntity.y(), polarEntity.z() + chunk.z() * 16, polarEntity.yaw(), polarEntity.pitch()));
            }
        }
    }

}
