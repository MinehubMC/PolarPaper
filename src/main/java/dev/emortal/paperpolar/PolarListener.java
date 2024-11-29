package dev.emortal.paperpolar;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

import java.util.logging.Logger;

public class PolarListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(PolarListener.class.getName());

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        PolarWorld polarWorld = PolarWorld.fromWorld(event.getWorld());
        if (polarWorld == null) return;

        PolarChunk chunk = polarWorld.chunkAt(event.getChunk().getX(), event.getChunk().getZ());
        if (chunk == null) return;

        for (PolarChunk.Entity polarEntity : chunk.entities()) {
            Entity entity;
            try {
                entity = Bukkit.getUnsafe().deserializeEntity(polarEntity.bytes(), event.getWorld());
            } catch (Exception e) {
                LOGGER.warning("Failed to deserialize entity");
                LOGGER.warning(e.toString());
                continue;
            }
            entity.spawnAt(new Location(event.getWorld(), polarEntity.x() + chunk.x() * 16, polarEntity.y(), polarEntity.z() + chunk.z() * 16, polarEntity.yaw(), polarEntity.pitch()));
        }
    }

}
