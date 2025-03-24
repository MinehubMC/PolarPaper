package live.minehub.polarpaper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.generator.ChunkGenerator;

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
    }

}
